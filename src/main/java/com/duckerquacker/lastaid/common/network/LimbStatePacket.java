package com.duckerquacker.lastaid.common.network;

import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: limb status (amputation / frostbite stage) of one player, sent to that player and everyone
 * tracking them. {@code shakeTicks} > 0 only for the player who was just amputated.
 */
public record LimbStatePacket(int entityId, byte[] limbs, int shakeTicks) implements CustomPacketPayload {
   public static final Type<LimbStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lastaid", "limb_state"));
   public static final StreamCodec<RegistryFriendlyByteBuf, LimbStatePacket> STREAM_CODEC = StreamCodec.of(
      (buf, packet) -> {
         buf.writeVarInt(packet.entityId);
         buf.writeByteArray(packet.limbs);
         buf.writeVarInt(packet.shakeTicks);
      },
      buf -> new LimbStatePacket(buf.readVarInt(), buf.readByteArray(16), buf.readVarInt())
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(LimbStatePacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         LimbVisualState.setClient(packet.entityId, packet.limbs);
         if (packet.shakeTicks > 0) {
            ClientAccess.startShake(packet.entityId, packet.shakeTicks);
         }
      });
   }

   /** Separate holder so the dedicated server never loads Minecraft.class through this packet. */
   private static final class ClientAccess {
      static void startShake(int entityId, int ticks) {
         Minecraft mc = Minecraft.getInstance();
         // only the amputated player's own camera shakes
         if (mc.level != null && mc.player != null && mc.player.getId() == entityId) {
            LimbVisualState.startShake(mc.level.getGameTime() + ticks, ticks);
         }
      }
   }
}
