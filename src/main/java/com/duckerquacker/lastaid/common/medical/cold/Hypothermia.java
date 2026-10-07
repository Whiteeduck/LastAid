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
import com.duckerquacker.lastaid.common.medical.PlayerCommentTrigger;
import com.duckerquacker.lastaid.common.medical.compat.TemperatureCompat;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class Hypothermia implements MedicalCondition<Hypothermia.State> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lastaid", "hypothermia");
   public static final Hypothermia INSTANCE = new Hypothermia();
   private static final ResourceLocation MINING_ID = EffectUtil.id("hypothermia_mining");

   private Hypothermia() {
   }

   public Hypothermia.State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   public static int stage(Player player) {
      return INSTANCE.state(player).stage;
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   public Hypothermia.State createState() {
      return new Hypothermia.State();
   }

   @Override
   public int getDefaultColor() {
      return -9452545;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return MedicalIcons.SNOWFLAKE;
   }

   public boolean isActive(Hypothermia.State state) {
      return state.stage > 0;
   }

   private static MedicalConfig.Hypothermia cfg() {
      return MedicalConfig.SERVER.cold;
   }

   private static double border(int i) {
      MedicalConfig.Hypothermia hypothermia = cfg();

      return switch (i) {
         case 1 -> hypothermia.stage1BelowC.get();
         case 2 -> hypothermia.stage2BelowC.get();
         case 3 -> hypothermia.stage3BelowC.get();
         default -> hypothermia.stage4BelowC.get();
      };
   }

   private static int band(float f) {
      for (int i = 4; i >= 1; i--) {
         if (f < border(i)) {
            return i;
         }
      }

      return 0;
   }

   public void tick(ServerPlayer serverPlayer, Hypothermia.State state, MedicalContext medicalContext) {
      if (!(Boolean)cfg().enabled.get() || !medicalContext.hasTemperatureMod()) {
         this.setStage(serverPlayer, state, 0);
         applyAttributes(serverPlayer, 0);
      } else if (medicalContext.temperatureFrozen()) {
         applyAttributes(serverPlayer, 0);
      } else {
         float f = medicalContext.bodyTemperatureC();
         int i = band(f);
         if (i > state.stage) {
            if (++state.pendingSeconds >= (Integer)cfg().stageUpDelaySeconds.get()) {
               this.setStage(serverPlayer, state, i);
               state.pendingSeconds = 0;
            }
         } else {
            state.pendingSeconds = 0;
            if (i < state.stage && f >= border(state.stage) + (Double)cfg().hysteresisC.get()) {
               this.setStage(serverPlayer, state, i);
            }
         }

         this.applySymptoms(serverPlayer, state);
         applyAttributes(serverPlayer, state.stage);
      }
   }

   private void setStage(ServerPlayer serverPlayer, Hypothermia.State state, int i) {
      if (i != state.stage) {
         int j = state.stage;
         state.stage = i;
         state.markDirty();
         PlayerCommentTrigger.onTemperatureStageChanged(serverPlayer, j, i);
      }
   }

   private void applySymptoms(ServerPlayer serverPlayer, Hypothermia.State state) {
      MedicalConfig.Hypothermia hypothermia = cfg();
      RandomSource randomSource = serverPlayer.getRandom();
      switch (state.stage) {
         case 1:
            PlayerCommentTrigger.comment(serverPlayer, "hypothermia_1");
            if (randomSource.nextDouble() < (Double)hypothermia.stage1SlownessChance.get()) {
               EffectUtil.pulseEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            }
            break;
         case 2:
            if (randomSource.nextDouble() < (Double)hypothermia.stage2SlownessChance.get()) {
               EffectUtil.pulseEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, 60, 0);
            }
            break;
         case 3:
            EffectUtil.keepEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, 1);
            break;
         case 4:
            EffectUtil.keepEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, 1);
            EffectUtil.keepEffect(serverPlayer, MobEffects.WEAKNESS, 0);
            EffectUtil.keepEffect(serverPlayer, MobEffects.DIG_SLOWDOWN, 0);
            if (--state.faintTimer <= 0) {
               state.faintTimer = 60;
               if (randomSource.nextDouble() < (Double)hypothermia.stage4FaintChancePerMinute.get()) {
                  serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false, true));
                  serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, false, true));
                  serverPlayer.displayClientMessage(Component.translatable("lastaid.hypothermia.faint").withStyle(ChatFormatting.AQUA), true);
               }
            }
      }
   }

   private static void applyAttributes(ServerPlayer serverPlayer, int i) {
      double d = switch (i) {
         case 3 -> -cfg().stage3MiningPenalty.get();
         case 4 -> -cfg().stage4MiningPenalty.get();
         default -> 0.0;
      };
      EffectUtil.setMultiplier(serverPlayer, Attributes.BLOCK_BREAK_SPEED, MINING_ID, d);
   }

   public void clear(ServerPlayer serverPlayer, Hypothermia.State state) {
      state.stage = 0;
      state.pendingSeconds = 0;
      state.markDirty();
      applyAttributes(serverPlayer, 0);
   }

   public ConditionSnapshot snapshot(ServerPlayer serverPlayer, Hypothermia.State state, MedicalContext medicalContext) {
      int i = state.stage;
      ArrayList arrayList = new ArrayList();
      arrayList.add(Component.translatable("lastaid.hypothermia.stage." + i));
      arrayList.add(
         Component.translatable("lastaid.hypothermia.tooltip.body", new Object[]{String.format(Locale.ROOT, "%.1f", medicalContext.bodyTemperatureC())})
      );
      TemperatureCompat temperatureCompat = TemperatureCompat.get();
      double d = temperatureCompat.coldInsulationRateFactor(serverPlayer);
      if (d < 0.99) {
         arrayList.add(Component.translatable("lastaid.gui.row.clothing.value", new Object[]{Math.round((1.0 - d) * 100.0)}).withStyle(ChatFormatting.GRAY));
      }

      if (temperatureCompat.isWet(serverPlayer)) {
         arrayList.add(Component.translatable("lastaid.hypothermia.tooltip.wet").withStyle(ChatFormatting.BLUE));
      }

      ArrayList arrayList1 = new ArrayList();
      arrayList1.add(Component.translatable("lastaid.hypothermia.detail.description"));
      arrayList1.add(Component.empty());
      arrayList1.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
      arrayList1.add(Component.literal(" • ").append(Component.translatable("lastaid.hypothermia.detail.stage" + i)));
      arrayList1.add(Component.empty());
      arrayList1.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));

      for (int j = 1; j <= 3; j++) {
         arrayList1.add(Component.literal(" + ").append(Component.translatable("lastaid.hypothermia.detail.helps." + j)).withStyle(ChatFormatting.GREEN));
      }

      arrayList1.add(Component.translatable("lastaid.concussion.detail.hurts_header").withStyle(ChatFormatting.RED));

      for (int k = 1; k <= 3; k++) {
         arrayList1.add(Component.literal(" - ").append(Component.translatable("lastaid.hypothermia.detail.hurts." + k)).withStyle(ChatFormatting.RED));
      }

      arrayList1.add(Component.empty());
      arrayList1.add(
         Component.translatable("lastaid.hypothermia.detail.borders", new Object[]{fmt(border(1)), fmt(border(2)), fmt(border(3)), fmt(border(4))})
            .withStyle(ChatFormatting.GRAY)
      );
      return new ConditionSnapshot(
         ID,
         Component.translatable("lastaid.condition.hypothermia"),
         this.getDefaultColor(),
         i,
         ConditionSnapshot.mask(EnumPlayerPart.BODY),
         -1.0F,
         i >= 4,
         arrayList,
         arrayList1,
         0,
         MedicalIcons.SNOWFLAKE,
         0,
         0
      );
   }

   private static String fmt(double d) {
      return String.format(Locale.ROOT, "%.1f", d);
   }

   public static final class State extends ConditionState {
      public int stage;
      int pendingSeconds;
      int faintTimer = 60;

      @Override
      public CompoundTag save() {
         CompoundTag compoundTag = new CompoundTag();
         compoundTag.putInt("stage", this.stage);
         return compoundTag;
      }

      @Override
      public void load(CompoundTag compoundTag) {
         this.stage = Mth.clamp(compoundTag.getInt("stage"), 0, 4);
         this.markDirty();
      }
   }
}
