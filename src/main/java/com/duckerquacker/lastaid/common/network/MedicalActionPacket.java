package com.duckerquacker.lastaid.common.network;

import com.duckerquacker.lastaid.common.medical.bone.Bones;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: a treatment chosen in the body panel (splint a limb, reduce a dislocation). */
public record MedicalActionPacket(byte action, byte limb, byte hand) implements CustomPacketPayload {
   public static final byte SPLINT = 0;
   public static final byte REDUCE = 1;
   /** limb = wound zone ordinal, hand = Wounds.ACT_* bit */
   public static final byte WOUND = 2;
   public static final Type<MedicalActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("lastaid", "medical_action"));
   public static final StreamCodec<RegistryFriendlyByteBuf, MedicalActionPacket> STREAM_CODEC = StreamCodec.of(
      (buf, packet) -> {
         buf.writeByte(packet.action);
         buf.writeByte(packet.limb);
         buf.writeByte(packet.hand);
      },
      buf -> new MedicalActionPacket(buf.readByte(), buf.readByte(), buf.readByte())
   );

   @Override
   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(MedicalActionPacket packet, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (!(context.player() instanceof ServerPlayer player)) {
            return;
         }
         if (packet.action == WOUND) {
            if (packet.limb >= 0 && packet.limb < com.duckerquacker.lastaid.common.medical.wounds.Zone.VALUES.length) {
               com.duckerquacker.lastaid.common.medical.wounds.Wounds.requestAction(
                  player, com.duckerquacker.lastaid.common.medical.wounds.Zone.VALUES[packet.limb], packet.hand & 0xFF
               );
            }
            return;
         }
         if (packet.limb < 0 || packet.limb >= Frostbite.Limb.VALUES.length) {
            return;
         }
         Frostbite.Limb limb = Frostbite.Limb.VALUES[packet.limb];
         if (packet.action == SPLINT) {
            Bones.requestSplint(player, packet.hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, limb);
         } else if (packet.action == REDUCE) {
            Bones.requestReduce(player, limb);
         }
      });
   }
}
