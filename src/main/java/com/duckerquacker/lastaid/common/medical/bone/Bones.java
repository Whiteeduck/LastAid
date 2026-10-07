package com.duckerquacker.lastaid.common.medical.bone;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.DamageContext;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalProcedures;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Fractures and dislocations: shared logic of {@link FractureCondition} and {@link DislocationCondition}
 * (healing, effects on the player, triggers, splint / reduction, medical kit, pain, client bits).
 */
public final class Bones {
   public static final ResourceLocation BONE_ICON = ResourceLocation.fromNamespaceAndPath("lastaid", "textures/gui/conditions/bone.png");
   public static final ResourceLocation JOINT_ICON = ResourceLocation.fromNamespaceAndPath("lastaid", "textures/gui/conditions/joint.png");
   /** ConditionSnapshot displayFlags bit: the icon on the body figure blinks (fracture without a splint). */
   public static final int FLAG_BLINK = 16;
   // client bits (LimbVisualState, shifted by 3): fracture type 0-3, splinted, dislocated
   public static final int BIT_SPLINT = 4;
   public static final int BIT_DISLOCATED = 8;
   public static final int BIT_BLEEDING = 16;
   private static final ResourceLocation SPEED_ID = EffectUtil.id("bone_speed");
   private static final ResourceLocation JUMP_ID = EffectUtil.id("bone_jump");
   private static final ResourceLocation ATTACK_ID = EffectUtil.id("bone_attack");
   private static final ResourceLocation MINING_ID = EffectUtil.id("bone_mining");
   private static final double DAY = 24000.0;

   private Bones() {
   }

   static MedicalConfig.Fracture cfg() {
      return MedicalConfig.SERVER.fracture;
   }

   static MedicalConfig.Splint splintCfg() {
      return MedicalConfig.SERVER.splint;
   }

