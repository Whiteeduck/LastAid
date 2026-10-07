package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalConditions;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class MedicalData {
   private final Map<ResourceLocation, ConditionState> states = new LinkedHashMap<>();
   private CompoundTag pendingUnknown = new CompoundTag();
   public long lastDamageTick = -100000L;
   public long lastFightTick = -100000L;
   public long lastJumpTick = -100000L;
   public float pendingFallDistance;
   public boolean pendingFallCrouching;
   public long pendingFallTick;
   public long lastNoiseTick = -100000L;
   public float temperatureQueue;
   public float temperaturePerSecond;

   public <S extends ConditionState> S getState(MedicalCondition<S> medicalCondition) {
      return (S)this.states.computeIfAbsent(medicalCondition.getId(), resourceLocation -> {
         ConditionState conditionState = medicalCondition.createState();
         if (this.pendingUnknown.contains(resourceLocation.toString())) {
            conditionState.load(this.pendingUnknown.getCompound(resourceLocation.toString()));
            this.pendingUnknown.remove(resourceLocation.toString());
         }

         return conditionState;
      });
   }

   public CompoundTag save() {
      CompoundTag compoundTag = this.pendingUnknown.copy();

      for (MedicalCondition medicalCondition : MedicalConditions.all()) {
         ConditionState conditionState = this.states.get(medicalCondition.getId());
         if (conditionState != null) {
            compoundTag.put(medicalCondition.getId().toString(), conditionState.save());
         }
      }

      if (this.temperatureQueue != 0.0F) {
         compoundTag.putFloat("lastaid:temperature_queue", this.temperatureQueue);
         compoundTag.putFloat("lastaid:temperature_rate", this.temperaturePerSecond);
      }

      return compoundTag;
   }

   public void load(CompoundTag compoundTag) {
      this.states.clear();
      this.pendingUnknown = compoundTag.copy();
      this.temperatureQueue = this.pendingUnknown.getFloat("lastaid:temperature_queue");
      this.temperaturePerSecond = this.pendingUnknown.getFloat("lastaid:temperature_rate");
      this.pendingUnknown.remove("lastaid:temperature_queue");
      this.pendingUnknown.remove("lastaid:temperature_rate");

      for (MedicalCondition medicalCondition : MedicalConditions.all()) {
         this.getState(medicalCondition);
      }
   }

   public void queueTemperature(float f, int i) {
      this.temperatureQueue += f;
      this.temperaturePerSecond = Math.max(Math.abs(this.temperaturePerSecond), Math.abs(f) / Math.max(1, i));
   }
}
