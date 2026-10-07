package com.duckerquacker.lastaid.api.medical;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class MedicalConditions {
   private static final Map<ResourceLocation, MedicalCondition<?>> CONDITIONS = new LinkedHashMap<>();

   private MedicalConditions() {
   }

   public static synchronized <T extends MedicalCondition<?>> T register(T medicalCondition) {
      if (CONDITIONS.putIfAbsent(medicalCondition.getId(), medicalCondition) != null) {
         throw new IllegalStateException("Duplicate medical condition " + medicalCondition.getId());
      } else {
         return (T)medicalCondition;
      }
   }

   public static MedicalCondition<?> get(ResourceLocation resourceLocation) {
      return CONDITIONS.get(resourceLocation);
   }

   public static Collection<MedicalCondition<?>> all() {
      return Collections.unmodifiableCollection(CONDITIONS.values());
   }
}
