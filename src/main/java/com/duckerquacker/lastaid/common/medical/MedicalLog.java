package com.duckerquacker.lastaid.common.medical;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Last medical events per player for /lastaid log (memory only, cleared on restart). */
public final class MedicalLog {
   public record Entry(long gameTime, Component text) {
   }

   private static final Map<UUID, ArrayDeque<Entry>> LOGS = new HashMap<>();

   private MedicalLog() {
   }

   public static void add(ServerPlayer player, Component text) {
      if (!MedicalConfig.SERVER_SPEC.isLoaded()) {
         return;
      }
      ArrayDeque<Entry> log = LOGS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
      log.addLast(new Entry(player.level().getGameTime(), text));
      int max = MedicalConfig.SERVER.general.medicalLogSize.get();
      while (log.size() > max) {
         log.removeFirst();
      }
      if (MedicalConfig.SERVER.general.debugLogging.get()) {
         com.duckerquacker.lastaid.LastAid.LOGGER.info("[medical] {}: {}", player.getScoreboardName(), text.getString());
      }
   }

   public static List<Entry> get(ServerPlayer player) {
      ArrayDeque<Entry> log = LOGS.get(player.getUUID());
      return log == null ? List.of() : new ArrayList<>(log);
   }

   public static void clear() {
      LOGS.clear();
   }
}
