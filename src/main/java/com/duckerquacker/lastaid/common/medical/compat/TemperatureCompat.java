package com.duckerquacker.lastaid.common.medical.compat;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

public abstract class TemperatureCompat {
   public static final float NORMAL_BODY_C = 36.6F;
   private static TemperatureCompat instance;
   private static final ResourceLocation GRACE_ID = ResourceLocation.fromNamespaceAndPath("cold_sweat", "grace");

   public static TemperatureCompat get() {
      if (instance == null) {
         TemperatureCompat temperatureCompat = null;
         if (ModList.get().isLoaded("cold_sweat")) {
            try {
               temperatureCompat = (TemperatureCompat)Class.forName("com.duckerquacker.lastaid.common.medical.compat.ColdSweatTemperature")
                  .getDeclaredConstructor()
                  .newInstance();
               LastAid.LOGGER.info("ColdSweat found, enabling body temperature integration");
            } catch (Throwable throwable) {
               LastAid.LOGGER.error("Failed to set up ColdSweat integration", throwable);
            }
         }

         instance = (TemperatureCompat)(temperatureCompat != null ? temperatureCompat : new TemperatureCompat.None());
      }

      return instance;
   }

   public boolean active() {
      return this.isPresent() && (Boolean)MedicalConfig.SERVER.coldSweatIntegration.get();
   }

   protected abstract boolean isPresent();

   public abstract double bodyUnits(ServerPlayer serverPlayer);

   public abstract double worldCelsius(ServerPlayer serverPlayer);

   public abstract void addCoreUnits(ServerPlayer serverPlayer, double d);

   public double coldInsulationRateFactor(ServerPlayer serverPlayer) {
      return 1.0;
   }

   public boolean isWet(ServerPlayer serverPlayer) {
      return false;
   }

   public double neutralWorldCelsius(ServerPlayer serverPlayer) {
      return 20.0;
   }

   public boolean hasGrace(ServerPlayer serverPlayer) {
      Optional optional = BuiltInRegistries.MOB_EFFECT.getHolder(GRACE_ID);
      return optional.isPresent() && serverPlayer.hasEffect((Holder)optional.get());
   }

   public double unitsToCelsiusDelta(double d) {
      return d * (Double)MedicalConfig.SERVER.celsiusPerColdSweatUnit.get();
   }

   public float bodyCelsius(ServerPlayer serverPlayer) {
      return !this.active() ? 36.6F : (float)(36.6F + this.bodyUnits(serverPlayer) * (Double)MedicalConfig.SERVER.celsiusPerColdSweatUnit.get());
   }

   public double celsiusToUnits(double d) {
      return d / (Double)MedicalConfig.SERVER.celsiusPerColdSweatUnit.get();
   }

   private static final class None extends TemperatureCompat {
      @Override
      protected boolean isPresent() {
         return false;
      }

      @Override
      public double bodyUnits(ServerPlayer serverPlayer) {
         return 0.0;
      }

      @Override
      public double worldCelsius(ServerPlayer serverPlayer) {
         return Double.NaN;
      }

      @Override
      public void addCoreUnits(ServerPlayer serverPlayer, double d) {
      }
   }
}
