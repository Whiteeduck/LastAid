package com.duckerquacker.lastaid.common.medical.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.ArmorInsulationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.Temperature.Trait;
import com.momosoftworks.coldsweat.api.util.Temperature.Units;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.duckerquacker.lastaid.LastAid;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

public final class ColdSweatTemperature extends TemperatureCompat {
   private boolean broken;

   @Override
   protected boolean isPresent() {
      return !this.broken;
   }

   private void fail(Throwable throwable) {
      if (!this.broken) {
         this.broken = true;
         LastAid.LOGGER.error("ColdSweat integration failed and has been disabled", throwable);
      }
   }

   @Override
   public double bodyUnits(ServerPlayer serverPlayer) {
      try {
         return Temperature.get(serverPlayer, Trait.BODY);
      } catch (Throwable throwable) {
         this.fail(throwable);
         return 0.0;
      }
   }

   @Override
   public double worldCelsius(ServerPlayer serverPlayer) {
      try {
         double d = Temperature.get(serverPlayer, Trait.WORLD);
         return Temperature.convert(d, Units.MC, Units.C, true);
      } catch (Throwable throwable) {
         this.fail(throwable);
         return Double.NaN;
      }
   }

   @Override
   public double coldInsulationRateFactor(ServerPlayer serverPlayer) {
      try {
         double d = 0.0;

         for (Trait trait : Trait.values()) {
            if (trait.isForModifiers()) {
               Optional optional = Temperature.getModifier(serverPlayer, trait, ArmorInsulationTempModifier.class);
               if (optional.isPresent()) {
                  d = Math.max(d, ((ArmorInsulationTempModifier)optional.get()).getNBT().getDouble("cold"));
               }
            }
         }

         if (d <= 0.0) {
            return 1.0;
         } else {
            double e = (Double)ConfigSettings.INSULATION_STRENGTH.get();
            return Math.pow(0.1, d * e / 40.0);
         }
      } catch (Throwable throwable) {
         this.fail(throwable);
         return 1.0;
      }
   }

   @Override
   public boolean isWet(ServerPlayer serverPlayer) {
      try {
         for (Trait trait : Trait.values()) {
            if (trait.isForModifiers()) {
               Optional optional = Temperature.getModifier(serverPlayer, trait, WaterTempModifier.class);
               if (optional.isPresent() && ((WaterTempModifier)optional.get()).getTemperature() != 0.0) {
                  return true;
               }
            }
         }

         return false;
      } catch (Throwable throwable) {
         this.fail(throwable);
         return false;
      }
   }

   @Override
   public double neutralWorldCelsius(ServerPlayer serverPlayer) {
      try {
         return Temperature.convert(Temperature.getNeutralWorldTemp(serverPlayer), Units.MC, Units.C, true);
      } catch (Throwable throwable) {
         this.fail(throwable);
         return 20.0;
      }
   }

   @Override
   public void addCoreUnits(ServerPlayer serverPlayer, double d) {
      try {
         double e = Temperature.get(serverPlayer, Trait.CORE);
         Temperature.set(serverPlayer, Trait.CORE, Math.max(-150.0, Math.min(150.0, e + d)));
      } catch (Throwable throwable) {
         this.fail(throwable);
      }
   }
}
