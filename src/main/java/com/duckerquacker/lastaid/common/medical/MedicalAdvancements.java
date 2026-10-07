package com.duckerquacker.lastaid.common.medical;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MedicalAdvancements {
   private MedicalAdvancements() {
   }

   public static void award(ServerPlayer serverPlayer, String string) {
      if (serverPlayer.server != null) {
         AdvancementHolder advancementHolder = serverPlayer.server
            .getAdvancements()
            .get(ResourceLocation.fromNamespaceAndPath("lastaid", "medical/" + string));
         if (advancementHolder != null) {
            AdvancementProgress advancementProgress = serverPlayer.getAdvancements().getOrStartProgress(advancementHolder);
            if (!advancementProgress.isDone()) {
               for (String string1 : advancementProgress.getRemainingCriteria()) {
                  serverPlayer.getAdvancements().award(advancementHolder, string1);
               }
            }
         }
      }
   }
}
