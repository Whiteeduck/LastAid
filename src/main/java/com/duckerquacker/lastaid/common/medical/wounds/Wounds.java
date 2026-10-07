package com.duckerquacker.lastaid.common.medical.wounds;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.DamageContext;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.distribution.DamageDistribution;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalLog;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.medical.cold.Hypothermia;
import com.duckerquacker.lastaid.common.medical.infection.Anemia;
import com.duckerquacker.lastaid.common.medical.infection.Infection;
import com.duckerquacker.lastaid.common.medical.overdose.Overdose;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Bleeding wounds per {@link Zone}. Stages: 1 light (scratch), 2 medium (laceration), 3 heavy (deep wound),
 * 4 arterial. A stuck arrow plugs its wound until pulled out.
 * <ul>
 *    <li>Rag / plaster / bandage (First Aid's apply screen) stop light wounds; rag and bandage stop medium ones;
 *    heavy and arterial wounds only bleed less under them until sutured (sewing kit).</li>
 *    <li>A tourniquet stops any limb bleeding, but left on too long kills the limb.</li>
 *    <li>Dressings get dirty (faster on worse wounds). A dirty dressing left on a wound for a day starts an infection.
 *    Taken off, a rag / bandage comes back dirty and can be washed in water.</li>
 * </ul>
 */
public final class Wounds implements MedicalCondition<Wounds.State> {
   public static final Wounds INSTANCE = new Wounds();
   public static final ResourceLocation ID = EffectUtil.id("bleeding");
   public static final String SNAPSHOT_PREFIX = "wound_";
   public static final ResourceKey<DamageType> BLEEDING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, EffectUtil.id("bleeding"));
   public static final ResourceLocation ICON_BLOOD = EffectUtil.id("textures/gui/conditions/blood_drop.png");
   public static final ResourceLocation ICON_DRESSING = EffectUtil.id("textures/gui/conditions/dressing.png");
   public static final ResourceLocation ICON_DRESSING_DIRTY = EffectUtil.id("textures/gui/conditions/dressing_dirty.png");
   public static final ResourceLocation ICON_ARROW = EffectUtil.id("textures/gui/conditions/arrow.png");
   public static final int FLAG_BLINK = 16;

   public static final int LIGHT = 1;
   public static final int MEDIUM = 2;
   public static final int HEAVY = 3;
   public static final int ARTERIAL = 4;

   public static final int DRESSING_NONE = 0;
   public static final int DRESSING_RAG = 1;
   public static final int DRESSING_PLASTER = 2;
   public static final int DRESSING_BANDAGE = 3;

   // panel actions (bits in ConditionSnapshot.clientFlags >> 4, and MedicalActionPacket actions)
   public static final int ACT_EXTRACT = 1;
   public static final int ACT_TOURNIQUET = 2;
   public static final int ACT_REMOVE_TOURNIQUET = 4;
   public static final int ACT_SUTURE = 8;
   public static final int ACT_DISINFECT = 16;
   public static final int ACT_REMOVE_DRESSING = 32;
   public static final int[] ACTIONS = {ACT_EXTRACT, ACT_TOURNIQUET, ACT_REMOVE_TOURNIQUET, ACT_SUTURE, ACT_DISINFECT, ACT_REMOVE_DRESSING};

   private static final double HOUR = 1000.0;
   private static final double DAY = 24000.0;

   private Wounds() {
   }

   static MedicalConfig.Bleeding cfg() {
      return MedicalConfig.SERVER.bleeding;
   }

   static MedicalConfig.Wounds woundCfg() {
      return MedicalConfig.SERVER.wounds;
   }

   public static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && MedicalConfig.SERVER.enabled.get() && cfg().enabled.get();
   }

   public State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   @Override
   public State createState() {
      return new State();
   }

   @Override
   public int getDefaultColor() {
      return 0xFFC01818;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return ICON_BLOOD;
   }

   @Override
   public boolean isActive(State state) {
      for (ZoneState zone : state.zones) {
         if (zone.present()) {
            return true;
         }
      }
      return state.extractPainUntil > 0L;
   }

   // ---------------------------------------------------------------- rates

   private static double stageRate(int severity) {
      return switch (severity) {
         case LIGHT -> cfg().hpPerSecondLight.get();
         case MEDIUM -> cfg().hpPerSecondMedium.get();
         case HEAVY -> cfg().hpPerSecondHeavy.get();
         case ARTERIAL -> cfg().hpPerSecondArterial.get();
         default -> 0.0;
      };
   }

   /** HP per second lost from this zone right now. */
   public static double rate(ZoneState zone) {
      if (zone.severity <= 0) {
         return 0.0;
      }
      if (zone.arrow) {
         return cfg().arrowHpPerSecond.get();
      }
      if (zone.stopped || zone.tourniquet) {
         return 0.0;
      }
      double rate = stageRate(zone.severity);
      if (zone.severity >= HEAVY && (zone.dressing == DRESSING_RAG || zone.dressing == DRESSING_BANDAGE)) {
         rate *= cfg().unsuturedDressingMultiplier.get();
      }
      return rate;
   }

   public static boolean bleeding(ZoneState zone) {
      return zone.severity > 0 && !zone.arrow && !zone.stopped && !zone.tourniquet;
   }

   public double totalRate(Player player) {
      double total = 0.0;
      for (ZoneState zone : this.state(player).zones) {
         total += rate(zone);
      }
      return total;
   }

   /** Highest bleeding stage of all zones (0 none), for blood drops on other clients. */
   public int bleedLevel(Player player) {
      int level = 0;
      for (ZoneState zone : this.state(player).zones) {
         if (bleeding(zone)) {
            int stage = zone.severity;
            if (stage >= HEAVY && zone.dressing != DRESSING_NONE) {
               stage--;
            }
            level = Math.max(level, stage);
         }
      }
      return level;
   }

   public static boolean isBleeding(Player player, EnumPlayerPart part) {
      return enabled() && bleeding(INSTANCE.state(player).get(Zone.of(part)));
   }

   /** A dressing can go on this part even at full health (wound or old dressing there). */
   public static boolean acceptsDressing(Player player, EnumPlayerPart part) {
      if (!enabled()) {
         return false;
      }
      ZoneState zone = INSTANCE.state(player).get(Zone.of(part));
      return zone.severity > 0 || zone.dressing != DRESSING_NONE;
   }

   // ---------------------------------------------------------------- tick

   @Override
   public void tick(ServerPlayer player, State state, MedicalContext context) {
      if (!enabled()) {
         return;
      }
      long now = player.level().getGameTime();
      if (state.extractPainUntil > 0L && now > state.extractPainUntil) {
         state.extractPainUntil = 0L;
         state.markDirty();
      }
      double total = 0.0;
      for (Zone z : Zone.VALUES) {
         ZoneState zone = state.get(z);
         if (!zone.present()) {
            continue;
         }
         if (z.limb != null && MedicalHooks.limbStatus(player, z.limb) == LimbVisualState.AMPUTATED) {
            zone.reset();
            state.markDirty();
            continue;
         }
         tickZone(player, state, z, zone, 20.0);
         total += rate(zone);
      }
      if (total > 0.0) {
         state.bloodAccumulator += total;
         if (state.bloodAccumulator >= 0.25) {
            float amount = state.bloodAccumulator;
            state.bloodAccumulator = 0.0F;
            loseBlood(player, amount);
         }
      }
      if (player.tickCount % 200 < 20) {
         state.markDirty(); // progress bars
      }
   }

   private static void tickZone(ServerPlayer player, State state, Zone z, ZoneState zone, double ticks) {
      MedicalConfig.Bleeding cfg = cfg();
      if (zone.severity > 0) {
         zone.age += (long)ticks;
         // stuck arrow: falls out by itself, tearing the wound
         if (zone.arrow) {
            zone.arrowTicks += (long)ticks;
            if (zone.arrowTicks >= woundCfg().arrowAutoEjectMinutes.get() * 1200L) {
               zone.arrow = false;
               zone.arrowTicks = 0L;
               zone.severity = z.limb != null ? ARTERIAL : HEAVY;
               zone.stopped = false;
               zone.sutured = false;
               zone.heal = 0.0;
               player.displayClientMessage(Component.translatable("lastaid.wound.arrow_fell", z.displayName()).withStyle(ChatFormatting.DARK_RED), false);
               MedicalLog.add(player, Component.translatable("lastaid.log.arrow_fell", z.displayName()));
               state.markDirty();
            }
         } else if (!zone.stopped && zone.severity == LIGHT && !zone.tourniquet) {
            zone.lightTicks += (int)ticks;
            if (zone.lightTicks >= cfg.lightStopsAfterSeconds.get() * 20) {
               zone.stopped = true;
               state.markDirty();
            }
         }
         // healing once the bleeding is stopped (a tourniquet only holds it)
         if (zone.stopped && !zone.arrow) {
            zone.heal += ticks;
            if (zone.heal >= healTicks(zone.severity)) {
               player.displayClientMessage(Component.translatable("lastaid.wound.healed", z.displayName()).withStyle(ChatFormatting.GREEN), true);
               MedicalLog.add(player, Component.translatable("lastaid.log.wound_healed", z.displayName()));
               zone.clearWound();
               state.markDirty();
            }
         }
         // bite: not disinfected in time
         if (zone.contaminated) {
            zone.contaminatedTicks += (long)ticks;
            if (zone.contaminatedTicks >= MedicalConfig.SERVER.infection.contaminationHours.get() * HOUR) {
               zone.contaminated = false;
               Infection.INSTANCE.start(player, z, "lastaid.infection.source.bite");
               state.markDirty();
            }
         }
      }
      // tourniquet: real time, the limb dies if it stays on too long
      if (zone.tourniquet) {
         zone.tourniquetTicks += (long)ticks;
         long warn = cfg.tourniquetWarnMinutes.get() * 1200L;
         long dead = cfg.tourniquetNecrosisMinutes.get() * 1200L;
         if (zone.tourniquetTicks >= warn && zone.tourniquetTicks - (long)ticks < warn) {
            player.displayClientMessage(Component.translatable("lastaid.tourniquet.warn", z.displayName()).withStyle(ChatFormatting.GOLD), false);
         }
         if (zone.tourniquetTicks >= dead && z.limb != null) {
            zone.tourniquet = false;
            zone.tourniquetTicks = 0L;
            giveBack(player, new ItemStack(MedicalRegistry.TOURNIQUET.get()));
            if (Frostbite.INSTANCE.state(player).get(z.limb).stage < 4) {
               Frostbite.INSTANCE.setStage(player, z.limb, 4);
            }
            // the dead limb no longer bleeds
            zone.stopped = true;
            player.sendSystemMessage(Component.translatable("lastaid.tourniquet.necrosis", z.displayName()).withStyle(ChatFormatting.DARK_RED));
            MedicalLog.add(player, Component.translatable("lastaid.log.tourniquet_necrosis", z.displayName()));
            state.markDirty();
         }
      }
      // dressing gets dirty
      if (zone.dressing != DRESSING_NONE) {
         double hours = switch (zone.severity) {
            case LIGHT -> cfg.dirtyHoursLight.get();
            case MEDIUM -> cfg.dirtyHoursMedium.get();
            case HEAVY -> zone.stopped ? cfg.dirtyHoursMedium.get() : cfg.dirtyHoursHeavy.get();
            case ARTERIAL -> zone.stopped ? cfg.dirtyHoursMedium.get() : cfg.dirtyHoursArterial.get();
            default -> cfg.dirtyHoursNoWound.get();
         };
         boolean wasDirty = zone.dirt >= 1.0F;
         zone.dirt = (float)Math.min(1.0, zone.dirt + ticks / (hours * HOUR));
         if (!wasDirty && zone.dirt >= 1.0F) {
            player.displayClientMessage(Component.translatable("lastaid.dressing.dirty", z.displayName()).withStyle(ChatFormatting.GOLD), true);
            MedicalLog.add(player, Component.translatable("lastaid.log.dressing_dirty", z.displayName()));
            state.markDirty();
         }
         if (zone.dirt >= 1.0F && zone.severity > 0) {
            zone.dirtyTicks += (long)ticks;
            if (!zone.dirtyRolled && zone.dirtyTicks >= MedicalConfig.SERVER.infection.dirtyDressingInfectionDays.get() * DAY) {
               zone.dirtyRolled = true;
               if (infectionRoll(player, zone, 1.0)) {
                  zone.infectious = true;
                  Infection.INSTANCE.start(player, z, "lastaid.infection.source.dressing");
               }
               state.markDirty();
            }
         }
      }
   }

   private static double healTicks(int severity) {
      return DAY * switch (severity) {
         case LIGHT -> cfg().healDaysLight.get();
         case MEDIUM -> cfg().healDaysMedium.get();
         case HEAVY -> cfg().healDaysHeavy.get();
         default -> cfg().healDaysArterial.get();
      };
   }

   /** Rolls an infection chance; a disinfected wound mostly resists. */
   private static boolean infectionRoll(ServerPlayer player, ZoneState zone, double chance) {
      if (!MedicalConfig.SERVER.infection.enabled.get()) {
         return false;
      }
      if (zone.disinfected) {
         chance *= 1.0 - MedicalConfig.SERVER.infection.alcoholPreventionChance.get();
      }
      return player.getRandom().nextDouble() < chance;
   }

   private static void loseBlood(ServerPlayer player, float amount) {
      AbstractPlayerDamageModel model = CommonUtils.getDamageModel(player);
      if (model == null || !player.isAlive()) {
         return;
      }
      DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLEEDING_DAMAGE));
      DamageDistribution.handleDamageTaken(Overdose.SPREAD, model, amount, player, source, false, true);
      CommonUtils.syncDamageModel(player);
      Anemia.INSTANCE.recordBloodLoss(player, amount);
   }

   // ---------------------------------------------------------------- new wounds

   @Override
   public void onDamage(ServerPlayer player, State state, DamageContext damage, MedicalContext context) {
      if (!enabled()) {
         return;
      }
      DamageSource source = damage.source();
      if (source.is(BLEEDING_DAMAGE) || source.is(Infection.INFECTION_DAMAGE) || source.is(Overdose.OVERDOSE_DAMAGE)
         || source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypes.STALAGMITE) && !source.is(DamageTypes.FALLING_STALACTITE)) {
         return;
      }
      // the zone that took the most damage
      Zone hit = null;
      float best = 0.0F;
      float total = 0.0F;
      for (Zone z : Zone.VALUES) {
         float dmg = damage.damageTo(z.part) + (z.extra != null ? damage.damageTo(z.extra) : 0.0F);
         total += dmg;
         if (dmg > best) {
            best = dmg;
            hit = z;
         }
      }
      if (hit == null || best <= 0.0F) {
         return;
      }
      if (hit.limb != null && MedicalHooks.limbStatus(player, hit.limb) == LimbVisualState.AMPUTATED) {
         return;
      }
      Entity direct = source.getDirectEntity();
      Entity attacker = source.getEntity();
      MedicalConfig.Wounds cfg = woundCfg();
      int severity = 0;
      boolean arrow = false;
      boolean bite = false;
      double roll = player.getRandom().nextDouble();
      if (direct instanceof AbstractArrow && !(direct instanceof ThrownTrident)) {
         arrow = true;
         severity = HEAVY;
      } else if (direct instanceof ThrownTrident) {
         severity = HEAVY;
      } else if (source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_STALACTITE)) {
         severity = hit.limb != null ? ARTERIAL : HEAVY;
      } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
         severity = total >= cfg.explosionMinDamage.get() ? (hit.limb != null ? ARTERIAL : HEAVY) : MEDIUM;
      } else if (attacker instanceof IronGolem || attacker instanceof Ravager) {
         severity = hit.limb != null ? ARTERIAL : HEAVY;
      } else if (attacker instanceof LivingEntity le && le.getMainHandItem().getItem() instanceof MaceItem) {
         severity = HEAVY;
      } else if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.THORNS)) {
         severity = LIGHT;
      } else if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
         bite = attacker instanceof Zombie || attacker instanceof Wolf || attacker instanceof Spider;
         if (isSharp(attacker)) {
            severity = roll < cfg.sharpLacerationChance.get() ? MEDIUM : LIGHT;
         } else if (roll < cfg.bluntScratchChance.get()) {
            severity = LIGHT;
         }
      }
      if (severity <= 0) {
         return;
      }
      addWound(player, hit, severity, arrow);
      ZoneState zone = state.get(hit);
      if (bite && player.getRandom().nextDouble() < cfg.biteInfectionChance.get()) {
         zone.contaminated = true;
         zone.contaminatedTicks = 0L;
      }
      state.markDirty();
   }

   private static boolean isSharp(@Nullable Entity attacker) {
      if (attacker instanceof LivingEntity living) {
         Item item = living.getMainHandItem().getItem();
         if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem) {
            return true;
         }
      }
      return attacker instanceof Wolf || attacker instanceof Spider || attacker instanceof Zombie || attacker instanceof Cat
         || attacker instanceof Ocelot || attacker instanceof Fox || attacker instanceof PolarBear || attacker instanceof Panda
         || attacker instanceof Hoglin || attacker instanceof Zoglin;
   }

   /** Opens (or worsens) a wound in this zone. */
   public void addWound(ServerPlayer player, Zone z, int severity, boolean arrow) {
      if (!enabled()) {
         return;
      }
      State state = this.state(player);
      ZoneState zone = state.get(z);
      boolean worse = severity > zone.severity || arrow && !zone.arrow;
      if (severity > zone.severity) {
         zone.severity = severity;
         zone.sutured = false;
         zone.heal = 0.0;
         zone.lightTicks = 0;
      }
      if (arrow) {
         zone.arrow = true;
         zone.arrowTicks = 0L;
      }
      if (worse || !zone.stopped) {
         zone.stopped = false;
         zone.heal = 0.0;
         // a fresh wound under an old dressing: the dressing stops what it can stop
         applyStopRules(zone);
      }
      if (worse) {
         zone.disinfected = false;
         zone.dirtyRolled = false;
         String key = arrow ? "lastaid.wound.new_arrow" : "lastaid.wound.new." + zone.severity;
         player.displayClientMessage(Component.translatable(key, z.displayName()).withStyle(zone.severity >= HEAVY ? ChatFormatting.DARK_RED : ChatFormatting.RED), true);
         MedicalLog.add(player, Component.translatable(key, z.displayName()));
      }
      state.markDirty();
   }

   /** What the current dressing does to the current wound. */
   private static boolean applyStopRules(ZoneState zone) {
      if (zone.arrow || zone.severity <= 0) {
         return false;
      }
      boolean stops = switch (zone.severity) {
         case LIGHT -> zone.dressing != DRESSING_NONE;
         case MEDIUM -> zone.dressing == DRESSING_RAG || zone.dressing == DRESSING_BANDAGE;
         default -> false;
      };
      if (stops) {
         zone.stopped = true;
      }
      return stops;
   }

   // ---------------------------------------------------------------- dressings (First Aid apply screen)

   public static int dressingFor(ItemStack stack) {
      if (stack.is(RegistryObjects.BANDAGE.get())) {
         return DRESSING_BANDAGE;
      } else if (stack.is(RegistryObjects.PLASTER.get())) {
         return DRESSING_PLASTER;
      } else if (stack.is(MedicalRegistry.RAG.get())) {
         return DRESSING_RAG;
      }
      return DRESSING_NONE;
   }

   /** A rag, plaster or bandage was just put on this part through the First Aid screen. */
   public static void onDressingApplied(ServerPlayer player, EnumPlayerPart part, ItemStack stack) {
      int type = dressingFor(stack);
      if (!enabled() || type == DRESSING_NONE) {
         return;
      }
      Zone z = Zone.of(part);
      State state = INSTANCE.state(player);
      ZoneState zone = state.get(z);
      if (zone.dressing != DRESSING_NONE) {
         giveBack(player, removedDressing(zone));
      }
      zone.dressing = type;
      zone.dirt = 0.0F;
      zone.dirtyTicks = 0L;
      zone.dirtyRolled = false;
      zone.infectious = false;
      boolean wasBleeding = bleeding(zone);
      applyStopRules(zone);
      String key;
      if (wasBleeding && !bleeding(zone)) {
         key = "lastaid.dressing.stopped";
      } else if (wasBleeding && type == DRESSING_PLASTER) {
         key = "lastaid.dressing.plaster_weak";
      } else if (wasBleeding) {
         key = "lastaid.dressing.not_enough";
      } else {
         key = "lastaid.dressing.applied";
      }
      player.displayClientMessage(Component.translatable(key, z.displayName()).withStyle(bleeding(zone) ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
      MedicalLog.add(player, Component.translatable("lastaid.log.dressing", stack.getHoverName(), z.displayName()));
      state.markDirty();
   }

   /** The item a dressing turns back into when taken off (plasters are thrown away). */
   private static ItemStack removedDressing(ZoneState zone) {
      boolean dirty = zone.dirt >= 0.2F || zone.severity >= MEDIUM && zone.dirt > 0.02F;
      return switch (zone.dressing) {
         case DRESSING_RAG -> new ItemStack(zone.infectious ? MedicalRegistry.INFECTED_RAG.get() : dirty ? MedicalRegistry.DIRTY_RAG.get() : MedicalRegistry.RAG.get());
         case DRESSING_BANDAGE -> new ItemStack(dirty ? MedicalRegistry.DIRTY_BANDAGE.get() : RegistryObjects.BANDAGE.get());
         default -> ItemStack.EMPTY;
      };
   }

   private static void giveBack(ServerPlayer player, ItemStack stack) {
      if (!stack.isEmpty() && !player.getInventory().add(stack)) {
         player.drop(stack, false);
      }
   }

   // ---------------------------------------------------------------- panel actions

   /** Which panel buttons make sense for this zone right now. */
   public static int actions(Zone z, ZoneState zone) {
      int mask = 0;
      if (zone.arrow) {
         mask |= ACT_EXTRACT;
      }
      if (z.limb != null) {
         if (zone.tourniquet) {
            mask |= ACT_REMOVE_TOURNIQUET;
         } else if (bleeding(zone) && zone.severity >= HEAVY) {
            mask |= ACT_TOURNIQUET;
         }
      }
      if (!zone.sutured && !zone.arrow && (zone.severity >= HEAVY || zone.severity == MEDIUM && bleeding(zone))) {
         mask |= ACT_SUTURE;
      }
      if (zone.severity > 0 && !zone.disinfected && !zone.arrow) {
         mask |= ACT_DISINFECT;
      }
      if (zone.dressing != DRESSING_NONE) {
         mask |= ACT_REMOVE_DRESSING;
      }
      return mask;
   }

   public static void requestAction(ServerPlayer player, Zone z, int action) {
      if (!enabled()) {
         return;
      }
      State state = INSTANCE.state(player);
      ZoneState zone = state.get(z);
      if ((actions(z, zone) & action) == 0) {
         return;
      }
      if (com.duckerquacker.lastaid.common.medical.MedicalProcedures.isBusy(player)) {
         player.displayClientMessage(Component.translatable("lastaid.wound.busy").withStyle(ChatFormatting.GRAY), true);
         return;
      }
      switch (action) {
         case ACT_EXTRACT -> com.duckerquacker.lastaid.common.medical.MedicalProcedures.startWoundProcedure(player, z, true);
         case ACT_SUTURE -> {
            if (findItem(player, MedicalRegistry.SEWING_KIT.get()).isEmpty()) {
               refuse(player, "lastaid.wound.need_sewing_kit");
            } else {
               com.duckerquacker.lastaid.common.medical.MedicalProcedures.startWoundProcedure(player, z, false);
            }
         }
         case ACT_TOURNIQUET -> {
            ItemStack stack = findItem(player, MedicalRegistry.TOURNIQUET.get());
            if (stack.isEmpty()) {
               refuse(player, "lastaid.wound.need_tourniquet");
               return;
            }
            if (!player.getAbilities().instabuild) {
               stack.shrink(1);
            }
            zone.tourniquet = true;
            zone.tourniquetTicks = 0L;
            sound(player, SoundEvents.ARMOR_EQUIP_LEATHER.value());
            player.displayClientMessage(Component.translatable("lastaid.tourniquet.on", z.displayName()).withStyle(ChatFormatting.AQUA), true);
            MedicalLog.add(player, Component.translatable("lastaid.log.tourniquet_on", z.displayName()));
         }
         case ACT_REMOVE_TOURNIQUET -> {
            zone.tourniquet = false;
            zone.tourniquetTicks = 0L;
            giveBack(player, new ItemStack(MedicalRegistry.TOURNIQUET.get()));
            player.displayClientMessage(Component.translatable(bleeding(zone) ? "lastaid.tourniquet.off_bleeding" : "lastaid.tourniquet.off", z.displayName())
               .withStyle(bleeding(zone) ? ChatFormatting.RED : ChatFormatting.GREEN), true);
            MedicalLog.add(player, Component.translatable("lastaid.log.tourniquet_off", z.displayName()));
         }
         case ACT_DISINFECT -> {
            ItemStack stack = findItem(player, MedicalRegistry.ALCOHOL.get());
            if (stack.isEmpty()) {
               refuse(player, "lastaid.wound.need_alcohol");
               return;
            }
            useDurability(player, stack);
            zone.disinfected = true;
            if (zone.contaminated && player.getRandom().nextDouble() < MedicalConfig.SERVER.infection.alcoholPreventionChance.get()) {
               zone.contaminated = false;
            }
            sound(player, SoundEvents.BOTTLE_EMPTY);
            player.displayClientMessage(Component.translatable("lastaid.wound.disinfected", z.displayName()).withStyle(ChatFormatting.AQUA), true);
            MedicalLog.add(player, Component.translatable("lastaid.log.disinfected", z.displayName()));
            com.duckerquacker.lastaid.common.medical.bone.Bones.onWoundDisinfected(player, z);
         }
         case ACT_REMOVE_DRESSING -> {
            giveBack(player, removedDressing(zone));
            zone.dressing = DRESSING_NONE;
            zone.dirt = 0.0F;
            zone.dirtyTicks = 0L;
            zone.dirtyRolled = false;
            zone.infectious = false;
            // an unsutured wound that is not closed yet opens again
            if (zone.severity >= MEDIUM && !zone.sutured && zone.stopped && zone.heal < healTicks(zone.severity) * 0.5) {
               zone.stopped = false;
            }
            player.displayClientMessage(Component.translatable(bleeding(zone) ? "lastaid.dressing.removed_bleeding" : "lastaid.dressing.removed", z.displayName())
               .withStyle(bleeding(zone) ? ChatFormatting.RED : ChatFormatting.GRAY), true);
            MedicalLog.add(player, Component.translatable("lastaid.log.dressing_removed", z.displayName()));
         }
         default -> {
         }
      }
      state.markDirty();
   }

   /** Procedure finished: pull the arrow out (extract) or stitch the wound (suture). */
   public static void finishProcedure(ServerPlayer player, Zone z, boolean extract) {
      State state = INSTANCE.state(player);
      ZoneState zone = state.get(z);
      if (extract) {
         if (!zone.arrow) {
            return;
         }
         zone.arrow = false;
         zone.arrowTicks = 0L;
         zone.severity = Math.max(zone.severity, HEAVY);
         zone.stopped = false;
         zone.sutured = false;
         zone.heal = 0.0;
         giveBack(player, new ItemStack(Items.ARROW));
         if (!player.hasEffect(RegistryObjects.PAINKILLER_EFFECT) && !player.hasEffect(RegistryObjects.MORPHINE_EFFECT)) {
            state.extractPainUntil = player.level().getGameTime() + woundCfg().extractPainSeconds.get() * 20L;
         }
         player.level().playSound(null, player.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6F, 0.7F);
         player.displayClientMessage(Component.translatable("lastaid.wound.extracted", z.displayName()).withStyle(ChatFormatting.GOLD), true);
         MedicalLog.add(player, Component.translatable("lastaid.log.extracted", z.displayName()));
      } else {
         ItemStack kit = findItem(player, MedicalRegistry.SEWING_KIT.get());
         if (kit.isEmpty() || zone.severity < MEDIUM || zone.arrow) {
            refuse(player, "lastaid.wound.need_sewing_kit");
            return;
         }
         useDurability(player, kit);
         zone.sutured = true;
         zone.stopped = true;
         zone.heal = Math.max(zone.heal, 0.0);
         player.displayClientMessage(Component.translatable("lastaid.wound.sutured", z.displayName()).withStyle(ChatFormatting.GREEN), true);
         MedicalLog.add(player, Component.translatable("lastaid.log.sutured", z.displayName()));
      }
      state.markDirty();
   }

   private static void refuse(ServerPlayer player, String key) {
      player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
   }

   private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent event) {
      player.level().playSound(null, player.blockPosition(), event, SoundSource.PLAYERS, 0.6F, 1.1F);
   }

   private static void useDurability(ServerPlayer player, ItemStack stack) {
      if (player.getAbilities().instabuild) {
         return;
      }
      if (stack.isDamageableItem()) {
         stack.hurtAndBreak(1, player.serverLevel(), player, item -> {
         });
      } else {
         stack.shrink(1);
      }
   }

   public static ItemStack findItem(Player player, Item item) {
      for (ItemStack stack : player.getInventory().items) {
         if (stack.is(item)) {
            return stack;
         }
      }
      for (ItemStack stack : player.getInventory().offhand) {
         if (stack.is(item)) {
            return stack;
         }
      }
      return ItemStack.EMPTY;
   }

   // ---------------------------------------------------------------- pain, sleep

   /** Pain floor: pulling an arrow out, arterial / heavy bleeding. */
   public static int painFloor(Player player) {
      if (!enabled() || !(player instanceof ServerPlayer serverPlayer)) {
         return 0;
      }
      State state = INSTANCE.state(serverPlayer);
      int pain = 0;
      if (state.extractPainUntil > 0L && serverPlayer.level().getGameTime() <= state.extractPainUntil) {
         pain = woundCfg().extractPain.get();
      }
      for (ZoneState zone : state.zones) {
         if (zone.arrow) {
            pain = Math.max(pain, 2);
         } else if (bleeding(zone) && zone.severity >= HEAVY) {
            pain = Math.max(pain, zone.severity - 1);
         }
      }
      return pain;
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
         if (!player.isSleepingLongEnough()) {
            continue;
         }
         State state = INSTANCE.state(player);
         boolean cold = Hypothermia.INSTANCE.state(player).stage > 0;
         for (Zone z : Zone.VALUES) {
            ZoneState zone = state.get(z);
            if (!zone.present()) {
               continue;
            }
            if (zone.severity > 0 && zone.dressing == DRESSING_NONE && !zone.sutured) {
               double chance = MedicalConfig.SERVER.infection.openWoundSleepChance.get() * (cold ? MedicalConfig.SERVER.infection.coldSleepMultiplier.get() : 1.0);
               if (infectionRoll(player, zone, chance)) {
                  Infection.INSTANCE.start(player, z, "lastaid.infection.source.sleep");
               }
            }
            // the night counts for healing, dirt and the dirty-dressing clock (not for the tourniquet: real time)
            boolean tourniquet = zone.tourniquet;
            zone.tourniquet = false;
            tickZone(player, state, z, zone, skipped);
            zone.tourniquet = tourniquet;
         }
         state.markDirty();
      }
   }

   @Override
   public void clear(ServerPlayer player, State state) {
      for (ZoneState zone : state.zones) {
         zone.reset();
      }
      state.extractPainUntil = 0L;
      state.bloodAccumulator = 0.0F;
      state.markDirty();
   }

   // ---------------------------------------------------------------- panel

   @Override
   public void collectSnapshots(ServerPlayer player, State state, MedicalContext context, List<ConditionSnapshot> list) {
      if (!enabled()) {
         return;
      }
      double total = this.totalRate(player);
      AbstractPlayerDamageModel model = CommonUtils.getDamageModel(player);
      if (model != null) {
         int max = 0;
         for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            max += model.getFromEnum(part).getMaxHealth();
         }
         displayScale = max > 0 ? player.getMaxHealth() / max : displayScale;
      }
      for (Zone z : Zone.VALUES) {
         ZoneState zone = state.get(z);
         if (zone.present()) {
            list.add(snapshot(z, zone, total));
         }
      }
   }

   /** Shown in vanilla health points (First Aid has ~34 part HP for 20 vanilla HP). */
   private static double displayScale = 20.0 / 34.0;

   private static String perMinute(double hpPerSecond) {
      return String.format(Locale.ROOT, "%.1f", hpPerSecond * 60.0 * displayScale);
   }

   private static ConditionSnapshot snapshot(Zone z, ZoneState zone, double total) {
      Component zoneName = z.displayName();
      boolean isBleeding = bleeding(zone);
      Component name;
      if (zone.severity <= 0) {
         name = Component.translatable("lastaid.condition.dressing", zoneName);
      } else if (zone.arrow) {
         name = Component.translatable("lastaid.condition.arrow", zoneName);
      } else if (isBleeding) {
         name = Component.translatable("lastaid.condition.bleeding", zone.severity, zoneName);
      } else {
         name = Component.translatable("lastaid.condition.wound", zoneName);
      }
      List<Component> tooltip = new ArrayList<>();
      MutableComponent line = zone.severity > 0
         ? Component.translatable("lastaid.wound.type." + zone.severity).copy()
         : Component.translatable("lastaid.wound.no_wound").copy();
      if (zone.arrow) {
         line.append(", ").append(Component.translatable("lastaid.wound.arrow_stuck").withStyle(ChatFormatting.RED));
      }
      if (isBleeding || zone.arrow) {
         line.append(", ").append(Component.translatable("lastaid.wound.rate", perMinute(rate(zone))).withStyle(ChatFormatting.RED));
      } else if (zone.severity > 0) {
         line.append(", ").append(Component.translatable("lastaid.wound.stopped").withStyle(ChatFormatting.GREEN));
      }
      tooltip.add(line);
      if (zone.tourniquet) {
         tooltip.add(Component.translatable("lastaid.wound.tourniquet_on", zone.tourniquetTicks / 1200L).withStyle(ChatFormatting.GOLD));
      }
      if (zone.sutured) {
         tooltip.add(Component.translatable("lastaid.wound.sutured_tag").withStyle(ChatFormatting.GREEN));
      }
      if (zone.dressing != DRESSING_NONE) {
         tooltip.add(Component.translatable("lastaid.dressing.line", Component.translatable("lastaid.dressing.type." + zone.dressing),
               Math.round(zone.dirt * 100.0F))
            .withStyle(zone.dirt >= 1.0F ? ChatFormatting.GOLD : ChatFormatting.GRAY));
      }
      if (zone.disinfected) {
         tooltip.add(Component.translatable("lastaid.wound.disinfected_tag").withStyle(ChatFormatting.AQUA));
      }
      if (zone.contaminated) {
         tooltip.add(Component.translatable("lastaid.wound.contaminated").withStyle(ChatFormatting.DARK_GREEN));
      }
      if (total > 0.0) {
         tooltip.add(Component.translatable("lastaid.wound.total", perMinute(total)).withStyle(ChatFormatting.DARK_RED));
      }
      String hint = hint(z, zone);
      if (hint != null) {
         tooltip.add(Component.translatable(hint).withStyle(ChatFormatting.YELLOW));
      }
      List<Component> detail = new ArrayList<>();
      detail.add(Component.translatable(zone.severity > 0 ? "lastaid.wound.detail." + zone.severity : "lastaid.wound.detail.dressing"));
      detail.add(Component.empty());
      detail.addAll(tooltip);
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));
      for (String key : helps(z, zone)) {
         detail.add(Component.literal(" • ").append(Component.translatable(key)));
      }
      float progress = zone.severity > 0 && zone.stopped && !zone.arrow ? (float)Mth.clamp(zone.heal / healTicks(zone.severity), 0.0, 1.0) : -1.0F;
      ResourceLocation icon = zone.arrow ? ICON_ARROW
         : isBleeding || zone.dressing == DRESSING_NONE ? ICON_BLOOD
         : zone.dirt >= 1.0F ? ICON_DRESSING_DIRTY : ICON_DRESSING;
      int flags = (isBleeding && zone.severity >= HEAVY) || zone.dirt >= 1.0F || zone.tourniquet ? FLAG_BLINK : 0;
      int tint = isBleeding ? (zone.severity >= HEAVY ? 0x70C01010 : 0x40C01010) : 0;
      return new ConditionSnapshot(
         EffectUtil.id(SNAPSHOT_PREFIX + z.key()),
         name,
         zone.severity > 0 ? 0xFFC01818 : 0xFFD8D0C0,
         zone.severity,
         z.mask(),
         progress,
         isBleeding && zone.severity >= HEAVY || zone.arrow,
         tooltip,
         detail,
         z.ordinal() | actions(z, zone) << 4,
         icon,
         tint,
         flags
      );
   }

   @Nullable
   private static String hint(Zone z, ZoneState zone) {
      if (zone.arrow) {
         return "lastaid.wound.hint.arrow";
      }
      if (bleeding(zone)) {
         return switch (zone.severity) {
            case LIGHT -> "lastaid.wound.hint.1";
            case MEDIUM -> "lastaid.wound.hint.2";
            case HEAVY -> "lastaid.wound.hint.3";
            default -> z.limb != null ? "lastaid.wound.hint.4" : "lastaid.wound.hint.4_torso";
         };
      }
      if (zone.tourniquet && zone.sutured) {
         return "lastaid.wound.hint.remove_tourniquet";
      }
      if (zone.dirt >= 1.0F) {
         return "lastaid.dressing.hint.dirty";
      }
      if (zone.severity <= 0 && zone.dressing != DRESSING_NONE) {
         return "lastaid.dressing.hint.healed";
      }
      return null;
   }

   private static List<String> helps(Zone z, ZoneState zone) {
      List<String> list = new ArrayList<>();
      if (zone.arrow) {
         list.add("lastaid.wound.help.extract");
      }
      if (zone.severity == LIGHT) {
         list.add("lastaid.wound.help.light");
      } else if (zone.severity == MEDIUM) {
         list.add("lastaid.wound.help.medium");
      } else if (zone.severity >= HEAVY && !zone.sutured) {
         list.add("lastaid.wound.help.suture");
         if (zone.severity == ARTERIAL && z.limb != null) {
            list.add("lastaid.wound.help.tourniquet");
         }
      }
      list.add("lastaid.wound.help.alcohol");
      list.add("lastaid.wound.help.dirty");
      return list;
   }

   @Override
   public ConditionSnapshot snapshot(ServerPlayer player, State state, MedicalContext context) {
      List<ConditionSnapshot> list = new ArrayList<>();
      this.collectSnapshots(player, state, context, list);
      return list.isEmpty() ? null : list.get(0);
   }

   // ---------------------------------------------------------------- state

   public static final class ZoneState {
      public int severity;
      public boolean arrow;
      public long arrowTicks;
      public boolean sutured;
      public boolean stopped;
      public double heal;
      public int lightTicks;
      public boolean disinfected;
      public boolean contaminated;
      public long contaminatedTicks;
      public boolean tourniquet;
      public long tourniquetTicks;
      public long age;
      public int dressing;
      public float dirt;
      public long dirtyTicks;
      public boolean dirtyRolled;
      public boolean infectious;

      public boolean present() {
         return this.severity > 0 || this.dressing != DRESSING_NONE || this.tourniquet;
      }

      void clearWound() {
         this.severity = 0;
         this.arrow = false;
         this.arrowTicks = 0L;
         this.sutured = false;
         this.stopped = false;
         this.heal = 0.0;
         this.lightTicks = 0;
         this.disinfected = false;
         this.contaminated = false;
         this.contaminatedTicks = 0L;
         this.age = 0L;
      }

      void reset() {
         this.clearWound();
         this.tourniquet = false;
         this.tourniquetTicks = 0L;
         this.dressing = DRESSING_NONE;
         this.dirt = 0.0F;
         this.dirtyTicks = 0L;
         this.dirtyRolled = false;
         this.infectious = false;
      }

      CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putInt("severity", this.severity);
         tag.putBoolean("arrow", this.arrow);
         tag.putLong("arrow_ticks", this.arrowTicks);
         tag.putBoolean("sutured", this.sutured);
         tag.putBoolean("stopped", this.stopped);
         tag.putDouble("heal", this.heal);
         tag.putInt("light_ticks", this.lightTicks);
         tag.putBoolean("disinfected", this.disinfected);
         tag.putBoolean("contaminated", this.contaminated);
         tag.putLong("contaminated_ticks", this.contaminatedTicks);
         tag.putBoolean("tourniquet", this.tourniquet);
         tag.putLong("tourniquet_ticks", this.tourniquetTicks);
         tag.putLong("age", this.age);
         tag.putInt("dressing", this.dressing);
         tag.putFloat("dirt", this.dirt);
         tag.putLong("dirty_ticks", this.dirtyTicks);
         tag.putBoolean("dirty_rolled", this.dirtyRolled);
         tag.putBoolean("infectious", this.infectious);
         return tag;
      }

      void load(CompoundTag tag) {
         this.severity = Mth.clamp(tag.getInt("severity"), 0, 4);
         this.arrow = tag.getBoolean("arrow");
         this.arrowTicks = tag.getLong("arrow_ticks");
         this.sutured = tag.getBoolean("sutured");
         this.stopped = tag.getBoolean("stopped");
         this.heal = tag.getDouble("heal");
         this.lightTicks = tag.getInt("light_ticks");
         this.disinfected = tag.getBoolean("disinfected");
         this.contaminated = tag.getBoolean("contaminated");
         this.contaminatedTicks = tag.getLong("contaminated_ticks");
         this.tourniquet = tag.getBoolean("tourniquet");
         this.tourniquetTicks = tag.getLong("tourniquet_ticks");
         this.age = tag.getLong("age");
         this.dressing = Mth.clamp(tag.getInt("dressing"), 0, 3);
         this.dirt = tag.getFloat("dirt");
         this.dirtyTicks = tag.getLong("dirty_ticks");
         this.dirtyRolled = tag.getBoolean("dirty_rolled");
         this.infectious = tag.getBoolean("infectious");
      }
   }

   public static final class State extends ConditionState {
      public final ZoneState[] zones = new ZoneState[Zone.VALUES.length];
      public long extractPainUntil;
      float bloodAccumulator;

      State() {
         for (int i = 0; i < this.zones.length; i++) {
            this.zones[i] = new ZoneState();
         }
      }

      public ZoneState get(Zone zone) {
         return this.zones[zone.ordinal()];
      }

      @Override
      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         for (Zone zone : Zone.VALUES) {
            tag.put(zone.key(), this.get(zone).save());
         }
         return tag;
      }

      @Override
      public void load(CompoundTag tag) {
         for (Zone zone : Zone.VALUES) {
            this.get(zone).load(tag.getCompound(zone.key()));
         }
         this.markDirty();
      }
   }
}

