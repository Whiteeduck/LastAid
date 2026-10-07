package com.duckerquacker.lastaid.api.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;

public interface MedicalContext {
   AbstractPlayerDamageModel damageModel();

   float bodyTemperatureC();

   boolean hasTemperatureMod();

   boolean temperatureFrozen();

   default boolean temperatureMatters() {
      return this.hasTemperatureMod() && !this.temperatureFrozen();
   }

   long gameTime();

   int secondsSinceDamage();

   int secondsSinceFight();

   MedicalContext.Activity activity();

   int lightLevel();

   boolean hostilesNearby();

   boolean noisyEnvironment();

   boolean nearHeatSource();

   boolean inSnowstorm();

   boolean otherPlayerNearby(double d);

   public static enum Activity {
      RESTING_IN_BED,
      STILL,
      WALKING,
      STRAINING;
   }
}
