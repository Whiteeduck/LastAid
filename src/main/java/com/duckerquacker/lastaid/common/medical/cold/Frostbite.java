package com.duckerquacker.lastaid.common.medical.cold;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalIcons;
import com.duckerquacker.lastaid.common.medical.PlayerCommentTrigger;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

public final class Frostbite implements MedicalCondition<Frostbite.State> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lastaid", "frostbite");
   public static final Frostbite INSTANCE = new Frostbite();
   private static final ResourceLocation ATTACK_ID = EffectUtil.id("frostbite_attack");
   private static final ResourceLocation MINING_ID = EffectUtil.id("frostbite_mining");
   private static final ResourceLocation SPEED_ID = EffectUtil.id("frostbite_speed");
   private static final ResourceLocation JUMP_ID = EffectUtil.id("frostbite_jump");
   private static final int[] STAGE_TINT = new int[]{0, -2136422160, -1602589736, -1068815750, -401600496};
   private static final int[] STAGE_COLOR = new int[]{-1, -5715728, -7701528, -8762704, -13619152};

   private Frostbite() {
   }

   public Frostbite.State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   private static MedicalConfig.Frostbite cfg() {
      return MedicalConfig.SERVER.frostbite;
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   public Frostbite.State createState() {
      return new Frostbite.State();
   }

   @Override
   public int getDefaultColor() {
      return -7701528;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return MedicalIcons.SNOWFLAKE;
   }

   public boolean isActive(Frostbite.State state) {
      for (Frostbite.LimbState limbState : state.limbs) {
         if (limbState.stage > 0 || limbState.amputated) {
            return true;
         }
      }

      return false;
   }

   public static Frostbite.Limb armFor(Player player, InteractionHand interactionHand) {
      boolean bl = player.getMainArm() == HumanoidArm.RIGHT;
      boolean bl1 = interactionHand == InteractionHand.MAIN_HAND;
      return bl1 == bl ? Frostbite.Limb.RIGHT_ARM : Frostbite.Limb.LEFT_ARM;
   }

   public boolean isArmDead(Player player, InteractionHand interactionHand) {
      return this.state(player).get(armFor(player, interactionHand)).isDead();
   }

   public int armStage(Player player, InteractionHand interactionHand) {
      Frostbite.LimbState limbState = this.state(player).get(armFor(player, interactionHand));
      return limbState.amputated ? 4 : limbState.stage;
   }

   private static double threshold(int i) {
      MedicalConfig.Frostbite frostbite = cfg();

      return switch (i) {
         case 1 -> frostbite.exposureStage1.get();
         case 2 -> frostbite.exposureStage2.get();
         case 3 -> frostbite.exposureStage3.get();
         case 4 -> frostbite.exposureStage4.get();
         default -> 0.0;
      };
   }

   private static int healSeconds(int i) {
      MedicalConfig.Frostbite frostbite = cfg();

      return switch (i) {
         case 1 -> frostbite.healSecondsStage1.get();
         case 2 -> frostbite.healSecondsStage2.get();
         case 3 -> frostbite.healSecondsStage3.get();
         default -> frostbite.healSecondsStage4.get();
      };
   }

   private static double protection(ServerPlayer serverPlayer, Frostbite.Limb limb) {
      return limb.arm
         ? protection(serverPlayer.getItemBySlot(EquipmentSlot.CHEST))
         : Math.max(protection(serverPlayer.getItemBySlot(EquipmentSlot.LEGS)), protection(serverPlayer.getItemBySlot(EquipmentSlot.FEET)));
   }

   private static double protection(ItemStack itemStack) {
      if (itemStack.isEmpty()) {
         return 1.0;
      } else {
         String string = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

         for (String string1 : cfg().furItems.get()) {
            if (string1.endsWith("*") ? string.startsWith(string1.substring(0, string1.length() - 1)) : string.equals(string1)) {
               return (Double)cfg().furProtection.get();
            }
         }

         return itemStack.getItem() instanceof ArmorItem armorItem
            ? armorItem.getMaterial().is(ArmorMaterials.LEATHER) ? (Double)cfg().leatherProtection.get() : (Double)cfg().otherArmorProtection.get()
            : 1.0;
      }
   }

   private static double weight(int i) {
      MedicalConfig.Frostbite frostbite = cfg();

      return switch (i) {
         case 2 -> frostbite.weightHypothermia2.get();
         case 3 -> frostbite.weightHypothermia3.get();
         case 4 -> frostbite.weightHypothermia4.get();
         default -> 0.0;
      };
   }

   public void tick(ServerPlayer serverPlayer, Frostbite.State state, MedicalContext medicalContext) {
      if (!(Boolean)cfg().enabled.get()) {
         this.applyAttributes(serverPlayer, state);
      } else {
         if (!medicalContext.temperatureFrozen()) {
            int i = Hypothermia.stage(serverPlayer);
            boolean bl = i == 0 || medicalContext.nearHeatSource();
            AbstractPlayerDamageModel abstractPlayerDamageModel = medicalContext.damageModel();
            boolean bl1 = false;

            for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
               bl1 |= this.tickLimb(serverPlayer, state, limb, state.get(limb), i, bl, abstractPlayerDamageModel);
            }

            if (bl1) {
               CommonUtils.syncDamageModel(serverPlayer);
            }
         }

         this.applyAttributes(serverPlayer, state);
      }
   }

   private boolean tickLimb(
      ServerPlayer serverPlayer,
      Frostbite.State state,
      Frostbite.Limb limb,
      Frostbite.LimbState limbState,
      int i,
      boolean bl,
      AbstractPlayerDamageModel abstractPlayerDamageModel
   ) {
      boolean bl1 = false;
      if (limbState.amputated) {
         return forceDead(abstractPlayerDamageModel, limb);
      } else {
         if ((
               abstractPlayerDamageModel.getFromEnum(limb.part).activeHealer != null
                  || limb.extra != null && abstractPlayerDamageModel.getFromEnum(limb.extra).activeHealer != null
            )
            && !limbState.bandaged
            && limbState.stage >= 2) {
            limbState.bandaged = true;
            state.markDirty();
         }

         double d = bl ? 0.0 : weight(i);
         if (d > 0.0) {
            d *= 0.8 + serverPlayer.getRandom().nextDouble() * 0.4;
            d /= protection(serverPlayer, limb);
            d *= com.duckerquacker.lastaid.common.medical.bone.Bones.frostbiteExposureMultiplier(serverPlayer, limb);
            limbState.exposure += (float)d;
            if (limbState.heal > 0.0F) {
               limbState.heal = 0.0F;
               state.markDirty();
            }

            while (limbState.stage < 4 && limbState.exposure >= threshold(limbState.stage + 1)) {
               limbState.stage++;
               this.onStageUp(serverPlayer, limb, limbState);
               state.markDirty();
            }
         } else if (bl) {
            if (limbState.stage == 0) {
               limbState.exposure = Math.max(0.0F, limbState.exposure - 1.0F);
            } else if (canHeal(limbState)) {
               limbState.heal++;
               if (limbState.heal >= healSeconds(limbState.stage)) {
                  limbState.stage--;
                  limbState.heal = 0.0F;
                  limbState.bandaged = false;
                  limbState.medkit = false;
                  limbState.exposure = (float)threshold(limbState.stage);
                  serverPlayer.displayClientMessage(
                     Component.translatable("lastaid.frostbite.better", new Object[]{limbName(limb)}).withStyle(ChatFormatting.GREEN), true
                  );
               }

               if ((int)limbState.heal % 5 == 0) {
                  state.markDirty();
               }
            }
         }

         if ((limbState.stage == 2 || limbState.stage == 3) && --limbState.damageTimer <= 0) {
            limbState.damageTimer = 10;
            float f = (limbState.stage == 2 ? (Double)cfg().limbDamageStage2.get() : (Double)cfg().limbDamageStage3.get()).floatValue();
            AbstractDamageablePart abstractDamageablePart = abstractPlayerDamageModel.getFromEnum(limb.part);
            if (f > 0.0F && abstractDamageablePart.currentHealth > 0.0F) {
               abstractDamageablePart.damage(f, serverPlayer, true, 0.0F);
               bl1 = true;
            }
         }

         if (limbState.stage >= 4) {
            bl1 |= forceDead(abstractPlayerDamageModel, limb);
            if (++limbState.infectionTimer >= (Integer)cfg().infectionCheckSeconds.get()) {
               limbState.infectionTimer = 0;
               if (serverPlayer.getRandom().nextDouble() < (Double)cfg().infectionChance.get()) {
                  com.duckerquacker.lastaid.common.medical.infection.Infection.INSTANCE.start(serverPlayer, com.duckerquacker.lastaid.common.medical.wounds.Zone.of(limb), "lastaid.infection.source.gangrene");
               }
            }
         }

         return bl1;
      }
   }

   private static boolean forceDead(AbstractPlayerDamageModel abstractPlayerDamageModel, Frostbite.Limb limb) {
      boolean bl = false;

      for (EnumPlayerPart enumPlayerPart : new EnumPlayerPart[]{limb.part, limb.extra}) {
         if (enumPlayerPart != null) {
            AbstractDamageablePart abstractDamageablePart = abstractPlayerDamageModel.getFromEnum(enumPlayerPart);
            if (abstractDamageablePart.currentHealth > 0.0F) {
               abstractDamageablePart.currentHealth = 0.0F;
               bl = true;
            }
         }
      }

      return bl;
   }

   private static boolean canHeal(Frostbite.LimbState limbState) {
      return switch (limbState.stage) {
         case 1 -> true;
         case 2 -> limbState.bandaged;
         case 3 -> limbState.medkit;
         case 4 -> !cfg().permanentDeath.get() && limbState.medkit;
         default -> false;
      };
   }

   private void onStageUp(ServerPlayer serverPlayer, Frostbite.Limb limb, Frostbite.LimbState limbState) {
      String string = limb.arm ? "arm" : "leg";
      switch (limbState.stage) {
         case 1:
            PlayerCommentTrigger.comment(serverPlayer, "frostbite_1_" + string);
            break;
         case 2:
            PlayerCommentTrigger.comment(serverPlayer, "frostbite_2_" + string);
            break;
         case 3:
            PlayerCommentTrigger.comment(serverPlayer, "frostbite_3_" + string);
            break;
         case 4:
            PlayerCommentTrigger.comment(serverPlayer, "gangrene_" + string);
            serverPlayer.sendSystemMessage(
               Component.translatable("lastaid.frostbite.dead_" + string, new Object[]{serverPlayer.getDisplayName(), limbName(limb)})
                  .withStyle(ChatFormatting.DARK_RED)
            );
            limbState.infectionTimer = 0;
      }
   }

   public static Component limbName(Frostbite.Limb limb) {
      return Component.translatable("lastaid.limb." + limb.key());
   }

   private void applyAttributes(ServerPlayer serverPlayer, Frostbite.State state) {
      MedicalConfig.Frostbite frostbite = cfg();
      Frostbite.Limb limb = armFor(serverPlayer, InteractionHand.MAIN_HAND);
      Frostbite.LimbState limbState = state.get(limb);
      int i = limbState.amputated ? 4 : limbState.stage;

      double d = switch (i) {
         case 1 -> -frostbite.attackPenaltyStage1.get();
         case 2 -> -frostbite.attackPenaltyStage2.get();
         case 3 -> -frostbite.attackPenaltyStage3.get();
         case 4 -> -0.9;
         default -> 0.0;
      };

      double e = switch (i) {
         case 2 -> -frostbite.miningPenaltyStage2.get();
         case 3, 4 -> -frostbite.miningPenaltyStage3.get();
         default -> 0.0;
      };
      int j = 0;

      for (Frostbite.Limb limb1 : new Frostbite.Limb[]{Frostbite.Limb.LEFT_LEG, Frostbite.Limb.RIGHT_LEG}) {
         Frostbite.LimbState limbState1 = state.get(limb1);
         if (!limbState1.isDead()) {
            j = Math.max(j, limbState1.stage);
         }
      }

      int k = state.deadLegs();

      double f = switch (j) {
         case 1 -> -frostbite.speedPenaltyStage1.get();
         case 2 -> -frostbite.speedPenaltyStage2.get();
         case 3 -> -frostbite.speedPenaltyStage3.get();
         default -> 0.0;
      };

      double g = switch (j) {
         case 2 -> -frostbite.jumpPenaltyStage2.get();
         case 3 -> -frostbite.jumpPenaltyStage3.get();
         default -> 0.0;
      };
      if (k >= 2) {
         f = -(Double)frostbite.crawlSpeedPenalty.get();
         g = -1.0;
      } else if (k == 1) {
         f = Math.min(f, -(Double)frostbite.deadLegSpeedPenalty.get());
         g = -1.0;
      }

      if (!(Boolean)frostbite.enabled.get()) {
         g = 0.0;
         f = 0.0;
         e = 0.0;
         d = 0.0;
      }

      EffectUtil.setMultiplier(serverPlayer, Attributes.ATTACK_DAMAGE, ATTACK_ID, d);
      EffectUtil.setMultiplier(serverPlayer, Attributes.BLOCK_BREAK_SPEED, MINING_ID, e);
      EffectUtil.setMultiplier(serverPlayer, Attributes.MOVEMENT_SPEED, SPEED_ID, f);
      EffectUtil.setMultiplier(serverPlayer, Attributes.JUMP_STRENGTH, JUMP_ID, g);
   }

   public float incomingDamageMultiplier(ServerPlayer serverPlayer, Frostbite.State state, EnumPlayerPart enumPlayerPart) {
      Frostbite.Limb limb = Frostbite.Limb.of(enumPlayerPart);
      if (limb != null && (Boolean)cfg().enabled.get()) {
         Frostbite.LimbState limbState = state.get(limb);
         return !limbState.amputated && limbState.stage < cfg().damageMultiplierFromStage.get() ? 1.0F : ((Double)cfg().damageMultiplier.get()).floatValue();
      } else {
         return 1.0F;
      }
   }

   public boolean blocksHealing(ServerPlayer serverPlayer, Frostbite.State state, EnumPlayerPart enumPlayerPart) {
      Frostbite.Limb limb = Frostbite.Limb.of(enumPlayerPart);
      return limb != null && state.get(limb).isDead();
   }

   public int movementFlags(ServerPlayer serverPlayer, Frostbite.State state) {
      int i = state.deadLegs();
      if (i >= 2) {
         return 7;
      } else {
         return i == 1 ? 3 : 0;
      }
   }

   public boolean onMedicalKit(ServerPlayer serverPlayer) {
      Frostbite.State state = this.state(serverPlayer);
      boolean bl = false;

      for (Frostbite.LimbState limbState : state.limbs) {
         if (!limbState.amputated && (limbState.stage == 3 || limbState.stage == 4 && !(Boolean)cfg().permanentDeath.get())) {
            limbState.medkit = true;
            bl = true;
         }
      }

      if (bl) {
         state.markDirty();
      }

      return bl;
   }

   public boolean needsMedicalKit(Player player) {
      for (Frostbite.LimbState limbState : this.state(player).limbs) {
         if (!limbState.amputated && !limbState.medkit && (limbState.stage == 3 || limbState.stage == 4 && !(Boolean)cfg().permanentDeath.get())) {
            return true;
         }
      }

      return false;
   }

   public Frostbite.Limb amputationTarget(Player player) {
      if (!(Boolean)cfg().permanentDeath.get()) {
         return null;
      } else {
         Frostbite.State state = this.state(player);

         for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
            Frostbite.LimbState limbState = state.get(limb);
            if (limbState.stage >= 4 && !limbState.amputated) {
               return limb;
            }
         }

         return null;
      }
   }

   public void amputate(ServerPlayer serverPlayer, Frostbite.Limb limb) {
      Frostbite.State state = this.state(serverPlayer);
      Frostbite.LimbState limbState = state.get(limb);
      limbState.amputated = true;
      limbState.stage = 4;
      limbState.heal = 0.0F;
      limbState.infectionTimer = 0;
      limbState.amputatedAt = serverPlayer.level().getGameTime();
      state.markDirty();
      AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer);
      if (abstractPlayerDamageModel != null && forceDead(abstractPlayerDamageModel, limb)) {
         CommonUtils.syncDamageModel(serverPlayer);
      }
      com.duckerquacker.lastaid.common.medical.Amputation.onAmputated(serverPlayer, limb);
   }

   /** Gives an amputated limb back (admin command). The limb comes back healthy. */
   public boolean restore(ServerPlayer serverPlayer, Frostbite.Limb limb) {
      Frostbite.State state = this.state(serverPlayer);
      Frostbite.LimbState limbState = state.get(limb);
      if (!limbState.amputated) {
         return false;
      }
      limbState.amputated = false;
      limbState.amputatedAt = -1L;
      limbState.stage = 0;
      limbState.exposure = 0.0F;
      limbState.heal = 0.0F;
      limbState.bandaged = false;
      limbState.medkit = false;
      state.markDirty();
      AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(serverPlayer);
      if (damageModel != null) {
         for (EnumPlayerPart enumPlayerPart : new EnumPlayerPart[]{limb.part, limb.extra}) {
            if (enumPlayerPart != null) {
               AbstractDamageablePart part = damageModel.getFromEnum(enumPlayerPart);
               part.currentHealth = part.getMaxHealth();
            }
         }
         CommonUtils.syncDamageModel(serverPlayer);
      }
      this.applyAttributes(serverPlayer, state);
      return true;
   }

   public void clear(ServerPlayer serverPlayer, Frostbite.State state) {
      for (Frostbite.LimbState limbState : state.limbs) {
         // Amputations are permanent and survive /medical clear and /medical heal (use /medical restore)
         limbState.stage = limbState.amputated ? 4 : 0;
         limbState.exposure = 0.0F;
         limbState.heal = 0.0F;
         limbState.bandaged = false;
         limbState.medkit = false;
      }

      state.markDirty();
      this.applyAttributes(serverPlayer, state);
   }

   public void setStage(ServerPlayer serverPlayer, Frostbite.Limb limb, int i) {
      Frostbite.State state = this.state(serverPlayer);
      Frostbite.LimbState limbState = state.get(limb);
      int j = limbState.stage;
      limbState.stage = Mth.clamp(i, 0, 4);
      limbState.amputated = false;
      limbState.exposure = (float)threshold(limbState.stage);
      limbState.heal = 0.0F;
      if (limbState.stage > j) {
         this.onStageUp(serverPlayer, limb, limbState);
      }

      state.markDirty();
   }

   public ConditionSnapshot snapshot(ServerPlayer serverPlayer, Frostbite.State state, MedicalContext medicalContext) {
      throw new UnsupportedOperationException("Frostbite uses collectSnapshots");
   }

   public void collectSnapshots(ServerPlayer serverPlayer, Frostbite.State state, MedicalContext medicalContext, List<ConditionSnapshot> list) {
      boolean bl = (Boolean)cfg().permanentDeath.get();

      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         Frostbite.LimbState limbState = state.get(limb);
         if (limbState.stage > 0 || limbState.amputated) {
            ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath("lastaid", "frostbite_" + limb.key());
            MutableComponent mutableComponent = Component.translatable(limbState.amputated ? "lastaid.condition.amputated" : "lastaid.condition.frostbite")
               .append(Component.literal(" — "))
               .append(limbName(limb));
            ArrayList arrayList = new ArrayList();
            ArrayList arrayList1 = new ArrayList();
            int i;
            float f;
            if (limbState.amputated) {
               arrayList.add(Component.translatable("lastaid.frostbite.amputated").withStyle(ChatFormatting.GRAY));
               arrayList1.add(Component.translatable("lastaid.frostbite.detail.amputated_" + (limb.arm ? "arm" : "leg")));
               i = 6;
               f = -1.0F;
            } else {
               arrayList.add(Component.translatable("lastaid.frostbite.stage." + limbState.stage));
               f = limbState.stage >= 4 && bl ? -1.0F : Mth.clamp(limbState.heal / healSeconds(limbState.stage), 0.0F, 1.0F);
               if (f >= 0.0F) {
                  arrayList.add(Component.translatable("lastaid.concussion.tooltip.progress", new Object[]{Math.round(f * 100.0F)}));
               }

               Component component = requirement(limbState, bl);
               if (component != null) {
                  arrayList.add(component);
               }

               if (limbState.stage < 4) {
                  float g = (float)(limbState.exposure / threshold(limbState.stage + 1));
                  arrayList.add(
                     Component.translatable("lastaid.frostbite.exposure", new Object[]{Math.round(Mth.clamp(g, 0.0F, 1.0F) * 100.0F)})
                        .withStyle(ChatFormatting.GRAY)
                  );
               }

               arrayList1.add(Component.translatable("lastaid.frostbite.detail.description"));
               arrayList1.add(Component.empty());
               arrayList1.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
               arrayList1.add(
                  Component.literal(" • ").append(Component.translatable("lastaid.frostbite.detail." + (limb.arm ? "arm" : "leg") + limbState.stage))
               );
               arrayList1.add(Component.empty());
               arrayList1.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));
               arrayList1.add(
                  Component.literal(" + ")
                     .append(
                        Component.translatable(
                           "lastaid.frostbite.detail.heal" + Math.min(limbState.stage, 4) + (limbState.stage >= 4 && bl ? "_permanent" : "")
                        )
                     )
                     .withStyle(ChatFormatting.GREEN)
               );
               arrayList1.add(Component.literal(" + ").append(Component.translatable("lastaid.frostbite.detail.protect")).withStyle(ChatFormatting.GREEN));
               arrayList1.add(Component.translatable("lastaid.concussion.detail.hurts_header").withStyle(ChatFormatting.RED));
               arrayList1.add(Component.literal(" - ").append(Component.translatable("lastaid.frostbite.detail.hurts")).withStyle(ChatFormatting.RED));
               if (component != null) {
                  arrayList1.add(Component.empty());
                  arrayList1.add(component);
               }

               i = limbState.stage >= 4 ? 2 : 0;
            }

            int j = limbState.amputated ? -1070583760 : STAGE_TINT[limbState.stage];
            int k = limbState.amputated ? -10461088 : STAGE_COLOR[limbState.stage];
            list.add(
               new ConditionSnapshot(
                  resourceLocation,
                  mutableComponent,
                  k,
                  limbState.amputated ? 4 : limbState.stage,
                  limb.mask(),
                  f,
                  limbState.stage >= 3,
                  arrayList,
                  arrayList1,
                  0,
                  limbState.amputated ? null : MedicalIcons.SNOWFLAKE,
                  j,
                  i
               )
            );
         }
      }
   }

   private static Component requirement(Frostbite.LimbState limbState, boolean bl) {
      return switch (limbState.stage) {
         case 2 -> limbState.bandaged ? null : Component.translatable("lastaid.frostbite.need_bandage").withStyle(ChatFormatting.YELLOW);
         case 3 -> limbState.medkit ? null : Component.translatable("lastaid.frostbite.need_medkit").withStyle(ChatFormatting.YELLOW);
         case 4 -> bl
            ? Component.translatable("lastaid.frostbite.need_amputation").withStyle(ChatFormatting.DARK_RED)
            : (limbState.medkit ? null : Component.translatable("lastaid.frostbite.need_medkit").withStyle(ChatFormatting.YELLOW));
         default -> null;
      };
   }

   public static enum Limb {
      RIGHT_ARM(EnumPlayerPart.RIGHT_ARM, null, true),
      LEFT_ARM(EnumPlayerPart.LEFT_ARM, null, true),
      RIGHT_LEG(EnumPlayerPart.RIGHT_LEG, EnumPlayerPart.RIGHT_FOOT, false),
      LEFT_LEG(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.LEFT_FOOT, false);

      public static final Frostbite.Limb[] VALUES = values();
      public final EnumPlayerPart part;
      public final EnumPlayerPart extra;
      public final boolean arm;

      private Limb(EnumPlayerPart enumPlayerPart, EnumPlayerPart enumPlayerPart1, boolean bl) {
         this.part = enumPlayerPart;
         this.extra = enumPlayerPart1;
         this.arm = bl;
      }

      public boolean contains(EnumPlayerPart enumPlayerPart) {
         return enumPlayerPart == this.part || enumPlayerPart == this.extra;
      }

      public int mask() {
         return this.extra == null ? ConditionSnapshot.mask(this.part) : ConditionSnapshot.mask(this.part, this.extra);
      }

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      public static Frostbite.Limb of(EnumPlayerPart enumPlayerPart) {
         for (Frostbite.Limb limb : VALUES) {
            if (limb.contains(enumPlayerPart)) {
               return limb;
            }
         }

         return null;
      }
   }

   public static final class LimbState {
      public int stage;
      public float exposure;
      public float heal;
      public boolean bandaged;
      public boolean medkit;
      public boolean amputated;
      /** Game time of the amputation (for the pain spike), -1 = unknown / long ago. */
      public long amputatedAt = -1L;
      int damageTimer = 10;
      int infectionTimer;

      CompoundTag save() {
         CompoundTag compoundTag = new CompoundTag();
         compoundTag.putInt("stage", this.stage);
         compoundTag.putFloat("exposure", this.exposure);
         compoundTag.putFloat("heal", this.heal);
         compoundTag.putBoolean("bandaged", this.bandaged);
         compoundTag.putBoolean("medkit", this.medkit);
         compoundTag.putBoolean("amputated", this.amputated);
         compoundTag.putLong("amputatedAt", this.amputatedAt);
         return compoundTag;
      }

      void load(CompoundTag compoundTag) {
         this.stage = Mth.clamp(compoundTag.getInt("stage"), 0, 4);
         this.exposure = compoundTag.getFloat("exposure");
         this.heal = compoundTag.getFloat("heal");
         this.bandaged = compoundTag.getBoolean("bandaged");
         this.medkit = compoundTag.getBoolean("medkit");
         this.amputated = compoundTag.getBoolean("amputated");
         this.amputatedAt = compoundTag.contains("amputatedAt") ? compoundTag.getLong("amputatedAt") : -1L;
      }

      public boolean isDead() {
         return this.amputated || this.stage >= 4;
      }
   }

   public static final class State extends ConditionState {
      public final Frostbite.LimbState[] limbs = new Frostbite.LimbState[Frostbite.Limb.VALUES.length];

      State() {
         for (int i = 0; i < this.limbs.length; i++) {
            this.limbs[i] = new Frostbite.LimbState();
         }
      }

      public Frostbite.LimbState get(Frostbite.Limb limb) {
         return this.limbs[limb.ordinal()];
      }

      @Override
      public CompoundTag save() {
         CompoundTag compoundTag = new CompoundTag();

         for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
            compoundTag.put(limb.key(), this.get(limb).save());
         }

         return compoundTag;
      }

      @Override
      public void load(CompoundTag compoundTag) {
         for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
            this.get(limb).load(compoundTag.getCompound(limb.key()));
         }

         this.markDirty();
      }

      public int deadLegs() {
         return (this.get(Frostbite.Limb.LEFT_LEG).isDead() ? 1 : 0) + (this.get(Frostbite.Limb.RIGHT_LEG).isDead() ? 1 : 0);
      }
   }
}
