package com.duckerquacker.lastaid.common.medical.cold;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalIcons;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import com.duckerquacker.lastaid.common.medical.PlayerCommentTrigger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;

public final class CommonCold implements MedicalCondition<CommonCold.State> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lastaid", "common_cold");
   public static final CommonCold INSTANCE = new CommonCold();

   private CommonCold() {
   }

   public CommonCold.State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   public CommonCold.State createState() {
      return new CommonCold.State();
   }

   @Override
   public int getDefaultColor() {
      return -2047936;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return MedicalIcons.THERMOMETER;
   }

   public boolean isActive(CommonCold.State state) {
      return state.stage > 0 || state.exposure > 0.5F;
   }

   private static MedicalConfig.CommonCold cfg() {
      return MedicalConfig.SERVER.commonCold;
   }

   private static int healSeconds(int i) {
      return switch (i) {
         case 1 -> cfg().healSecondsStage1.get();
         case 2 -> cfg().healSecondsStage2.get();
         default -> cfg().healSecondsStage3.get();
      };
   }

   private static double exposureWeight(int i) {
      return switch (i) {
         case 2 -> cfg().exposureWeightStage2.get();
         case 3 -> cfg().exposureWeightStage3.get();
         case 4 -> cfg().exposureWeightStage4.get();
         default -> 0.0;
      };
   }

   public void tick(ServerPlayer serverPlayer, CommonCold.State state, MedicalContext medicalContext) {
      if ((Boolean)cfg().enabled.get() && !medicalContext.temperatureFrozen()) {
         state.counter++;
         trackSleepCooldown(serverPlayer, state);
         int i = Hypothermia.stage(serverPlayer);
         boolean bl = i >= 2 && !medicalContext.nearHeatSource();
         if (state.stage == 0) {
            float f = state.exposure;
            if (bl) {
               state.exposure = state.exposure + (float)exposureWeight(i);
            } else {
               state.exposure = Math.max(0.0F, state.exposure - ((Double)cfg().exposureDecayPerSecond.get()).floatValue());
            }

            if (state.exposure >= ((Integer)cfg().exposureToCatchSeconds.get()).intValue()) {
               state.stage = 1;
               state.exposure = 0.0F;
               state.healProgress = 0.0F;
               state.sneezeTimer = nextSneeze(1, serverPlayer.getRandom());
               state.markDirty();
               PlayerCommentTrigger.onConditionAdded(serverPlayer, "cold_1");
            } else if (f > 0.5F != state.exposure > 0.5F || state.exposure > 0.0F && state.counter % 5 == 0) {
               state.markDirty();
            }

            state.wasCold = i >= 2;
         } else {
            if (i >= 2 && !state.wasCold) {
               state.healProgress = 0.0F;
               if (state.stage < 3 && serverPlayer.getRandom().nextDouble() < (Double)cfg().reexposureStageUpChance.get()) {
                  int j = state.stage++;
                  PlayerCommentTrigger.onSeverityChanged(serverPlayer, "cold_" + state.stage, j, state.stage);
               }

               state.markDirty();
            }

            state.wasCold = i >= 2;
            double sleepRate = sleepRate(serverPlayer, state, medicalContext);
            if (sleepRate > 0.0 || isWarm(medicalContext)) {
               // Sleeping heals like being warm (a cold body heals slower), otherwise only warmth heals
               state.healProgress = state.healProgress + (float)(healRate(serverPlayer) * (sleepRate > 0.0 ? sleepRate : 1.0));
               if (state.healProgress >= healSeconds(state.stage)) {
                  state.stage = 0;
                  state.healProgress = 0.0F;
                  state.exposure = 0.0F;
                  serverPlayer.displayClientMessage(Component.translatable("lastaid.cold.cured").withStyle(ChatFormatting.GREEN), true);
                  state.markDirty();
                  return;
               }
            }

            if (state.counter % 5 == 0) {
               state.markDirty();
            }

            switch (state.stage) {
               case 2:
                  EffectUtil.keepEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, 0);
                  EffectUtil.keepEffect(serverPlayer, MobEffects.WEAKNESS, 0);
                  break;
               case 3:
                  EffectUtil.keepEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, 1);
                  EffectUtil.keepEffect(serverPlayer, MobEffects.WEAKNESS, 0);
                  EffectUtil.keepEffect(serverPlayer, MobEffects.DIG_SLOWDOWN, 0);
            }

            if (--state.sneezeTimer <= 0) {
               sneeze(serverPlayer, state.stage);
               state.sneezeTimer = nextSneeze(state.stage, serverPlayer.getRandom());
            }

            if (state.stage >= 2 && state.counter % 60 == 0) {
               PlayerCommentTrigger.comment(serverPlayer, "cold_" + state.stage);
            }
         }
      }
   }

   /** Real ticks left until sleep treats the cold again (0 = sleep heals). */
   public static long sleepCooldownLeft(ServerPlayer serverPlayer, CommonCold.State state) {
      long cooldown = MedicalConfig.SERVER.sleep.healCooldownMinutes.get() * 60L * 20L;
      if (cooldown <= 0L || state.lastSleepHealEnd < 0L) {
         return 0L;
      }
      long left = state.lastSleepHealEnd + cooldown - serverPlayer.level().getGameTime();
      return Math.max(0L, left);
   }

   /** Starts the cooldown when the player gets up after a sleep that treated the cold; tells a sick sleeper when sleep does not help. */
   private static void trackSleepCooldown(ServerPlayer serverPlayer, CommonCold.State state) {
      if (serverPlayer.isSleeping()) {
         if (state.stage > 0 && !state.sleepHealing && !state.cooldownNotified) {
            long left = sleepCooldownLeft(serverPlayer, state);
            if (left > 0L) {
               state.cooldownNotified = true;
               serverPlayer.displayClientMessage(Component.translatable("lastaid.cold.sleep_cooldown", Math.max(1L, (left + 1199L) / 1200L)).withStyle(ChatFormatting.GRAY), true);
            }
         }
      } else {
         state.cooldownNotified = false;
         if (state.sleepHealing) {
            state.sleepHealing = false;
            state.lastSleepHealEnd = serverPlayer.level().getGameTime();
            state.markDirty();
         }
      }
   }

   /** 0 when not sleeping or sleep is on cooldown, otherwise the sleep healing multiplier ([sleep] config). */
   private static double sleepRate(ServerPlayer serverPlayer, CommonCold.State state, MedicalContext medicalContext) {
      if (!serverPlayer.isSleeping()) {
         return 0.0;
      }
      if (!state.sleepHealing && sleepCooldownLeft(serverPlayer, state) > 0L) {
         return 0.0;
      }
      // this sleep treats the cold: the cooldown starts when the player gets up
      state.sleepHealing = true;
      MedicalConfig.Sleep sleep = MedicalConfig.SERVER.sleep;
      double rate = sleep.coldRecoveryPerSleepTick.get();
      if (medicalContext.hasTemperatureMod() && medicalContext.bodyTemperatureC() < sleep.coldBodyBelowC.get()) {
         rate *= sleep.coldBodyMultiplier.get();
      }
      return rate;
   }

   @Override
   public void onSleepFinished(ServerPlayer serverPlayer, CommonCold.State state, MedicalContext medicalContext) {
      if (state.stage <= 0 || !(Boolean)cfg().enabled.get()) {
         return;
      }
      if (!state.sleepHealing && sleepCooldownLeft(serverPlayer, state) > 0L) {
         return;
      }
      state.sleepHealing = true;
      MedicalConfig.Sleep sleep = MedicalConfig.SERVER.sleep;
      double bonus = healSeconds(state.stage) * sleep.sleepFullNightBonus.get();
      if (medicalContext.hasTemperatureMod() && medicalContext.bodyTemperatureC() < sleep.coldBodyBelowC.get()) {
         bonus *= sleep.coldBodyMultiplier.get();
      }
      state.healProgress += (float)bonus;
      if (state.healProgress >= healSeconds(state.stage)) {
         state.stage = 0;
         state.healProgress = 0.0F;
         state.exposure = 0.0F;
         serverPlayer.displayClientMessage(Component.translatable("lastaid.cold.cured").withStyle(ChatFormatting.GREEN), true);
      } else {
         serverPlayer.displayClientMessage(Component.translatable("lastaid.cold.slept", Math.round(state.healProgress * 100.0F / healSeconds(state.stage))).withStyle(ChatFormatting.GREEN), true);
      }
      state.markDirty();
   }

   private static boolean isWarm(MedicalContext medicalContext) {
      return !medicalContext.hasTemperatureMod() || medicalContext.bodyTemperatureC() >= (Double)MedicalConfig.SERVER.normalFromC.get();
   }

   public static double healRate(ServerPlayer serverPlayer) {
      double d = 1.0;
      if (serverPlayer.hasEffect(MedicalRegistry.WARM_PROTECTION)) {
         d *= cfg().teaHealMultiplier.get();
      }

      if (serverPlayer.hasEffect(MedicalRegistry.ANTIVIRAL_EFFECT)) {
         d *= cfg().antiviralHealMultiplier.get();
      }

      return d;
   }

   private static int nextSneeze(int i, RandomSource randomSource) {
      MedicalConfig.CommonCold commonCold = cfg();
      int j;
      int k;
      switch (i) {
         case 1:
            j = (Integer)commonCold.sneezeMinStage1.get();
            k = (Integer)commonCold.sneezeMaxStage1.get();
            break;
         case 2:
            j = (Integer)commonCold.sneezeMinStage2.get();
            k = (Integer)commonCold.sneezeMaxStage2.get();
            break;
         default:
            j = (Integer)commonCold.sneezeMinStage3.get();
            k = (Integer)commonCold.sneezeMaxStage3.get();
      }

      return j + randomSource.nextInt(Math.max(1, k - j + 1));
   }

   public void delaySneeze(ServerPlayer serverPlayer) {
      CommonCold.State state = this.state(serverPlayer);
      if (state.stage > 0) {
         state.sneezeTimer = state.sneezeTimer + nextSneeze(state.stage, serverPlayer.getRandom());
      }
   }

   public static void sneeze(ServerPlayer serverPlayer, int i) {
      ServerLevel serverLevel = serverPlayer.serverLevel();
      serverLevel.playSound(
         null,
         serverPlayer.getX(),
         serverPlayer.getY(),
         serverPlayer.getZ(),
         SoundEvents.PANDA_SNEEZE,
         SoundSource.PLAYERS,
         0.6F,
         1.3F + serverPlayer.getRandom().nextFloat() * 0.2F
      );
      serverLevel.gameEvent(serverPlayer, GameEvent.ENTITY_ACTION, serverPlayer.position());
      int j = (Integer)cfg().sneezeChatRadius.get();
      MutableComponent mutableComponent = Component.translatable("lastaid.cold.sneeze", new Object[]{serverPlayer.getDisplayName()})
         .withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.GRAY});

      for (ServerPlayer serverPlayer1 : serverLevel.players()) {
         if (serverPlayer1 == serverPlayer || serverPlayer1.distanceTo(serverPlayer) <= j) {
            serverPlayer1.sendSystemMessage(mutableComponent);
         }
      }
      int k = switch (i) {
         case 1 -> cfg().mobHearingStage1.get();
         case 2 -> cfg().mobHearingStage2.get();
         default -> cfg().mobHearingStage3.get();
      };
      if (k > 0) {
         for (Mob mob : serverLevel.getEntitiesOfClass(
            Mob.class, serverPlayer.getBoundingBox().inflate(k), mob1 -> mob1 instanceof Enemy && mob1.isAlive() && mob1.getTarget() == null
         )) {
            mob.getNavigation().moveTo(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), 1.0);
         }
      }

      if (i >= 3 && !serverPlayer.getMainHandItem().isEmpty() && serverPlayer.getRandom().nextDouble() < (Double)cfg().sneezeDropChanceStage3.get()) {
         serverPlayer.drop(false);
         serverPlayer.displayClientMessage(Component.translatable("lastaid.cold.cough_drop").withStyle(ChatFormatting.GRAY), true);
      }
   }

   public void clear(ServerPlayer serverPlayer, CommonCold.State state) {
      state.stage = 0;
      state.exposure = 0.0F;
      state.healProgress = 0.0F;
      state.markDirty();
   }

   public ConditionSnapshot snapshot(ServerPlayer serverPlayer, CommonCold.State state, MedicalContext medicalContext) {
      int i = ConditionSnapshot.mask(EnumPlayerPart.BODY);
      if (state.stage == 0) {
         float g = Mth.clamp(state.exposure / ((Integer)cfg().exposureToCatchSeconds.get()).intValue(), 0.0F, 1.0F);
         List list = List.of(
            Component.translatable("lastaid.cold.risk.tooltip", new Object[]{Math.round(g * 100.0F)}),
            Component.translatable("lastaid.cold.risk.hint").withStyle(ChatFormatting.GRAY)
         );
         List list1 = List.of(
            Component.translatable("lastaid.cold.risk.detail"),
            Component.empty(),
            Component.translatable("lastaid.cold.risk.tooltip", new Object[]{Math.round(g * 100.0F)})
         );
         return new ConditionSnapshot(
            ID, Component.translatable("lastaid.condition.cold_risk"), -12224, 0, i, g, false, list, list1, 0, MedicalIcons.THERMOMETER, 0, 1
         );
      } else {
         int j = healSeconds(state.stage);
         float f = Mth.clamp(state.healProgress / j, 0.0F, 1.0F);
         boolean bl = isWarm(medicalContext);
         double d = healRate(serverPlayer);
         ArrayList arrayList = new ArrayList();
         arrayList.add(Component.translatable("lastaid.cold.stage." + state.stage));
         arrayList.add(Component.translatable("lastaid.concussion.tooltip.progress", new Object[]{Math.round(f * 100.0F)}));
         if (!bl) {
            arrayList.add(Component.translatable("lastaid.cold.tooltip.too_cold").withStyle(ChatFormatting.AQUA));
         }

         if (d > 1.0) {
            arrayList.add(Component.translatable("lastaid.cold.tooltip.boost", new Object[]{String.format("x%.1f", d)}).withStyle(ChatFormatting.GREEN));
         }

         ArrayList arrayList1 = new ArrayList();
         arrayList1.add(Component.translatable("lastaid.cold.detail.description"));
         arrayList1.add(Component.empty());
         arrayList1.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
         arrayList1.add(Component.literal(" • ").append(Component.translatable("lastaid.cold.detail.stage" + state.stage)));
         arrayList1.add(Component.empty());
         arrayList1.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));

         for (int k = 1; k <= 3; k++) {
            arrayList1.add(Component.literal(" + ").append(Component.translatable("lastaid.cold.detail.helps." + k)).withStyle(ChatFormatting.GREEN));
         }

         arrayList1.add(Component.translatable("lastaid.concussion.detail.hurts_header").withStyle(ChatFormatting.RED));

         for (int m = 1; m <= 2; m++) {
            arrayList1.add(Component.literal(" - ").append(Component.translatable("lastaid.cold.detail.hurts." + m)).withStyle(ChatFormatting.RED));
         }

         arrayList1.add(Component.empty());
         if (bl) {
            int n = (int)Math.ceil(Math.max(0.0F, j - state.healProgress) / d);
            arrayList1.add(
               Component.translatable("lastaid.concussion.detail.eta", new Object[]{String.format("%d:%02d", n / 60, n % 60)}).withStyle(ChatFormatting.AQUA)
            );
         } else {
            arrayList1.add(Component.translatable("lastaid.cold.tooltip.too_cold").withStyle(ChatFormatting.AQUA));
         }

         long o = serverPlayer.level().getGameTime() - state.lastAntiviralTick;
         long l = ((Integer)cfg().antiviralDoseTicks.get()).intValue();
         if (o < l) {
            arrayList1.add(Component.translatable("lastaid.antiviral.next_dose", new Object[]{formatTicks(l - o)}).withStyle(ChatFormatting.GRAY));
         }

         return new ConditionSnapshot(
            ID,
            Component.translatable("lastaid.condition.common_cold"),
            this.getDefaultColor(),
            state.stage,
            i,
            f,
            state.stage >= 3,
            arrayList,
            arrayList1,
            0,
            MedicalIcons.THERMOMETER,
            0,
            0
         );
      }
   }

   public static String formatTicks(long l) {
      long m = l / 20L;
      return String.format("%d:%02d", m / 60L, m % 60L);
   }

   public static final class State extends ConditionState {
      public int stage;
      public float exposure;
      public float healProgress;
      public long lastAntiviralTick = -1000000L;
      int sneezeTimer = 60;
      boolean wasCold;
      int counter;
      /** Game time when the player got up after the last sleep that treated the cold (-1 = never). */
      public long lastSleepHealEnd = -1L;
      /** The current sleep treats the cold (cooldown starts when it ends). */
      boolean sleepHealing;
      boolean cooldownNotified;

      @Override
      public CompoundTag save() {
         CompoundTag compoundTag = new CompoundTag();
         compoundTag.putInt("stage", this.stage);
         compoundTag.putFloat("exposure", this.exposure);
         compoundTag.putFloat("heal", this.healProgress);
         compoundTag.putLong("antiviral", this.lastAntiviralTick);
         compoundTag.putLong("sleep_heal_end", this.lastSleepHealEnd);
         return compoundTag;
      }

      @Override
      public void load(CompoundTag compoundTag) {
         this.stage = Mth.clamp(compoundTag.getInt("stage"), 0, 3);
         this.exposure = compoundTag.getFloat("exposure");
         this.healProgress = compoundTag.getFloat("heal");
         this.lastAntiviralTick = compoundTag.contains("antiviral") ? compoundTag.getLong("antiviral") : -1000000L;
         this.lastSleepHealEnd = compoundTag.contains("sleep_heal_end") ? compoundTag.getLong("sleep_heal_end") : -1L;
         this.markDirty();
      }
   }
}
