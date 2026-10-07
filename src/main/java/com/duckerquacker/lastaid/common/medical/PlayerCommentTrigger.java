package com.duckerquacker.lastaid.common.medical;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerCommentTrigger {
   private static final Map<String, Integer> VARIANTS = new HashMap<>();
   private static final Map<UUID, Map<String, Long>> LAST = new ConcurrentHashMap<>();

   private PlayerCommentTrigger() {
   }

   private static void variants(int i, String... strings) {
      for (String string : strings) {
         VARIANTS.put(string, i);
      }
   }

   public static void onConditionAdded(ServerPlayer serverPlayer, String string) {
      comment(serverPlayer, string);
   }

   public static void onSeverityChanged(ServerPlayer serverPlayer, String string, int i, int j) {
      if (j > i) {
         comment(serverPlayer, string);
      }
   }

   public static void onTemperatureStageChanged(ServerPlayer serverPlayer, int i, int j) {
      if (j > i && j > 0) {
         comment(serverPlayer, "hypothermia_" + j);
      }
   }

   public static boolean comment(ServerPlayer serverPlayer, String string) {
      Integer integer = VARIANTS.get(string);
      if (integer == null) {
         return false;
      } else {
         long l = serverPlayer.level().getGameTime();
         long m = ((Integer)MedicalConfig.SERVER.commentCooldownSeconds.get()).intValue() * 20L;
         Map map = LAST.computeIfAbsent(serverPlayer.getUUID(), uUID -> new HashMap<>());
         Long long1 = (Long)map.get(string);
         if (long1 != null && l - long1 < m && l >= long1) {
            return false;
         } else {
            map.put(string, l);
            int i = 1 + serverPlayer.getRandom().nextInt(integer);
            serverPlayer.sendSystemMessage(
               Component.translatable("lastaid.comment." + string + "." + i).withStyle(new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.GRAY})
            );
            return true;
         }
      }
   }

   public static void forget(UUID uUID) {
      LAST.remove(uUID);
   }

   static {
      variants(4, "concussion_1", "concussion_2", "concussion_3");
      variants(4, "hypothermia_1", "hypothermia_2", "hypothermia_3", "hypothermia_4");
      variants(3, "cold_1", "cold_2", "cold_3");
      variants(
         3, "frostbite_1_arm", "frostbite_1_leg", "frostbite_2_arm", "frostbite_2_leg", "frostbite_3_arm", "frostbite_3_leg", "gangrene_arm", "gangrene_leg"
      );
   }
}