   public static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && MedicalConfig.SERVER.enabled.get() && cfg().enabled.get();
   }

   static FractureCondition.Bone bone(Player player, Frostbite.Limb limb) {
      return FractureCondition.INSTANCE.state(player).get(limb);
   }

   static DislocationCondition.Joint joint(Player player, Frostbite.Limb limb) {
      return DislocationCondition.INSTANCE.state(player).get(limb);
   }

   static double healTicks(int type) {
      return DAY * switch (type) {
         case FractureCondition.HAIRLINE -> cfg().hairlineHealDays.get();
         case FractureCondition.CLOSED -> cfg().closedHealDays.get();
         default -> cfg().openHealDays.get();
      };
   }

   static double dislocationHealTicks() {
      return DAY * cfg().dislocationHealDays.get();
   }

   private static boolean dead(Player player, Frostbite.Limb limb) {
      return MedicalHooks.limbStatus(player, limb) >= 4;
   }

   // ---------------------------------------------------------------- state changes

   /** Breaks a bone (only ever makes it worse). Returns true if something changed. */
   public static boolean breakBone(ServerPlayer player, Frostbite.Limb limb, int type, boolean announce) {
      if (type <= 0 || MedicalHooks.limbStatus(player, limb) == LimbVisualState.AMPUTATED) {
         return false;
      }
      FractureCondition.State state = FractureCondition.INSTANCE.state(player);
      FractureCondition.Bone bone = state.get(limb);
      if (bone.type >= type) {
         return false;
      }
      bone.reset();
      bone.type = Math.min(3, type);
      state.markDirty();
      if (bone.type == FractureCondition.OPEN) {
         // the bone tore the skin: a bleeding wound in the bleeding system
         com.duckerquacker.lastaid.common.medical.wounds.Zone zone = com.duckerquacker.lastaid.common.medical.wounds.Zone.of(limb);
         if (zone != null) {
            com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.addWound(player, zone, com.duckerquacker.lastaid.common.medical.wounds.Wounds.MEDIUM, false);
         }
      }
      if (announce) {
         player.level().playSound(null, player.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.55F);
         player.displayClientMessage(
            Component.translatable("lastaid.fracture.broke", Frostbite.limbName(limb), Component.translatable("lastaid.fracture.type." + bone.type))
               .withStyle(ChatFormatting.RED),
            true
         );
      }
      applyEffects(player);
      return true;
   }

   public static boolean dislocate(ServerPlayer player, Frostbite.Limb limb, boolean announce) {
      if (MedicalHooks.limbStatus(player, limb) == LimbVisualState.AMPUTATED) {
         return false;
      }
      DislocationCondition.State state = DislocationCondition.INSTANCE.state(player);
      DislocationCondition.Joint joint = state.get(limb);
      if (joint.dislocated) {
         return false;
      }
      joint.dislocated = true;
      joint.heal = 0.0;
      state.markDirty();
      if (announce) {
         player.level().playSound(null, player.blockPosition(), SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.7F);
         player.displayClientMessage(Component.translatable("lastaid.dislocation.happened", Frostbite.limbName(limb)).withStyle(ChatFormatting.RED), true);
      }
      applyEffects(player);
      return true;
   }

   /** Admin command: set the exact fracture type (0 heals it, also clears "healed wrong" when 0). */
   public static void setFracture(ServerPlayer player, Frostbite.Limb limb, int type) {
      FractureCondition.State state = FractureCondition.INSTANCE.state(player);
      FractureCondition.Bone bone = state.get(limb);
      bone.reset();
      bone.type = Math.max(0, Math.min(3, type));
      if (type == 0) {
         bone.healedWrong = false;
      }
      state.markDirty();
      applyEffects(player);
   }

   public static void setDislocated(ServerPlayer player, Frostbite.Limb limb, boolean dislocated) {
      DislocationCondition.State state = DislocationCondition.INSTANCE.state(player);
      DislocationCondition.Joint joint = state.get(limb);
      joint.dislocated = dislocated;
      joint.heal = 0.0;
      state.markDirty();
      applyEffects(player);
   }

   /** Admin command: heal progress of all bones and joints jumps ahead. */
   public static void advance(ServerPlayer player, double days) {
      addHealing(player, days * DAY);
   }

   // ---------------------------------------------------------------- ticking

   private static double healRate(ServerPlayer player) {
      double rate = 20.0;
      if (player.getFoodData().getFoodLevel() < 6) {
         rate *= cfg().hungryHealMultiplier.get();
      }
      if (player.isSleeping()) {
         rate *= cfg().sleepHealMultiplier.get();
      }
      return rate;
   }

   static void tickFractures(ServerPlayer player, FractureCondition.State state) {
      double rate = healRate(player);
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         FractureCondition.Bone bone = state.get(limb);
         if (bone.type <= 0) {
            continue;
         }
         if (MedicalHooks.limbStatus(player, limb) == LimbVisualState.AMPUTATED) {
            bone.reset();
            state.markDirty();
            continue;
         }
         bone.age += 20L;
         if (bone.type == FractureCondition.OPEN) {
            com.duckerquacker.lastaid.common.medical.wounds.Zone zone = com.duckerquacker.lastaid.common.medical.wounds.Zone.of(limb);
            if (!bone.bleedingStopped) {
               if (zone != null && com.duckerquacker.lastaid.common.medical.wounds.Wounds.isBleeding(player, zone.part)) {
                  continue; // an open fracture only starts to heal once the bleeding is stopped
               }
               bone.bleedingStopped = true;
               state.markDirty();
            }
            if (!bone.treated && !bone.infectionRolled
               && bone.age >= MedicalConfig.SERVER.infection.openFractureInfectionHours.get() * 1000.0) {
               bone.infectionRolled = true;
               state.markDirty();
               if (player.getRandom().nextDouble() < MedicalConfig.SERVER.infection.openFractureInfectionChance.get()) {
                  com.duckerquacker.lastaid.common.medical.infection.Infection.INSTANCE.start(player, zone, "lastaid.infection.source.open_fracture");
               }
            }
         }
         bone.heal += rate;
         if (bone.heal >= healTicks(bone.type)) {
            boolean wrong = bone.type >= FractureCondition.CLOSED && !bone.splinted && splintCfg().healWithoutSplintPenalty.get();
            bone.reset();
            bone.healedWrong |= wrong;
            player.displayClientMessage(
               Component.translatable(wrong ? "lastaid.fracture.healed_wrong.msg" : "lastaid.fracture.healed", Frostbite.limbName(limb))
                  .withStyle(wrong ? ChatFormatting.GOLD : ChatFormatting.GREEN),
               false
            );
            state.markDirty();
         } else if (player.tickCount % 200 < 20) {
            state.markDirty();
         }
      }
      tickSplintWalk(player);
   }

   // ---------------------------------------------------------------- walking on splinted broken legs

   private static final class Walk {
      boolean active;
      int walkSeconds;
      int strainTimer;
      double x;
      double z;
   }

   private static final java.util.Map<java.util.UUID, Walk> WALK = new java.util.HashMap<>();

   /** Strong pain relief: an active morphine effect or enough morphine in the blood. */
   public static boolean strongPainRelief(Player player) {
      if (player.hasEffect(com.duckerquacker.lastaid.common.RegistryObjects.MORPHINE_EFFECT)) {
         return true;
      }
      return com.duckerquacker.lastaid.common.medical.overdose.Overdose.INSTANCE.state(player).morphine >= cfg().strongPainkillerMorphineDoses.get();
   }

   /** Both legs have a closed / open fracture, both splinted, no other reason for the legs to fail, strong pain relief. */
   public static boolean canSplintWalk(ServerPlayer player) {
      if (!enabled() || !cfg().allowWalkOnSplintedBrokenLegs.get()) {
         return false;
      }
      for (Frostbite.Limb leg : new Frostbite.Limb[]{Frostbite.Limb.LEFT_LEG, Frostbite.Limb.RIGHT_LEG}) {
         FractureCondition.Bone bone = bone(player, leg);
         if (bone.type < FractureCondition.CLOSED || !bone.splinted || joint(player, leg).dislocated || dead(player, leg)) {
            return false;
         }
      }
      return strongPainRelief(player);
   }

   /** Walking on splinted broken legs right now (server). */
   public static boolean splintWalking(Player player) {
      Walk walk = WALK.get(player.getUUID());
      return walk != null && walk.active;
   }

   private static void tickSplintWalk(ServerPlayer player) {
      boolean can = canSplintWalk(player);
      Walk walk = WALK.get(player.getUUID());
      if (!can) {
         if (walk != null) {
            WALK.remove(player.getUUID());
            if (walk.active) {
               applyEffects(player);
               player.displayClientMessage(Component.translatable("lastaid.splint_walk.stop").withStyle(ChatFormatting.RED), true);
            }
         }
         return;
      }
      if (walk == null) {
         walk = new Walk();
         walk.x = player.getX();
         walk.z = player.getZ();
         WALK.put(player.getUUID(), walk);
      }
      if (!walk.active) {
         walk.active = true;
         applyEffects(player);
         player.displayClientMessage(Component.translatable("lastaid.splint_walk.start").withStyle(ChatFormatting.YELLOW), true);
      }
      double moved = Math.hypot(player.getX() - walk.x, player.getZ() - walk.z);
      walk.x = player.getX();
      walk.z = player.getZ();
      if (moved > 0.1 && !player.isPassenger()) {
         walk.walkSeconds++;
         if (++walk.strainTimer >= cfg().strainIntervalSeconds.get()) {
            walk.strainTimer = 0;
            if (player.getRandom().nextDouble() < cfg().strainDamageChance.get() && !player.isCreative() && !player.isSpectator()) {
               Frostbite.Limb leg = player.getRandom().nextBoolean() ? Frostbite.Limb.LEFT_LEG : Frostbite.Limb.RIGHT_LEG;
               hurtPart(player, leg, cfg().strainDamage.get().floatValue());
               player.displayClientMessage(Component.translatable("lastaid.splint_walk.strain", Frostbite.limbName(leg)).withStyle(ChatFormatting.RED), true);
            }
         }
      } else if (walk.walkSeconds > 0 && player.tickCount % 40 < 20) {
         walk.walkSeconds--; // resting slowly takes the strain pain back
      }
   }

   /** Extra pain from walking on broken legs: +1 level per minute of walking. */
   private static int splintWalkPain(ServerPlayer player) {
      Walk walk = WALK.get(player.getUUID());
      if (walk == null || !walk.active || walk.walkSeconds <= 0) {
         return 0;
      }
      return Math.min(cfg().splintWalkMaxPain.get(), 1 + walk.walkSeconds / 60);
   }

   private static void hurtPart(ServerPlayer player, Frostbite.Limb limb, float amount) {
      AbstractPlayerDamageModel model = CommonUtils.getDamageModel(player);
      if (model == null || amount <= 0.0F) {
         return;
      }
      AbstractDamageablePart part = model.getFromEnum(limb.part);
      if (part.currentHealth > 0.0F) {
         part.damage(amount, player, false, 0.0F);
         CommonUtils.syncDamageModel(player);
      }
   }

   public static void forgetWalk(Player player) {
      WALK.remove(player.getUUID());
   }

   static void tickDislocations(ServerPlayer player, DislocationCondition.State state) {
      double rate = healRate(player);
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         DislocationCondition.Joint joint = state.get(limb);
         if (!joint.dislocated) {
            continue;
         }
         if (MedicalHooks.limbStatus(player, limb) == LimbVisualState.AMPUTATED) {
            joint.dislocated = false;
            state.markDirty();
            continue;
         }
         joint.heal += rate;
         if (joint.heal >= dislocationHealTicks()) {
            joint.dislocated = false;
            joint.heal = 0.0;
            player.displayClientMessage(Component.translatable("lastaid.dislocation.healed", Frostbite.limbName(limb)).withStyle(ChatFormatting.GREEN), false);
            state.markDirty();
         }
      }
   }

   /** Healing ticks for everything at once (sleeping through the night, admin command). */
   private static void addHealing(ServerPlayer player, double ticks) {
      FractureCondition.State fractures = FractureCondition.INSTANCE.state(player);
      for (FractureCondition.Bone bone : fractures.limbs) {
         if (bone.type > 0 && (bone.type != FractureCondition.OPEN || bone.bleedingStopped)) {
            // leave one tick so the normal tick finishes it (messages, healed wrong)
            bone.heal = Math.min(healTicks(bone.type) - 1.0, bone.heal + ticks);
            bone.age += (long)ticks;
         }
      }
      fractures.markDirty();
      DislocationCondition.State joints = DislocationCondition.INSTANCE.state(player);
      for (DislocationCondition.Joint joint : joints.limbs) {
         if (joint.dislocated) {
            joint.heal = Math.min(dislocationHealTicks() - 1.0, joint.heal + ticks);
         }
      }
      joints.markDirty();
   }

   // ---------------------------------------------------------------- effects

   private static int legPenaltyLevel(FractureCondition.Bone bone, DislocationCondition.Joint joint) {
      return Math.max(bone.type >= FractureCondition.CLOSED ? 2 : (bone.type == FractureCondition.HAIRLINE ? 1 : 0), joint.dislocated ? 2 : 0);
   }

   /** A leg that can't carry: closed / open fracture or dislocation. */
   public static boolean legOut(Player player, Frostbite.Limb leg) {
      if (!enabled()) {
         return false;
      }
      int bits = boneBits(player, leg); // both sides
      return (bits & 3) >= FractureCondition.CLOSED || (bits & BIT_DISLOCATED) != 0;
   }

   private static boolean armOut(Player player, Frostbite.Limb arm) {
      return bone(player, arm).type >= FractureCondition.CLOSED || joint(player, arm).dislocated;
   }

   /**
    * The arm of this hand can't hold weapons / tools: frostbite stage 4 or amputated, an open fracture,
    * or both arms broken / dislocated.
    */
   public static boolean armUseless(Player player, InteractionHand hand) {
      Frostbite.Limb arm = Frostbite.armFor(player, hand);
      if (Frostbite.INSTANCE.state(player).get(arm).isDead()) {
         return true;
      }
      if (!enabled()) {
         return false;
      }
      if (bone(player, arm).type == FractureCondition.OPEN) {
         return true;
      }
      return armOut(player, Frostbite.Limb.LEFT_ARM) && armOut(player, Frostbite.Limb.RIGHT_ARM);
   }

   /** A closed+ fracture on either arm: no two-handed items. */
   private static boolean twoHandedBlocked(Player player) {
      return enabled()
         && (bone(player, Frostbite.Limb.LEFT_ARM).type >= FractureCondition.CLOSED || bone(player, Frostbite.Limb.RIGHT_ARM).type >= FractureCondition.CLOSED);
   }

   private static boolean isTwoHanded(ItemStack stack) {
      return stack.getItem() instanceof BowItem
         || stack.getItem() instanceof CrossbowItem
         || stack.getItem() instanceof ShieldItem
         || stack.getItem() instanceof TridentItem
         || stack.getItem() instanceof PickaxeItem
         || stack.getItem() instanceof AxeItem
         || stack.getItem() instanceof MaceItem;
   }

   public static int movementFlags(ServerPlayer player) {
      int flags = 0;
      int out = 0;
      for (Frostbite.Limb leg : new Frostbite.Limb[]{Frostbite.Limb.LEFT_LEG, Frostbite.Limb.RIGHT_LEG}) {
         FractureCondition.Bone bone = bone(player, leg);
         DislocationCondition.Joint joint = joint(player, leg);
         if (bone.type >= FractureCondition.CLOSED || joint.dislocated) {
            out++;
         }
         if (bone.type >= FractureCondition.CLOSED) {
            flags |= MedicalCondition.MOVE_NO_SPRINT;
         }
         if (bone.type == FractureCondition.OPEN) {
            flags |= MedicalCondition.MOVE_NO_JUMP;
         }
      }
      if (out >= 2) {
         flags |= MedicalCondition.MOVE_NO_SPRINT | MedicalCondition.MOVE_NO_JUMP;
         if (!splintWalking(player)) {
            flags |= MedicalCondition.MOVE_CRAWL;
         }
      }
      return flags;
   }

   /** Speed / jump / attack / mining modifiers of all bones and joints together. Called once per second. */
   public static void applyEffects(ServerPlayer player) {
      double speed = 0.0;
      double jump = 0.0;
      double attack = 0.0;
      double mining = 0.0;
      if (enabled()) {
         MedicalConfig.Fracture cfg = cfg();
         int legsOut = 0;
         for (Frostbite.Limb leg : new Frostbite.Limb[]{Frostbite.Limb.LEFT_LEG, Frostbite.Limb.RIGHT_LEG}) {
            if (dead(player, leg)) {
               continue; // frostbite handles dead legs
            }
            FractureCondition.Bone bone = bone(player, leg);
            DislocationCondition.Joint joint = joint(player, leg);
            double penalty = switch (bone.type) {
               case FractureCondition.HAIRLINE -> cfg.legHairlineSpeedPenalty.get();
               case FractureCondition.CLOSED -> cfg.legClosedSpeedPenalty.get();
               case FractureCondition.OPEN -> cfg.legOpenSpeedPenalty.get();
               default -> 0.0;
            };
            if (joint.dislocated) {
               penalty = Math.max(penalty, cfg.legDislocationSpeedPenalty.get());
            }
            if (bone.healedWrong) {
               penalty = Math.max(penalty, splintCfg().healedWrongLegSpeedPenalty.get());
            }
            speed = Math.max(speed, penalty);
            if (bone.type == FractureCondition.OPEN) {
               jump = 1.0;
            }
            if (legPenaltyLevel(bone, joint) >= 2) {
               legsOut++;
            }
         }
         if (legsOut >= 2) {
            speed = splintWalking(player) ? Math.max(speed, 1.0 - cfg.walkSpeedWithSplints.get())
               : Math.max(speed, MedicalConfig.SERVER.frostbite.crawlSpeedPenalty.get());
            jump = 1.0;
         }
         Frostbite.Limb arm = Frostbite.armFor(player, InteractionHand.MAIN_HAND);
         if (!dead(player, arm)) {
            FractureCondition.Bone bone = bone(player, arm);
            double factor = 1.0;
            factor *= 1.0 - switch (bone.type) {
               case FractureCondition.HAIRLINE -> cfg.armHairlineDamagePenalty.get();
               case FractureCondition.CLOSED, FractureCondition.OPEN -> cfg.armClosedDamagePenalty.get();
               default -> 0.0;
            };
            if (joint(player, arm).dislocated) {
               factor *= 1.0 - cfg.armDislocationDamagePenalty.get();
            }
            if (bone.healedWrong) {
               factor *= 1.0 - splintCfg().healedWrongArmPenalty.get();
               mining = splintCfg().healedWrongArmPenalty.get();
            }
            attack = 1.0 - factor;
         }
      }
      EffectUtil.setMultiplier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, -speed);
      EffectUtil.setMultiplier(player, Attributes.JUMP_STRENGTH, JUMP_ID, -jump);
      EffectUtil.setMultiplier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, -attack);
      EffectUtil.setMultiplier(player, Attributes.BLOCK_BREAK_SPEED, MINING_ID, -mining);
   }

   /** Frostbite exposure multiplier: a broken limb freezes faster. */
   public static double frostbiteExposureMultiplier(Player player, Frostbite.Limb limb) {
      if (!enabled() || bone(player, limb).type <= 0) {
         return 1.0;
      }
      return 1.0 / Math.max(0.1, cfg().fracturedFrostbiteTimeMultiplier.get());
   }

   // ---------------------------------------------------------------- pain

   /** Pain floor from bones and joints (both sides: the client reads the synced limb bits). */
   public static int painFloor(Player player) {
      if (!MedicalConfig.SERVER_SPEC.isLoaded() || !MedicalConfig.SERVER.enabled.get() || !cfg().enabled.get()) {
         return 0;
      }
      MedicalConfig.Fracture cfg = cfg();
      int floor = 0;
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         int bits = boneBits(player, limb);
         int type = bits & 3;
         int pain = switch (type) {
            case FractureCondition.HAIRLINE -> cfg.hairlinePain.get();
            case FractureCondition.CLOSED -> cfg.closedPain.get();
            case FractureCondition.OPEN -> cfg.openPain.get();
            default -> 0;
         };
         if (type > 0 && (bits & BIT_SPLINT) != 0) {
            pain = Math.max(1, pain - 1);
         }
         if ((bits & BIT_DISLOCATED) != 0) {
            pain = Math.max(pain, cfg.dislocationPain.get());
         }
         floor = Math.max(floor, pain);
      }
      if (player instanceof ServerPlayer serverPlayer) {
         floor = Math.max(floor, splintWalkPain(serverPlayer));
         long now = serverPlayer.level().getGameTime();
         for (DislocationCondition.Joint joint : DislocationCondition.INSTANCE.state(serverPlayer).limbs) {
            if (joint.reducedAt < 0L) {
               continue;
            }
            long dt = now - joint.reducedAt;
            if (dt < cfg.reductionPainSeconds.get() * 20L) {
               floor = Math.max(floor, cfg.reductionPain.get());
            } else if (dt < cfg.afterReductionPainDays.get() * DAY) {
               floor = Math.max(floor, cfg.afterReductionPain.get());
            }
         }
      }
      return Math.min(5, floor);
   }

   // ---------------------------------------------------------------- client bits

   /** Bits for LimbVisualState (server): fracture type (0-3) | splinted | dislocated. */
   public static int encode(Player player, Frostbite.Limb limb) {
      if (!enabled()) {
         return 0;
      }
      FractureCondition.Bone bone = bone(player, limb);
      int bits = bone.type & 3;
      if (bone.splinted && bone.type > 0) {
         bits |= BIT_SPLINT;
      }
      if (joint(player, limb).dislocated) {
         bits |= BIT_DISLOCATED;
      }
      if (bone.type == FractureCondition.OPEN && !bone.bleedingStopped) {
         bits |= BIT_BLEEDING;
      }
      return bits;
   }

   /** Bone bits on either side. */
   public static int boneBits(Player player, Frostbite.Limb limb) {
      if (player.level().isClientSide()) {
         return LimbVisualState.clientBones(player.getId(), limb);
      }
      return encode(player, limb);
   }

   // ---------------------------------------------------------------- triggers

   private static Frostbite.Limb randomLeg(RandomSource random) {
      return random.nextBoolean() ? Frostbite.Limb.LEFT_LEG : Frostbite.Limb.RIGHT_LEG;
   }

   private static Frostbite.Limb randomArm(RandomSource random) {
      return random.nextBoolean() ? Frostbite.Limb.LEFT_ARM : Frostbite.Limb.RIGHT_ARM;
   }

   /** Chance roll; frostbitten limbs break more easily. */
   private static boolean roll(ServerPlayer player, Frostbite.Limb limb, double chance) {
      if (MedicalHooks.limbStatus(player, limb) >= 1 && MedicalHooks.limbStatus(player, limb) < 5) {
         chance *= cfg().frostbittenBreakMultiplier.get();
      }
      return player.getRandom().nextDouble() < chance;
   }

   static void onDamage(ServerPlayer player, DamageContext context) {
      if (player.getAbilities().invulnerable) {
         return;
      }
      MedicalConfig.Fracture cfg = cfg();
      RandomSource random = player.getRandom();
      DamageSource source = context.source();
      float fall = context.fallDistance();
      if (source.is(DamageTypeTags.IS_FALL) && fall > 0.0F) {
         if (fall >= cfg.fallSevereMinBlocks.get()) {
            Frostbite.Limb first = randomLeg(random);
            if (roll(player, first, cfg.fallSevereLegChance.get())) {
               breakBone(player, first, random.nextDouble() < cfg.fallSevereOpenChance.get() ? FractureCondition.OPEN : FractureCondition.CLOSED, true);
               Frostbite.Limb second = first == Frostbite.Limb.LEFT_LEG ? Frostbite.Limb.RIGHT_LEG : Frostbite.Limb.LEFT_LEG;
               if (random.nextBoolean()) {
                  breakBone(player, second, random.nextDouble() < cfg.fallSevereOpenChance.get() ? FractureCondition.OPEN : FractureCondition.CLOSED, true);
               }
            }
            Frostbite.Limb arm = randomArm(random);
            if (roll(player, arm, cfg.fallSevereArmChance.get())) {
               breakBone(player, arm, random.nextDouble() < cfg.fallSevereOpenChance.get() ? FractureCondition.OPEN : FractureCondition.CLOSED, true);
            }
         } else if (fall >= cfg.fallClosedMinBlocks.get()) {
            Frostbite.Limb leg = randomLeg(random);
            if (roll(player, leg, cfg.fallClosedChance.get())) {
               breakBone(player, leg, FractureCondition.CLOSED, true);
            }
         } else if (fall >= cfg.fallHairlineMinBlocks.get()) {
            Frostbite.Limb leg = randomLeg(random);
            if (roll(player, leg, cfg.fallHairlineChance.get())) {
               breakBone(player, leg, FractureCondition.HAIRLINE, true);
            }
         }
         return;
      }
      if (source.is(DamageTypes.FLY_INTO_WALL)) {
         Frostbite.Limb arm = randomArm(random);
         if (roll(player, arm, cfg.elytraWallChance.get())) {
            breakBone(player, arm, random.nextDouble() < cfg.elytraWallOpenChance.get() ? FractureCondition.OPEN : FractureCondition.CLOSED, true);
         }
         return;
      }
      if (source.is(DamageTypeTags.IS_EXPLOSION)) {
         if (context.totalDamage() >= cfg.explosionMinDamage.get()) {
            Frostbite.Limb limb = random.nextBoolean() ? randomArm(random) : randomLeg(random);
            if (roll(player, limb, cfg.explosionChance.get())) {
               breakBone(player, limb, FractureCondition.CLOSED, true);
            }
         }
         return;
      }
      Entity attacker = source.getEntity();
      if (attacker instanceof LivingEntity living && source.getDirectEntity() == attacker) {
         ItemStack weapon = living.getMainHandItem();
         Frostbite.Limb arm = randomArm(random);
         if (weapon.getItem() instanceof MaceItem) {
            if (roll(player, arm, cfg.maceChance.get())) {
               breakBone(player, arm, random.nextDouble() < cfg.maceOpenChance.get() ? FractureCondition.OPEN : FractureCondition.CLOSED, true);
            }
         } else if (attacker instanceof IronGolem || attacker instanceof Ravager) {
            if (roll(player, arm, cfg.heavyMobChance.get())) {
               breakBone(player, arm, FractureCondition.CLOSED, true);
            }
         } else if (attacker instanceof Player hitter && weapon.getItem() instanceof AxeItem && isCritical(hitter)) {
            if (roll(player, arm, cfg.axeCritChance.get())) {
               breakBone(player, arm, FractureCondition.HAIRLINE, true);
            }
         }
      }
   }

   private static boolean isCritical(Player player) {
      return player.fallDistance > 0.0F
         && !player.onGround()
         && !player.onClimbable()
         && !player.isInWater()
         && !player.hasEffect(MobEffects.BLINDNESS)
         && !player.isPassenger()
         && !player.isSprinting();
   }

   @SubscribeEvent
   public static void onFall(LivingFallEvent event) {
      // landing from ~3 blocks: no fall damage, but the ankle may give way
      if (event.getEntity() instanceof ServerPlayer player && enabled() && !player.getAbilities().invulnerable && !player.isSpectator()) {
         float distance = event.getDistance();
         if (distance >= 2.6F && distance < 3.5F && event.getDamageMultiplier() > 0.0F && !player.isInWater()) {
            Frostbite.Limb leg = randomLeg(player.getRandom());
            if (roll(player, leg, cfg().landingDislocationChance.get())) {
               dislocate(player, leg, true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onJump(LivingJumpEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && enabled() && !player.getAbilities().invulnerable) {
         for (Frostbite.Limb leg : new Frostbite.Limb[]{Frostbite.Limb.LEFT_LEG, Frostbite.Limb.RIGHT_LEG}) {
            if (joint(player, leg).dislocated && player.getRandom().nextDouble() < cfg().dislocationJumpFallChance.get()) {
               // the leg gives way: 2 s on the ground
               player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6, false, false, true));
               player.displayClientMessage(Component.translatable("lastaid.dislocation.fell", Frostbite.limbName(leg)).withStyle(ChatFormatting.RED), true);
               return;
            }
         }
      }
   }

   private static void refuse(Player player, String key) {
      player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_RED), true);
   }

   @SubscribeEvent
   public static void onAttack(AttackEntityEvent event) {
      if (!(event.getEntity() instanceof ServerPlayer player) || !enabled()) {
         return;
      }
      ItemStack held = player.getMainHandItem();
      if (splintWalking(player)) {
         event.setCanceled(true);
         refuse(player, "lastaid.splint_walk.no_attack");
         return;
      }
      if (twoHandedBlocked(player) && isTwoHanded(held)) {
         event.setCanceled(true);
         refuse(player, "lastaid.fracture.two_handed");
         return;
      }
      Frostbite.Limb arm = Frostbite.armFor(player, InteractionHand.MAIN_HAND);
      if (joint(player, arm).dislocated && !held.isEmpty() && player.getRandom().nextDouble() < cfg().dislocationDropChance.get()) {
         player.drop(held.copy(), true);
         player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
         player.displayClientMessage(Component.translatable("lastaid.dislocation.dropped").withStyle(ChatFormatting.RED), true);
      }
   }

   @SubscribeEvent
   public static void onLeftClickBlock(LeftClickBlock event) {
      if (event.getEntity() instanceof ServerPlayer player && twoHandedBlocked(player) && isTwoHanded(player.getMainHandItem())) {
         event.setCanceled(true);
         refuse(player, "lastaid.fracture.two_handed");
      }
   }

   @SubscribeEvent
   public static void onBreakSpeed(BreakSpeed event) {
      Player player = event.getEntity();
      if (!player.level().isClientSide() && twoHandedBlocked(player) && isTwoHanded(player.getMainHandItem())) {
         event.setNewSpeed(0.0F);
      }
   }

   @SubscribeEvent
   public static void onRightClickItem(RightClickItem event) {
      if (event.getEntity() instanceof ServerPlayer player && twoHandedBlocked(player)) {
         ItemStack stack = event.getItemStack();
         if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof ShieldItem || stack.getItem() instanceof TridentItem) {
            event.setCanceled(true);
            refuse(player, "lastaid.fracture.two_handed");
         }
      }
   }

   @SubscribeEvent
   public static void onShieldBlock(LivingShieldBlockEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getBlocked() && twoHandedBlocked(player)) {
         event.setBlocked(false);
      }
   }

   /** Sneak + right click on a player with an empty hand: reduce their dislocation. */
   @SubscribeEvent
   public static void onEntityInteract(EntityInteract event) {
      if (!(event.getEntity() instanceof ServerPlayer medic) || !(event.getTarget() instanceof ServerPlayer patient) || !enabled()) {
         return;
      }
      if (event.getHand() != InteractionHand.MAIN_HAND || !medic.isShiftKeyDown() || !medic.getMainHandItem().isEmpty()) {
         return;
      }
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         if (joint(patient, limb).dislocated) {
            if (medic.distanceTo(patient) <= 2.5F && !MedicalProcedures.isBusy(medic)) {
               MedicalProcedures.startReduce(medic, patient, limb);
            }
            event.setCanceled(true);
            return;
         }
      }
   }

   @SubscribeEvent
   public static void onSleepFinished(SleepFinishedTimeEvent event) {
      if (!enabled() || !(event.getLevel() instanceof ServerLevel level)) {
         return;
      }
      long skipped = event.getNewTime() - level.getDayTime();
      if (skipped <= 0L) {
         return;
      }
      for (ServerPlayer player : level.players()) {
         if (player.isSleepingLongEnough()) {
            double mult = cfg().sleepHealMultiplier.get();
            if (player.getFoodData().getFoodLevel() < 6) {
               mult *= cfg().hungryHealMultiplier.get();
            }
            addHealing(player, skipped * mult);
         }
      }
   }

   // ---------------------------------------------------------------- treatment

   /** Limb for a splint put on by someone else: legs first, then arms; open fractures first. */
   @Nullable
   public static Frostbite.Limb splintTarget(Player patient) {
      Frostbite.Limb best = null;
      int bestScore = 0;
      for (Frostbite.Limb limb : new Frostbite.Limb[]{Frostbite.Limb.LEFT_LEG, Frostbite.Limb.RIGHT_LEG, Frostbite.Limb.LEFT_ARM, Frostbite.Limb.RIGHT_ARM}) {
         FractureCondition.Bone bone = bone(patient, limb);
         if (bone.type > 0 && !bone.splinted && bone.type > bestScore) {
            best = limb;
            bestScore = bone.type;
         }
      }
      return best;
   }

   public static boolean canSplint(Player patient, Frostbite.Limb limb) {
      FractureCondition.Bone bone = bone(patient, limb);
      return enabled() && bone.type > 0 && !bone.splinted;
   }

   public static void finishSplint(ServerPlayer medic, ServerPlayer patient, Frostbite.Limb limb, ItemStack splint) {
      FractureCondition.State state = FractureCondition.INSTANCE.state(patient);
      FractureCondition.Bone bone = state.get(limb);
      if (bone.type <= 0 || bone.splinted) {
         return;
      }
      bone.splinted = true;
      state.markDirty();
      if (!medic.getAbilities().instabuild) {
         splint.shrink(1);
      }
      patient.level().playSound(null, patient.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
      Component msg = Component.translatable("lastaid.splint.done", Frostbite.limbName(limb)).withStyle(ChatFormatting.GREEN);
      medic.displayClientMessage(msg, true);
      if (medic != patient) {
         patient.displayClientMessage(msg, true);
      }
   }

   public static void finishReduce(ServerPlayer medic, ServerPlayer patient, Frostbite.Limb limb) {
      DislocationCondition.State state = DislocationCondition.INSTANCE.state(patient);
      DislocationCondition.Joint joint = state.get(limb);
      if (!joint.dislocated) {
         return;
      }
      joint.dislocated = false;
      joint.heal = 0.0;
      joint.reducedAt = patient.level().getGameTime();
      state.markDirty();
      patient.level().playSound(null, patient.blockPosition(), SoundEvents.BONE_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
      Component msg = Component.translatable("lastaid.reduce.done", Frostbite.limbName(limb)).withStyle(ChatFormatting.GREEN);
      medic.displayClientMessage(msg, true);
      if (medic != patient) {
         patient.displayClientMessage(msg, true);
      }
      applyEffects(patient);
   }

   /** A bandage / plaster put on a limb stops the bleeding of an open fracture there. */
   public static void onBandaged(ServerPlayer player, EnumPlayerPart part) {
      Frostbite.Limb limb = Frostbite.Limb.of(part);
      if (limb == null || !enabled()) {
         return;
      }
      FractureCondition.State state = FractureCondition.INSTANCE.state(player);
      FractureCondition.Bone bone = state.get(limb);
      if (bone.type == FractureCondition.OPEN && !bone.bleedingStopped) {
         bone.bleedingStopped = true;
         state.markDirty();
         player.displayClientMessage(Component.translatable("lastaid.fracture.bleeding_stopped", Frostbite.limbName(limb)).withStyle(ChatFormatting.GREEN), true);
      }
   }

   /** A wound or old dressing on this part: a rag / plaster / bandage may be put on even if the part looks full. */
   public static boolean isBleeding(Player player, EnumPlayerPart part) {
      if (player.level().isClientSide()) {
         return com.duckerquacker.lastaid.common.medical.ClientMedicalState.acceptsDressing(player, part);
      }
      return com.duckerquacker.lastaid.common.medical.wounds.Wounds.acceptsDressing(player, part);
   }

   /** Alcohol on the wound of an open fracture counts as treated (no infection roll). */
   public static void onWoundDisinfected(ServerPlayer player, com.duckerquacker.lastaid.common.medical.wounds.Zone zone) {
      if (zone.limb == null || !enabled()) {
         return;
      }
      FractureCondition.State state = FractureCondition.INSTANCE.state(player);
      FractureCondition.Bone bone = state.get(zone.limb);
      if (bone.type == FractureCondition.OPEN && !bone.treated) {
         bone.treated = true;
         state.markDirty();
      }
   }

   /** First Aid's "nothing to heal" check, except a bleeding open fracture can always take a bandage. */
   public static boolean fullForBandage(Player player, com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart part) {
      return CommonUtils.isPartVisuallyFull(part) && !isBleeding(player, part.part);
   }

   public static boolean needsMedicalKit(Player patient) {
      if (!enabled()) {
         return false;
      }
      for (FractureCondition.Bone bone : FractureCondition.INSTANCE.state(patient).limbs) {
         if (bone.healedWrong || bone.type == FractureCondition.OPEN && !bone.treated) {
            return true;
         }
      }
      return false;
   }

   /**
    * Medical kit: cleans open fractures (no infection, bleeding stopped); a bone that healed wrong is broken again
    * on purpose (closed fracture, needs a splint this time).
    */
   public static void onMedicalKit(ServerPlayer patient) {
      if (!enabled()) {
         return;
      }
      FractureCondition.State state = FractureCondition.INSTANCE.state(patient);
      List<Frostbite.Limb> rebroken = new ArrayList<>();
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         FractureCondition.Bone bone = state.get(limb);
         if (bone.type == FractureCondition.OPEN) {
            bone.treated = true;
            bone.bleedingStopped = true;
         } else if (bone.healedWrong && bone.type == 0) {
            bone.healedWrong = false;
            bone.reset();
            bone.type = FractureCondition.CLOSED;
            rebroken.add(limb);
         }
      }
      state.markDirty();
      for (Frostbite.Limb limb : rebroken) {
         patient.level().playSound(null, patient.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
         patient.sendSystemMessage(Component.translatable("lastaid.fracture.rebroken", Frostbite.limbName(limb)).withStyle(ChatFormatting.GOLD));
      }
      applyEffects(patient);
   }

   /** Called by the procedure system for the panel / packet. */
   public static void requestSplint(ServerPlayer player, InteractionHand hand, Frostbite.Limb limb) {
      if (!enabled() || MedicalProcedures.isBusy(player)) {
         return;
      }
      if (!player.getItemInHand(hand).is(com.duckerquacker.lastaid.common.medical.MedicalRegistry.SPLINT.get())) {
         return;
      }
      if (!canSplint(player, limb)) {
         player.displayClientMessage(Component.translatable("lastaid.splint.not_needed").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      MedicalProcedures.startSplint(player, player, hand, limb);
   }

   public static void requestReduce(ServerPlayer player, Frostbite.Limb limb) {
      if (!enabled() || MedicalProcedures.isBusy(player) || !joint(player, limb).dislocated) {
         return;
      }
      MedicalProcedures.startReduce(player, player, limb);
   }

   @SubscribeEvent
   public static void onMount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
      if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer player && splintWalking(player)
         && !(event.getEntityBeingMounted() instanceof Player)) {
         boolean helper = !player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(4.0), p -> p != player && p.isAlive() && !p.isSpectator()).isEmpty();
         if (!helper) {
            event.setCanceled(true);
            refuse(player, "lastaid.splint_walk.no_mount");
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
      forgetWalk(event.getEntity());
   }
}
