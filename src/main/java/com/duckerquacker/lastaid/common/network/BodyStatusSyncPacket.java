package com.duckerquacker.lastaid.common.network;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.medical.ClientMedicalState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BodyStatusSyncPacket(List<ConditionSnapshot> conditions, BodyStatusSyncPacket.Temperature temperature, int ticksSinceRest, int movementFlags)
   implements CustomPacketPayload {
   public static final byte COMFORT_OK = 0;
   public static final byte COMFORT_COLD = 1;
   public static final byte COMFORT_HOT = 2;
   public static final byte COMFORT_HYPOTHERMIA = 3;
   public static final byte COMFORT_HEATSTROKE = 4;
   public static final BodyStatusSyncPacket EMPTY = new BodyStatusSyncPacket(List.of(), BodyStatusSyncPacket.Temperature.NONE, 0, 0);
   public static final Type<BodyStatusSyncPacket> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("lastaid", "body_status"));
   public static final StreamCodec<RegistryFriendlyByteBuf, BodyStatusSyncPacket> STREAM_CODEC = StreamCodec.of(
      (registryFriendlyByteBuf, bodyStatusSyncPacket) -> bodyStatusSyncPacket.write(registryFriendlyByteBuf), BodyStatusSyncPacket::read
   );

   public boolean hasTemperature() {
      return this.temperature.available();
   }

   public float bodyC() {
      return this.temperature.bodyC();
   }

   public float worldC() {
      return this.temperature.worldC();
   }

   public byte worldTrend() {
      return this.temperature.worldTrend();
   }

   public byte comfort() {
      return this.temperature.comfort();
   }

   private void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
      registryFriendlyByteBuf.writeVarInt(this.conditions.size());

      for (ConditionSnapshot conditionSnapshot : this.conditions) {
         conditionSnapshot.write(registryFriendlyByteBuf);
      }

      this.temperature.write(registryFriendlyByteBuf);
      registryFriendlyByteBuf.writeVarInt(this.ticksSinceRest);
      registryFriendlyByteBuf.writeVarInt(this.movementFlags);
   }

   private static BodyStatusSyncPacket read(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
      int i = registryFriendlyByteBuf.readVarInt();
      ArrayList arrayList = new ArrayList(i);

      for (int j = 0; j < i; j++) {
         arrayList.add(ConditionSnapshot.read(registryFriendlyByteBuf));
      }

      return new BodyStatusSyncPacket(
         arrayList, BodyStatusSyncPacket.Temperature.read(registryFriendlyByteBuf), registryFriendlyByteBuf.readVarInt(), registryFriendlyByteBuf.readVarInt()
      );
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static void handle(BodyStatusSyncPacket bodyStatusSyncPacket, IPayloadContext iPayloadContext) {
      iPayloadContext.enqueueWork(() -> ClientMedicalState.update(bodyStatusSyncPacket));
   }

   public record Temperature(
      boolean available,
      float bodyC,
      float worldC,
      byte worldTrend,
      byte comfort,
      float feelsLikeC,
      float insulationPercent,
      boolean wet,
      boolean grace,
      boolean warmProtection
   ) {
      public static final BodyStatusSyncPacket.Temperature NONE = new BodyStatusSyncPacket.Temperature(
         false, 36.6F, Float.NaN, (byte)0, (byte)0, Float.NaN, -1.0F, false, false, false
      );

      void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
         registryFriendlyByteBuf.writeBoolean(this.available);
         registryFriendlyByteBuf.writeFloat(this.bodyC);
         registryFriendlyByteBuf.writeFloat(this.worldC);
         registryFriendlyByteBuf.writeByte(this.worldTrend);
         registryFriendlyByteBuf.writeByte(this.comfort);
         registryFriendlyByteBuf.writeFloat(this.feelsLikeC);
         registryFriendlyByteBuf.writeFloat(this.insulationPercent);
         registryFriendlyByteBuf.writeBoolean(this.wet);
         registryFriendlyByteBuf.writeBoolean(this.grace);
         registryFriendlyByteBuf.writeBoolean(this.warmProtection);
      }

      static BodyStatusSyncPacket.Temperature read(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
         return new BodyStatusSyncPacket.Temperature(
            registryFriendlyByteBuf.readBoolean(),
            registryFriendlyByteBuf.readFloat(),
            registryFriendlyByteBuf.readFloat(),
            registryFriendlyByteBuf.readByte(),
            registryFriendlyByteBuf.readByte(),
            registryFriendlyByteBuf.readFloat(),
            registryFriendlyByteBuf.readFloat(),
            registryFriendlyByteBuf.readBoolean(),
            registryFriendlyByteBuf.readBoolean(),
            registryFriendlyByteBuf.readBoolean()
         );
      }
   }
}
