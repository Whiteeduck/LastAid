package com.duckerquacker.lastaid.common.medical.overdose;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalIcons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Morphine / adrenaline overdose. Every intake adds doses to a pool (one pool per drug), the pool breaks down
 * slowly ({@code [overdose] overdoseDecayHours} per dose). From {@code overdoseThreshold} doses the player overdoses:
 * stage I at the threshold, stage II one dose above, stage III at {@code maxDose}.
 * Both pools above zero at the same time: mixed intoxication (slower, shaky camera).
 * Naloxone empties the morphine pool, the beta blocker the adrenaline pool.
 * <p>
 * The client gets the pools through a hidden snapshot ({@link #DRUGS_ID}) for the "Drugs" rows of the body panel and
 * the camera effects.
 */
public final class Overdose implements MedicalCondition<Overdose.State> {
   public static final Overdose INSTANCE = new Overdose();
   public static final ResourceLocation ID = EffectUtil.id("overdose");
   public static final ResourceLocation DRUGS_ID = EffectUtil.id("drugs");
   public static final ResourceLocation MORPHINE_ID = EffectUtil.id("overdose_morphine");
   public static final ResourceLocation ADRENALINE_ID = EffectUtil.id("overdose_adrenaline");
   public static final ResourceLocation MIXED_ID = EffectUtil.id("mixed_intoxication");
   /** displayFlags bit: the snapshot is data only, the panel does not list it as a condition. */
   public static final int FLAG_HIDDEN = 8;
   public static final ResourceKey<DamageType> OVERDOSE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, EffectUtil.id("overdose"));
   /** Cardiac arrest / breathing stops: the torso. */
   public static final DirectDamageDistributionAlgorithm TORSO = new DirectDamageDistributionAlgorithm(EnumPlayerPart.BODY, false);
   /** Slow poisoning: spread over the whole body, so the numbers act like plain health. */
   public static final com.duckerquacker.lastaid.common.damagesystem.distribution.EqualDamageDistributionAlgorithm SPREAD =
      new com.duckerquacker.lastaid.common.damagesystem.distribution.EqualDamageDistributionAlgorithm(false, 1.0F);
   private static final ThreadLocal<com.duckerquacker.lastaid.api.distribution.IDamageDistributionAlgorithm> APPLYING = new ThreadLocal<>();
   private static final ResourceLocation SPEED_ID = EffectUtil.id("mixed_intoxication_speed");
   private static final ResourceLocation ATTACK_SPEED_ID = EffectUtil.id("mixed_intoxication_attack_speed");

   public enum Drug {
      MORPHINE,
      ADRENALINE
   }

   private Overdose() {
   }

   private static MedicalConfig.Overdose cfg() {
      return MedicalConfig.SERVER.overdose;
   }

   private static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && MedicalConfig.SERVER.enabled.get() && cfg().enabled.get();
   }

   public static boolean isApplying() {
      return APPLYING.get() != null;
   }

   /** Distribution of the overdose damage being applied right now. */
   public static com.duckerquacker.lastaid.api.distribution.IDamageDistributionAlgorithm distribution() {
      return APPLYING.get();
   }

   public State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   // ---- pools ----

   /** Whole doses shown to the player (a pool of 3.2 still counts as 4 doses). */
   public static int doses(float pool) {
      return pool <= 0.001F ? 0 : Mth.ceil(pool - 0.001F);
   }

   public static int stage(float pool) {
      if (!MedicalConfig.SERVER_SPEC.isLoaded()) {
         return 0;
      }
      int doses = doses(pool);
      int threshold = cfg().overdoseThreshold.get();
      int max = Math.max(threshold, cfg().maxDose.get());
      if (doses >= max) {
         return 3;
      } else if (doses >= threshold + 1) {
         return 2;
      } else {
         return doses >= threshold ? 1 : 0;
      }
   }

   /** Called when a drug is taken (pills, injector). */
   public static void addDose(Player player, Drug drug, int doses) {
      if (!(player instanceof ServerPlayer serverPlayer) || !enabled() || doses <= 0) {
         return;
      }
      State state = INSTANCE.state(serverPlayer);
      float max = cfg().maxDose.get();
      float before = state.pool(drug);
      float after = Math.min(max, before + doses);
      state.set(drug, after);
      state.markDirty();
      int stageBefore = stage(before);
      int stageAfter = stage(after);
      if (stageAfter > stageBefore) {
         String key = drug == Drug.MORPHINE ? "lastaid.overdose.morphine.warn." : "lastaid.overdose.adrenaline.warn.";
         serverPlayer.displayClientMessage(Component.translatable(key + stageAfter).withStyle(stageAfter >= 3 ? ChatFormatting.DARK_RED : ChatFormatting.RED), true);
      }
   }

   /** Naloxone (morphine) / beta blocker (adrenaline): the pool is gone at once, so are the symptoms. */
   public static boolean applyAntidote(ServerPlayer target, Drug drug) {
      State state = INSTANCE.state(target);
      if (state.pool(drug) <= 0.0F) {
         return false;
      }
      state.set(drug, 0.0F);
      if (drug == Drug.MORPHINE) {
         state.morphineStage3Seconds = 0;
         target.removeEffect(MobEffects.BLINDNESS);
         // naloxone also ends the morphine high itself
         target.removeEffect(RegistryObjects.MORPHINE_EFFECT);
      } else {
         state.arrested = false;
      }
      target.removeEffect(MobEffects.CONFUSION);
      target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
      target.removeEffect(MobEffects.WEAKNESS);
      state.markDirty();
      updateMixed(target, state);
      return true;
   }

   // ---- condition ----

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
      return 0xFFB04CFF;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return MedicalIcons.PILL;
   }

   @Override
   public boolean isActive(State state) {
      return state.morphine > 0.0F || state.adrenaline > 0.0F;
   }

   @Override
   public void tick(ServerPlayer player, State state, MedicalContext context) {
      if (!enabled()) {
         return;
      }
      state.counter++;
      // break down: one dose per decayHours game hours (1000 ticks each), this runs once per second
      float decay = (float)(20.0 / (cfg().overdoseDecayHours.get() * 1000.0));
      int morphineStageBefore = stage(state.morphine);
      int adrenalineStageBefore = stage(state.adrenaline);
      boolean active = isActive(state);
      state.morphine = Math.max(0.0F, state.morphine - decay);
      state.adrenaline = Math.max(0.0F, state.adrenaline - decay);
      int morphineStage = stage(state.morphine);
      int adrenalineStage = stage(state.adrenaline);
      if (morphineStage != morphineStageBefore || adrenalineStage != adrenalineStageBefore || active != isActive(state) || state.counter % 10 == 0) {
         state.markDirty();
      }
      tickMorphine(player, state, morphineStage);
      tickAdrenaline(player, state, adrenalineStage);
      updateMixed(player, state);
      if (mixed(state) && state.counter % 30 == 0) {
         EffectUtil.pulseEffect(player, MobEffects.CONFUSION, 100, 0);
      }
   }

   private static void tickMorphine(ServerPlayer player, State state, int stage) {
      MedicalConfig.Overdose cfg = cfg();
      if (stage < 3) {
         state.morphineStage3Seconds = 0;
      }
      switch (stage) {
         case 1 -> {
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
            if (state.counter % 30 == 0) {
               EffectUtil.pulseEffect(player, MobEffects.CONFUSION, 200, 0);
            }
            if (state.counter % 60 == 0) {
               // nodding off for a moment
               player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, true));
            }
         }
         case 2 -> {
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 2);
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 1);
            if (state.counter % 4 == 0) {
               player.playNotifySound(SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.7F);
            }
            if (state.counter % cfg.morphineStage2Interval.get() == 0) {
               hurt(player, cfg.morphineStage2Damage.get().floatValue());
            }
         }
         case 3 -> {
            state.morphineStage3Seconds++;
            EffectUtil.keepEffect(player, MobEffects.BLINDNESS, 0);
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 3);
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 1);
            if (state.counter % 4 == 0) {
               player.playNotifySound(SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.6F, 0.5F);
            }
            if (state.counter % cfg.morphineStage3Interval.get() == 0) {
               hurt(player, cfg.morphineStage3Damage.get().floatValue());
            }
            int death = cfg.morphineDeathSeconds.get();
            if (death > 0 && state.morphineStage3Seconds >= death && player.isAlive()) {
               // breathing stops
               hurt(player, 1000.0F, true);
            }
         }
         default -> {
         }
      }
   }

   private static void tickAdrenaline(ServerPlayer player, State state, int stage) {
      MedicalConfig.Overdose cfg = cfg();
      if (stage < 3) {
         state.arrested = false;
      }
      if (stage >= 1) {
         // a loud heartbeat only this player hears
         player.playNotifySound(RegistryObjects.HEARTBEAT.value(), SoundSource.PLAYERS, stage >= 2 ? 1.0F : 0.7F, stage >= 2 ? 1.25F : 1.05F);
      }
      switch (stage) {
         case 1 -> {
            if (state.counter % 30 == 0) {
               EffectUtil.pulseEffect(player, MobEffects.CONFUSION, 120, 0);
            }
         }
         case 2 -> {
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 0);
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 1);
            if (state.counter % cfg.adrenalineStage2Interval.get() == 0) {
               hurt(player, cfg.adrenalineStage2Damage.get().floatValue());
            }
         }
         case 3 -> {
            EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 1);
            EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 2);
            if (!state.arrested) {
               state.arrested = true;
               state.markDirty();
               player.sendSystemMessage(Component.translatable("lastaid.overdose.adrenaline.arrest").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
               hurt(player, cfg.adrenalineArrestDamage.get().floatValue(), true);
            } else if (state.counter % cfg.adrenalineStage3Interval.get() == 0) {
               hurt(player, cfg.adrenalineStage3Damage.get().floatValue());
            }
         }
         default -> {
         }
      }
   }

   /** Stage II+ of either drug: the poisoned body does not regenerate from food (else saturation outheals the damage). */
   public static boolean blocksNaturalRegen(Player player) {
      if (!(player instanceof ServerPlayer) || !enabled()) {
         return false;
      }
      State state = INSTANCE.state(player);
      return stage(state.morphine) >= 2 || stage(state.adrenaline) >= 2;
   }

   /** For /medical overdose: set the pool, restart the stage III timers. */
   public static void setPool(ServerPlayer player, Drug drug, float doses) {
      State state = INSTANCE.state(player);
      state.set(drug, Math.max(0.0F, doses));
      state.morphineStage3Seconds = 0;
      state.arrested = false;
      state.markDirty();
   }

   public static boolean mixed(State state) {
      return doses(state.morphine) >= 1 && doses(state.adrenaline) >= 1;
   }

   private static void updateMixed(ServerPlayer player, State state) {
      double penalty = enabled() && mixed(state) ? -cfg().mixedSpeedPenalty.get() : 0.0;
      EffectUtil.setMultiplier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, penalty);
      EffectUtil.setMultiplier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, penalty);
   }

   private static void hurt(ServerPlayer player, float amount) {
      hurt(player, amount, false);
   }

   private static void hurt(ServerPlayer player, float amount, boolean torso) {
      if (amount <= 0.0F || !player.isAlive()) {
         return;
      }
      APPLYING.set(torso ? TORSO : SPREAD);
      try {
         player.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(OVERDOSE_DAMAGE)), amount);
      } finally {
         APPLYING.remove();
      }
   }

   @Override
   public void clear(ServerPlayer player, State state) {
      state.morphine = 0.0F;
      state.adrenaline = 0.0F;
      state.morphineStage3Seconds = 0;
      state.arrested = false;
      state.markDirty();
      updateMixed(player, state);
   }

   // ---- client data ----

   @Override
   public void collectSnapshots(ServerPlayer player, State state, MedicalContext context, List<ConditionSnapshot> list) {
      if (!enabled() || !this.isActive(state)) {
         return;
      }
      int max = cfg().maxDose.get();
      int morphineDoses = Math.min(63, doses(state.morphine));
      int adrenalineDoses = Math.min(63, doses(state.adrenaline));
      int morphineStage = stage(state.morphine);
      int adrenalineStage = stage(state.adrenaline);
      boolean mixed = mixed(state);
      int packed = morphineDoses | adrenalineDoses << 6 | morphineStage << 12 | adrenalineStage << 14 | (mixed ? 1 << 16 : 0);
      list.add(
         new ConditionSnapshot(DRUGS_ID, Component.translatable("lastaid.gui.row.drugs"), 0xFFB04CFF, max, 0, 0.0F, false, List.of(), List.of(), packed, null, 0, FLAG_HIDDEN)
      );
      if (morphineStage > 0) {
         list.add(this.stageSnapshot(MORPHINE_ID, "morphine", "naloxone", morphineStage, morphineDoses, max, 0xFFB04CFF));
      }
      if (adrenalineStage > 0) {
         list.add(this.stageSnapshot(ADRENALINE_ID, "adrenaline", "beta_blocker", adrenalineStage, adrenalineDoses, max, 0xFFFF7040));
      }
      if (mixed) {
         List<Component> tooltip = List.of(Component.translatable("lastaid.overdose.mixed.tooltip").withStyle(ChatFormatting.GRAY));
         List<Component> detail = List.of(
            Component.translatable("lastaid.overdose.mixed.detail"),
            Component.empty(),
            Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN),
            Component.literal(" • ").append(Component.translatable("lastaid.overdose.mixed.help"))
         );
         list.add(new ConditionSnapshot(MIXED_ID, Component.translatable("lastaid.condition.mixed_intoxication"), 0xFFE0C040, 1, 0, 0.0F, false, tooltip, detail, 0, MedicalIcons.PILL, 0, 0));
      }
   }

   private ConditionSnapshot stageSnapshot(ResourceLocation id, String drug, String antidote, int stage, int doses, int max, int color) {
      List<Component> tooltip = new ArrayList<>();
      tooltip.add(Component.translatable("lastaid.overdose." + drug + ".stage." + stage));
      tooltip.add(Component.translatable("lastaid.overdose.doses", doses, max).withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("lastaid.overdose.need", Component.translatable("item.lastaid." + antidote)).withStyle(ChatFormatting.YELLOW));
      List<Component> detail = new ArrayList<>();
      detail.add(Component.translatable("lastaid.overdose." + drug + ".detail"));
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.overdose." + drug + ".stage." + stage)));
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.overdose.help.antidote", Component.translatable("item.lastaid." + antidote))));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.overdose.help.time")));
      return new ConditionSnapshot(
         id, Component.translatable("lastaid.condition.overdose_" + drug), color, stage, ConditionSnapshot.mask(EnumPlayerPart.BODY),
         (float)doses / Math.max(1, max), stage >= 2, tooltip, detail, 0, MedicalIcons.PILL, 0, 0
      );
   }

   @Override
   public ConditionSnapshot snapshot(ServerPlayer player, State state, MedicalContext context) {
      List<ConditionSnapshot> list = new ArrayList<>();
      this.collectSnapshots(player, state, context, list);
      return list.isEmpty() ? null : list.get(0);
   }

   public static final class State extends ConditionState {
      public float morphine;
      public float adrenaline;
      int morphineStage3Seconds;
      boolean arrested;
      int counter;

      public float pool(Drug drug) {
         return drug == Drug.MORPHINE ? this.morphine : this.adrenaline;
      }

      void set(Drug drug, float value) {
         if (drug == Drug.MORPHINE) {
            this.morphine = value;
         } else {
            this.adrenaline = value;
         }
      }

      @Override
      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putFloat("morphine", this.morphine);
         tag.putFloat("adrenaline", this.adrenaline);
         tag.putInt("morphine_stage3", this.morphineStage3Seconds);
         tag.putBoolean("arrested", this.arrested);
         return tag;
      }

      @Override
      public void load(CompoundTag tag) {
         this.morphine = Math.max(0.0F, tag.getFloat("morphine"));
         this.adrenaline = Math.max(0.0F, tag.getFloat("adrenaline"));
         this.morphineStage3Seconds = tag.getInt("morphine_stage3");
         this.arrested = tag.getBoolean("arrested");
         this.markDirty();
      }
   }
}
