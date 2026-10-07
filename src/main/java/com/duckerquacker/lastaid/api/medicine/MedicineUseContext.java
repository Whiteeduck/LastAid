package com.duckerquacker.lastaid.api.medicine;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MedicineUseContext {
   private final Player player;
   private final Level level;
   private final ItemStack stack;
   @Nullable
   private final AbstractPlayerDamageModel damageModel;

   public MedicineUseContext(Player player, Level level, ItemStack stack, @Nullable AbstractPlayerDamageModel damageModel) {
      this.player = player;
      this.level = level;
      this.stack = stack;
      this.damageModel = damageModel;
   }

   @Nonnull
   public Player getPlayer() {
      return this.player;
   }

   @Nonnull
   public Level getLevel() {
      return this.level;
   }

   @Nonnull
   public ItemStack getStack() {
      return this.stack;
   }

   @Nullable
   public AbstractPlayerDamageModel getDamageModel() {
      return this.damageModel;
   }

   /** Overdose pool doses per intake: 0 morphine pills, 1 morphine injector, 2 adrenaline injector ([overdose] config). */
   private static int overdoseDoses(int kind) {
      if (!com.duckerquacker.lastaid.common.medical.MedicalConfig.SERVER_SPEC.isLoaded()) {
         return 0;
      }
      com.duckerquacker.lastaid.common.medical.MedicalConfig.Overdose cfg = com.duckerquacker.lastaid.common.medical.MedicalConfig.SERVER.overdose;
      return switch (kind) {
         case 0 -> cfg.morphinePillDoses.get();
         case 1 -> cfg.morphineInjectorDoses.get();
         default -> cfg.adrenalineDoses.get();
      };
   }

   public void queuePainkillerActivation() {
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.queuePainkillerActivation();
      } else {
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, PlayerDamageModel.getPainkillerDuration(), 0, false, false));
      }
   }

   public void queueMorphineActivation() {
      com.duckerquacker.lastaid.common.medical.overdose.Overdose.addDose(
         this.player, com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.MORPHINE, overdoseDoses(0)
      );
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.queueMorphineActivation();
      } else if (this.damageModel != null) {
         this.damageModel.applyMorphine(this.player);
      }
   }

   public void applyMorphineInjection() {
      com.duckerquacker.lastaid.common.medical.overdose.Overdose.addDose(
         this.player, com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.MORPHINE, overdoseDoses(1)
      );
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.applyMorphineInjection(this.player);
      } else {
         int basePainRelief = PlayerDamageModel.getRandMorphineDuration();
         int painDuration = Math.round(basePainRelief * PlayerDamageModel.MORPHINE_INJECTOR_DURATION_MULTIPLIER * PlayerDamageModel.MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER);
         this.player.addEffect(new MobEffectInstance(RegistryObjects.MORPHINE_EFFECT, Math.round(basePainRelief * PlayerDamageModel.MORPHINE_INJECTOR_EFFECT_DURATION_MULTIPLIER), 0, false, false));
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, painDuration, 0, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, PlayerDamageModel.MORPHINE_INJECTOR_REGEN_TICKS, 0, false, false));
      }
   }

   public void applyAdrenalineInjection() {
      com.duckerquacker.lastaid.common.medical.overdose.Overdose.addDose(
         this.player, com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.ADRENALINE, overdoseDoses(2)
      );
      if (this.damageModel instanceof PlayerDamageModel playerDamageModel) {
         playerDamageModel.applyAdrenalineInjection(this.player);
      } else {
         MobEffectInstance activePainkiller = this.player.getEffect(RegistryObjects.PAINKILLER_EFFECT);
         int duration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activePainkiller == null ? 0 : activePainkiller.getDuration());
         MobEffectInstance activeAbsorption = this.player.getEffect(MobEffects.ABSORPTION);
         int absorptionDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeAbsorption == null ? 0 : activeAbsorption.getDuration());
         int absorptionAmplifier = Math.max(1, activeAbsorption == null ? 0 : activeAbsorption.getAmplifier());
         MobEffectInstance activeHaste = this.player.getEffect(MobEffects.DIG_SPEED);
         MobEffectInstance activeStrength = this.player.getEffect(MobEffects.DAMAGE_BOOST);
         MobEffectInstance activeSpeed = this.player.getEffect(MobEffects.MOVEMENT_SPEED);
         int hasteDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeHaste == null ? 0 : activeHaste.getDuration());
         int hasteAmplifier = Math.max(0, activeHaste == null ? 0 : activeHaste.getAmplifier());
         int strengthDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeStrength == null ? 0 : activeStrength.getDuration());
         int strengthAmplifier = Math.max(0, activeStrength == null ? 0 : activeStrength.getAmplifier());
         int speedDuration = Math.max(PlayerDamageModel.getAdrenalineDuration(), activeSpeed == null ? 0 : activeSpeed.getDuration());
         int speedAmplifier = Math.max(0, activeSpeed == null ? 0 : activeSpeed.getAmplifier());
         this.player.addEffect(new MobEffectInstance(RegistryObjects.PAINKILLER_EFFECT, duration, 0, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, absorptionDuration, absorptionAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, hasteDuration, hasteAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, strengthDuration, strengthAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, speedDuration, speedAmplifier, false, false));
         this.player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 140, 0, false, false));
      }
   }
}
