package com.duckerquacker.lastaid.common.medical;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

public final class EffectUtil {
   private EffectUtil() {
   }

   public static void keepEffect(ServerPlayer serverPlayer, Holder<MobEffect> holder, int i) {
      MobEffectInstance mobEffectInstance = serverPlayer.getEffect(holder);
      if (mobEffectInstance == null
         || mobEffectInstance.getAmplifier() <= i && (mobEffectInstance.getAmplifier() != i || mobEffectInstance.getDuration() <= 30)) {
         serverPlayer.addEffect(new MobEffectInstance(holder, 50, i, false, false, true));
      }
   }

   public static void pulseEffect(ServerPlayer serverPlayer, Holder<MobEffect> holder, int i, int j) {
      MobEffectInstance mobEffectInstance = serverPlayer.getEffect(holder);
      if (mobEffectInstance == null || mobEffectInstance.getAmplifier() < j || mobEffectInstance.getDuration() < i) {
         serverPlayer.addEffect(new MobEffectInstance(holder, i, j, false, false, true));
      }
   }

   public static ResourceLocation id(String string) {
      return ResourceLocation.fromNamespaceAndPath("lastaid", string);
   }

   public static void setMultiplier(ServerPlayer serverPlayer, Holder<Attribute> holder, ResourceLocation resourceLocation, double d) {
      AttributeInstance attributeInstance = serverPlayer.getAttribute(holder);
      if (attributeInstance != null) {
         AttributeModifier attributeModifier = attributeInstance.getModifier(resourceLocation);
         if (Math.abs(d) < 1.0E-4) {
            if (attributeModifier != null) {
               attributeInstance.removeModifier(resourceLocation);
            }
         } else if (attributeModifier == null || !(Math.abs(attributeModifier.amount() - d) < 1.0E-4)) {
            if (attributeModifier != null) {
               attributeInstance.removeModifier(resourceLocation);
            }

            attributeInstance.addTransientModifier(new AttributeModifier(resourceLocation, d, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }
   }
}
