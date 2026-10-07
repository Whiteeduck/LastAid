package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.PlayerMedicalContext;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

final class MedicalItemHelper {
   static final double TREAT_RANGE = 2.5;

   private MedicalItemHelper() {
   }

   static ServerPlayer asTarget(Player player, LivingEntity livingEntity) {
      if (!(livingEntity instanceof ServerPlayer serverPlayer && serverPlayer.isAlive())) {
         return null;
      } else if (serverPlayer.distanceTo(player) > 2.5) {
         player.displayClientMessage(Component.translatable("lastaid.medical.too_far").withStyle(ChatFormatting.RED), true);
         return null;
      } else {
         return serverPlayer;
      }
   }

   static boolean needsCompanion(ServerPlayer serverPlayer, ServerPlayer serverPlayer1) {
      if (serverPlayer != serverPlayer1 || !(Boolean)MedicalConfig.SERVER.concussion.requireSecondPlayerForSeverity3.get()) {
         return false;
      } else if (Concussion.INSTANCE.state(serverPlayer1).severity < 3) {
         return false;
      } else if (PlayerMedicalContext.isOtherPlayerNearby(serverPlayer1, 8.0)) {
         return false;
      } else {
         serverPlayer.displayClientMessage(Component.translatable("lastaid.medical.need_companion").withStyle(ChatFormatting.RED), true);
         return true;
      }
   }
}
