package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.network.BodyStatusSyncPacket;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public final class ClientMedicalState {
   private static volatile BodyStatusSyncPacket latest = BodyStatusSyncPacket.EMPTY;
   private static volatile Consumer<BodyStatusSyncPacket> listener = bodyStatusSyncPacket -> {};
   private static volatile UUID localPlayerId;

   private ClientMedicalState() {
   }

   public static void update(BodyStatusSyncPacket bodyStatusSyncPacket) {
      BodyStatusSyncPacket bodyStatusSyncPacket1 = latest;
      latest = bodyStatusSyncPacket;
      listener.accept(bodyStatusSyncPacket1);
   }

   public static void setListener(Consumer<BodyStatusSyncPacket> consumer) {
      listener = consumer;
   }

   public static BodyStatusSyncPacket get() {
      return latest;
   }

   public static ConditionSnapshot find(ResourceLocation resourceLocation) {
      for (ConditionSnapshot conditionSnapshot : latest.conditions()) {
         if (conditionSnapshot.id().equals(resourceLocation)) {
            return conditionSnapshot;
         }
      }

      return null;
   }

   public static int movementFlagsFor(Player player) {
      return localPlayerId != null && localPlayerId.equals(player.getUUID()) ? latest.movementFlags() : 0;
   }

   public static void setLocalPlayer(UUID uUID) {
      localPlayerId = uUID;
   }

   public static void reset() {
      latest = BodyStatusSyncPacket.EMPTY;
   }

   /** Local player: a wound / dressing snapshot covers this part. */
   public static boolean acceptsDressing(net.minecraft.world.entity.player.Player player, com.duckerquacker.lastaid.api.enums.EnumPlayerPart part) {
      if (localPlayerId == null || !localPlayerId.equals(player.getUUID())) {
         return false;
      }
      for (com.duckerquacker.lastaid.api.medical.ConditionSnapshot snapshot : latest.conditions()) {
         if (snapshot.id().getPath().startsWith(com.duckerquacker.lastaid.common.medical.wounds.Wounds.SNAPSHOT_PREFIX) && snapshot.affects(part)) {
            return true;
         }
      }
      return false;
   }
}
