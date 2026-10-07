package com.duckerquacker.lastaid.api.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import net.minecraft.world.damagesource.DamageSource;

public record DamageContext(DamageSource source, AbstractPlayerDamageModel before, AbstractPlayerDamageModel after, float fallDistance) {
   public float damageTo(EnumPlayerPart enumPlayerPart) {
      return Math.max(0.0F, this.before.getFromEnum(enumPlayerPart).currentHealth - this.after.getFromEnum(enumPlayerPart).currentHealth);
   }

   public float healthAfter(EnumPlayerPart enumPlayerPart) {
      return this.after.getFromEnum(enumPlayerPart).currentHealth;
   }

   public int maxHealth(EnumPlayerPart enumPlayerPart) {
      return this.after.getFromEnum(enumPlayerPart).getMaxHealth();
   }

   public float totalDamage() {
      float f = 0.0F;

      for (EnumPlayerPart enumPlayerPart : EnumPlayerPart.VALUES) {
         f += this.damageTo(enumPlayerPart);
      }

      return f;
   }
}
