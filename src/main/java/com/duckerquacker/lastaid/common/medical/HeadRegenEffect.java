package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class HeadRegenEffect extends MedicalRegistry.MedicalEffect {
   public HeadRegenEffect(MobEffectCategory mobEffectCategory, int i) {
      super(mobEffectCategory, i);
   }

   public boolean shouldApplyEffectTickThisTick(int i, int j) {
      return i % 20 == 0;
   }

   public boolean applyEffectTick(LivingEntity livingEntity, int i) {
      if (livingEntity instanceof ServerPlayer serverPlayer && !MedicalHooks.blocksPassiveHeadRegen(serverPlayer)) {
         AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer);
         if (abstractPlayerDamageModel == null) {
            return true;
         } else {
            AbstractDamageablePart abstractDamageablePart = abstractPlayerDamageModel.getFromEnum(EnumPlayerPart.HEAD);
            if (abstractDamageablePart.currentHealth > 0.0F && abstractDamageablePart.currentHealth < abstractDamageablePart.getMaxHealth()) {
               MedicalConfig.Items items = MedicalConfig.SERVER.items;
               float f = (float)((Double)items.brothHeadHealTotal.get() / Math.max(1, (Integer)items.brothSeconds.get())) * (i + 1);
               abstractDamageablePart.heal(f, serverPlayer, true);
               CommonUtils.syncDamageModel(serverPlayer);
            }

            return true;
         }
      } else {
         return true;
      }
   }
}
