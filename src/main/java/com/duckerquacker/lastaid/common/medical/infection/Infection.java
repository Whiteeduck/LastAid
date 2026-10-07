package com.duckerquacker.lastaid.common.medical.infection;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.damagesystem.distribution.DamageDistribution;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalLog;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.medical.overdose.Overdose;
import com.duckerquacker.lastaid.common.medical.wounds.Wounds;
import com.duckerquacker.lastaid.common.medical.wounds.Zone;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Wound infection: 1 mild, 2 moderate (fever), 3 severe, 4 sepsis. Without treatment it gets one stage worse every
 * {@code stageHours}; at sepsis an infected limb dies (necrosis = gangrene, only amputation helps), and amputating the
 * infected limb takes the source away (back to stage 2 at most).
 * <p>
 * Antibiotic: one counts per game day, -1 stage and no worsening for that day. Stage 1 heals by itself when the source
 * wound is disinfected and under a clean dressing (or healed).
 */
public final class Infection implements MedicalCondition<Infection.State> {
   public static final Infection INSTANCE = new Infection();
   public static final ResourceLocation ID = EffectUtil.id("infection");
   public static final ResourceKey<DamageType> INFECTION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, EffectUtil.id("infection"));
   public static final ResourceLocation ICON = EffectUtil.id("textures/gui/conditions/bacteria.png");
   private static final double HOUR = 1000.0;
   private static final double DAY = 24000.0;

   private Infection() {
   }

   static MedicalConfig.Infection cfg() {
      return MedicalConfig.SERVER.infection;
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
      return 0xFF30C030;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return ICON;
   }

   @Override
   public boolean isActive(State state) {
      return state.stage > 0;
   }

   /** Starts an infection from this zone (no effect if already infected). */
   public void start(ServerPlayer player, @Nullable Zone zone, String reasonKey) {
      if (!enabled()) {
         return;
      }
      State state = this.state(player);
      if (state.stage > 0) {
         return;
      }
      state.stage = 1;
      state.source = zone;
      state.progress = 0.0;
      state.heal = 0.0;
      state.necrosis = 0.0;
      Component where = zone != null ? zone.displayName() : Component.translatable("lastaid.zone.body");
      player.sendSystemMessage(Component.translatable("lastaid.infection.started", where, Component.translatable(reasonKey)).withStyle(ChatFormatting.DARK_GREEN));
      MedicalLog.add(player, Component.translatable("lastaid.log.infection", where, Component.translatable(reasonKey)));
      if (MedicalConfig.SERVER.general.debugLogging.get()) {
         com.duckerquacker.lastaid.LastAid.LOGGER.info("[medical] infection started for {} at {} ({})", player.getScoreboardName(), zone, reasonKey);
      }
      state.markDirty();
   }

   public static float feverC(Player player) {
      if (!enabled()) {
         return 0.0F;
      }
      return switch (INSTANCE.state(player).stage) {
         case 2 -> cfg().feverStage2.get().floatValue();
         case 3 -> cfg().feverStage3.get().floatValue();
         case 4 -> cfg().feverStage4.get().floatValue();
         default -> 0.0F;
      };
   }

   private static boolean antibioticActive(ServerPlayer player, State state) {
      return state.lastAntibiotic >= 0L && player.level().getGameTime() - state.lastAntibiotic < cfg().antibioticCooldownDays.get() * DAY;
   }

   /** Antibiotic taken (item). Returns false if it was too soon after the last one. */
   public boolean takeAntibiotic(ServerPlayer player) {
      State state = this.state(player);
      if (antibioticActive(player, state) && cfg().antibioticCooldownDays.get() > 0.0) {
         player.displayClientMessage(Component.translatable("lastaid.antibiotic.too_soon").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
      state.lastAntibiotic = player.level().getGameTime();
      if (state.stage > 0) {
         state.stage = Math.max(0, state.stage - cfg().antibioticStages.get());
         state.progress = 0.0;
         state.necrosis = 0.0;
         if (state.stage == 0) {
            state.source = null;
            player.displayClientMessage(Component.translatable("lastaid.infection.cured").withStyle(ChatFormatting.GREEN), true);
         } else {
            player.displayClientMessage(Component.translatable("lastaid.antibiotic.better", state.stage).withStyle(ChatFormatting.GREEN), true);
         }
      } else {
         player.displayClientMessage(Component.translatable("lastaid.antibiotic.prevent").withStyle(ChatFormatting.GRAY), true);
      }
      MedicalLog.add(player, Component.translatable("lastaid.log.antibiotic", state.stage));
      state.markDirty();
      return true;
   }

   /** Stage 1 heals by itself when the source is clean: disinfected under a clean dressing, or healed. */
   private static boolean sourceClean(ServerPlayer player, State state) {
      if (state.source == null) {
         return true;
      }
      Wounds.ZoneState zone = Wounds.INSTANCE.state(player).get(state.source);
      if (zone.severity <= 0) {
         return zone.dressing == Wounds.DRESSING_NONE || zone.dirt < 1.0F;
      }
      return zone.disinfected && zone.dressing != Wounds.DRESSING_NONE && zone.dirt < 1.0F;
   }

   @Override
   public void tick(ServerPlayer player, State state, MedicalContext context) {
      if (!enabled() || state.stage <= 0) {
         return;
      }
      tickProgress(player, state, 20.0, player.isSleeping());
      if (state.stage <= 0) {
         return;
      }
      MedicalConfig.Infection cfg = cfg();
      state.counter++;
      switch (state.stage) {
         case 1 -> {
            if (state.counter % 60 == 0) {
               EffectUtil.pulseEffect(player, MobEffects.CONFUSION, 100, 0);
            }
         }
         case 2 -> {
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 0);
            if (state.counter % cfg.stage2IntervalSeconds.get() == 0) {
               hurt(player, cfg.stage2Damage.get().floatValue());
            }
         }
         case 3 -> {
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1);
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 1);
            EffectUtil.keepEffect(player, MobEffects.DIG_SLOWDOWN, 0);
            if (state.counter % cfg.stage3IntervalSeconds.get() == 0) {
               hurt(player, cfg.stage3Damage.get().floatValue());
            }
         }
         default -> {
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1);
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 1);
            EffectUtil.keepEffect(player, MobEffects.DIG_SLOWDOWN, 0);
            if (state.counter % 60 == 0) {
               EffectUtil.pulseEffect(player, MobEffects.BLINDNESS, 60, 0);
            }
            if (state.counter % cfg.stage4IntervalSeconds.get() == 0) {
               hurt(player, cfg.stage4Damage.get().floatValue());
            }
         }
      }
   }

   /** Advances the infection by this many ticks (normal play or a night of sleep). */
   private static void tickProgress(ServerPlayer player, State state, double ticks, boolean sleeping) {
      MedicalConfig.Infection cfg = cfg();
      // the infected limb is gone: no source any more
      if (state.source != null && state.source.limb != null && MedicalHooks.limbStatus(player, state.source.limb) == LimbVisualState.AMPUTATED) {
         state.source = null;
         if (state.stage > 2) {
            state.stage = 2;
            state.progress = 0.0;
         }
         player.displayClientMessage(Component.translatable("lastaid.infection.source_removed").withStyle(ChatFormatting.GREEN), false);
         state.markDirty();
      }
      double mult = sleeping ? cfg.sleepHealMultiplier.get() : 1.0;
      if (state.stage == 1 && sourceClean(player, state)) {
         state.heal += ticks * mult;
         if (state.heal >= cfg.stage1HealHours.get() * HOUR) {
            state.stage = 0;
            state.source = null;
            state.heal = 0.0;
            player.displayClientMessage(Component.translatable("lastaid.infection.cured").withStyle(ChatFormatting.GREEN), true);
            MedicalLog.add(player, Component.translatable("lastaid.log.infection_cured"));
            state.markDirty();
         }
         return;
      }
      if (antibioticActive(player, state)) {
         return;
      }
      state.progress += ticks;
      if (state.progress >= cfg.stageHours.get() * HOUR && state.stage < 4) {
         state.progress = 0.0;
         state.stage++;
         player.sendSystemMessage(Component.translatable("lastaid.infection.worse." + state.stage).withStyle(ChatFormatting.DARK_GREEN));
         MedicalLog.add(player, Component.translatable("lastaid.log.infection_worse", state.stage));
         state.markDirty();
      }
      // sepsis from a limb: the limb dies
      if (state.stage >= 4 && state.source != null && state.source.limb != null) {
         state.necrosis += ticks;
         Frostbite.Limb limb = state.source.limb;
         if (state.necrosis >= cfg.necrosisHours.get() * HOUR && Frostbite.INSTANCE.state(player).get(limb).stage < 4) {
            Frostbite.INSTANCE.setStage(player, limb, 4);
            player.sendSystemMessage(Component.translatable("lastaid.infection.necrosis", state.source.displayName()).withStyle(ChatFormatting.DARK_RED));
            MedicalLog.add(player, Component.translatable("lastaid.log.necrosis", state.source.displayName()));
            state.markDirty();
         }
      }
   }

   private static void hurt(ServerPlayer player, float amount) {
      AbstractPlayerDamageModel model = CommonUtils.getDamageModel(player);
      if (model == null || amount <= 0.0F || !player.isAlive()) {
         return;
      }
      DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(INFECTION_DAMAGE));
      DamageDistribution.handleDamageTaken(Overdose.SPREAD, model, amount, player, source, false, true);
      CommonUtils.syncDamageModel(player);
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
         State state = INSTANCE.state(player);
         if (player.isSleepingLongEnough() && state.stage > 0) {
            tickProgress(player, state, skipped, true);
            state.markDirty();
         }
      }
   }

   @Override
   public void clear(ServerPlayer player, State state) {
      state.stage = 0;
      state.source = null;
      state.progress = 0.0;
      state.heal = 0.0;
      state.necrosis = 0.0;
      state.markDirty();
   }

   /** Admin command: set the stage directly. */
   public void set(ServerPlayer player, int stage, @Nullable Zone zone) {
      State state = this.state(player);
      state.stage = Mth.clamp(stage, 0, 4);
      state.source = state.stage > 0 ? zone : null;
      state.progress = 0.0;
      state.heal = 0.0;
      state.necrosis = 0.0;
      state.markDirty();
   }

   @Override
   public ConditionSnapshot snapshot(ServerPlayer player, State state, MedicalContext context) {
      MedicalConfig.Infection cfg = cfg();
      Component where = state.source != null ? state.source.displayName() : Component.translatable("lastaid.infection.blood");
      boolean healing = state.stage == 1 && sourceClean(player, state);
      boolean antibiotic = antibioticActive(player, state);
      float progress;
      Component status;
      if (healing) {
         progress = (float)Mth.clamp(state.heal / (cfg.stage1HealHours.get() * HOUR), 0.0, 1.0);
         status = Component.translatable("lastaid.infection.healing", Math.round(progress * 100.0F)).withStyle(ChatFormatting.GREEN);
      } else if (antibiotic) {
         progress = -1.0F;
         status = Component.translatable("lastaid.infection.antibiotic_active").withStyle(ChatFormatting.AQUA);
      } else {
         progress = -1.0F;
         double hoursLeft = Math.max(0.0, cfg.stageHours.get() - state.progress / HOUR);
         status = state.stage < 4
            ? Component.translatable("lastaid.infection.worse_in", String.format(Locale.ROOT, "%.1f", hoursLeft)).withStyle(ChatFormatting.RED)
            : Component.translatable("lastaid.infection.sepsis_line").withStyle(ChatFormatting.DARK_RED);
      }
      List<Component> tooltip = new ArrayList<>();
      tooltip.add(Component.translatable("lastaid.infection.line", state.stage, where).append(", ").append(status));
      float fever = feverC(player);
      if (fever > 0.0F) {
         tooltip.add(Component.translatable("lastaid.infection.fever", String.format(Locale.ROOT, "%.1f", fever)).withStyle(ChatFormatting.RED));
      }
      tooltip.add(Component.translatable("lastaid.infection.hint." + Math.min(state.stage, 4)).withStyle(ChatFormatting.YELLOW));
      List<Component> detail = new ArrayList<>();
      detail.add(Component.translatable("lastaid.infection.detail"));
      detail.add(Component.empty());
      detail.addAll(tooltip);
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.infection.effects." + state.stage)));
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.infection.help.antibiotic")));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.infection.help.clean")));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.infection.help.sleep")));
      if (state.source != null && state.source.limb != null) {
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.infection.help.amputate")));
      }
      return new ConditionSnapshot(
         ID,
         Component.translatable("lastaid.condition.infection", state.stage),
         0xFF30C030,
         state.stage,
         state.source != null ? state.source.mask() : 0,
         progress,
         state.stage >= 3,
         tooltip,
         detail,
         0,
         ICON,
         state.source != null ? 0x4030C030 : 0,
         Wounds.FLAG_BLINK
      );
   }

   public static final class State extends ConditionState {
      public int stage;
      @Nullable
      public Zone source;
      double progress;
      double heal;
      double necrosis;
      long lastAntibiotic = -1L;
      int counter;

      @Override
      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putInt("stage", this.stage);
         tag.putInt("source", this.source == null ? -1 : this.source.ordinal());
         tag.putDouble("progress", this.progress);
         tag.putDouble("heal", this.heal);
         tag.putDouble("necrosis", this.necrosis);
         tag.putLong("last_antibiotic", this.lastAntibiotic);
         return tag;
      }

      @Override
      public void load(CompoundTag tag) {
         this.stage = Mth.clamp(tag.getInt("stage"), 0, 4);
         int source = tag.contains("source") ? tag.getInt("source") : -1;
         this.source = source >= 0 && source < Zone.VALUES.length ? Zone.VALUES[source] : null;
         this.progress = tag.getDouble("progress");
         this.heal = tag.getDouble("heal");
         this.necrosis = tag.getDouble("necrosis");
         this.lastAntibiotic = tag.contains("last_antibiotic") ? tag.getLong("last_antibiotic") : -1L;
         this.markDirty();
      }
   }
}
