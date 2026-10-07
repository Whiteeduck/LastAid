package com.duckerquacker.lastaid.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> the amputated player only: drives the client side amputation feedback (camera shake, red text).
 * Other players never get it, so they only see the animation and hear the sounds.
 *
 * @param phase    {@link #STOP}, {@link #PROCEDURE} (limb is being cut) or {@link #AGONY} (right after the cut)
 * @param strength {@link #NUMB} (painkiller: nothing), {@link #PARTIAL} (painkiller not active yet) or {@link #FULL}
 * @param ticks    how long the phase lasts from now
 */
public record AmputationFeedbackPacket(byte phase, byte strength, int ticks) implements CustomPacketPayload {
   public static final byte STOP = 0;
   public static final byte PROCEDURE = 1;
   public static final byte AGONY = 2;
   public static final byte NUMB = 0;
   public static final byte PARTIAL = 1;
   public static final byte FULL = 2;
   public static final Type<AmputationFeedbackPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lastaid", "amputation_feedback"));
   public static final StreamCodec<RegistryFriendlyByteBuf, AmputationFeedbackPacket> STREAM_CODEC = StreamCodec.of(
      (buf, packet) -> {
         buf.writeByte(packet.phase);
         buf.writeByte(packet.strength);
         buf.writeVarInt(packet.ticks);
      },
      buf -> new AmputationFeedbackPacket(buf.readByte(), buf.readByte(), buf.readVarInt())
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(AmputationFeedbackPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> ClientAccess.receive(packet));
   }

   /** Separate holder so the dedicated server never loads client classes through this packet. */
   private static final class ClientAccess {
      static void receive(AmputationFeedbackPacket packet) {
         com.duckerquacker.lastaid.client.medical.AmputationFeedback.receive(packet.phase, packet.strength, packet.ticks);
      }
   }
}
