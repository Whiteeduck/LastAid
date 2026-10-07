package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.common.medical.compat.TemperatureCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

public final class TemperatureRegulator {
   private TemperatureRegulator() {
   }

   static void tick(ServerPlayer serverPlayer, PlayerMedicalContext playerMedicalContext) {
      TemperatureCompat temperatureCompat = TemperatureCompat.get();
      if (!temperatureCompat.active()) {
         playerMedicalContext.hasLastBody = false;
      } else {
         double d = temperatureCompat.bodyUnits(serverPlayer);
         if (!playerMedicalContext.hasLastBody) {
            playerMedicalContext.lastBodyUnits = d;
            playerMedicalContext.hasLastBody = true;
         } else {
            double e = d - playerMedicalContext.lastBodyUnits;
            MedicalConfig.Server server = MedicalConfig.SERVER;
            if ((Boolean)server.freezeDuringGrace.get() && temperatureCompat.hasGrace(serverPlayer)) {
               if (e != 0.0) {
                  temperatureCompat.addCoreUnits(serverPlayer, -e);
                  d -= e;
               }
            } else if (e < 0.0) {
               double f = 1.0;
               if (serverPlayer.hasEffect(MedicalRegistry.WARM_PROTECTION)) {
                  f *= server.warmProtectionCoolingMultiplier.get();
               }

               if (serverPlayer.getFoodData().getFoodLevel() < (Integer)server.hungryBelowFood.get()) {
                  f *= server.hungryCoolingMultiplier.get();
               }

               int i = serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
               if (i > (Integer)server.tiredAfterTicksAwake.get()) {
                  f *= server.tiredCoolingMultiplier.get();
               }

               if (Math.abs(f - 1.0) > 0.001) {
                  double g = (f - 1.0) * e;
                  temperatureCompat.addCoreUnits(serverPlayer, g);
                  d += g;
               }
            }

            playerMedicalContext.lastBodyUnits = d;
         }
      }
   }

   public static void warmInstantly(ServerPlayer serverPlayer, double d) {
      TemperatureCompat temperatureCompat = TemperatureCompat.get();
      if (temperatureCompat.active() && d > 0.0) {
         double e = temperatureCompat.celsiusToUnits(d);
         temperatureCompat.addCoreUnits(serverPlayer, e);
         PlayerMedicalContext playerMedicalContext = MedicalManager.context(serverPlayer);
         if (playerMedicalContext.hasLastBody) {
            playerMedicalContext.lastBodyUnits += e;
         }
      }
   }
}
