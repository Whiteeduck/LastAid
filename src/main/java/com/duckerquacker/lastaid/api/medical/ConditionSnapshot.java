package com.duckerquacker.lastaid.api.medical;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

public record ConditionSnapshot(
   ResourceLocation id,
   Component name,
   int color,
   int severity,
   int partsMask,
   float progress,
   boolean severe,
   List<Component> tooltipLines,
   List<Component> detailLines,
   int clientFlags,
   @Nullable ResourceLocation icon,
   int partTint,
   int displayFlags
) {
   public static final int FLAG_RISK = 1;
   public static final int FLAG_PART_DEAD = 2;
   public static final int FLAG_PART_MISSING = 4;

   public ConditionSnapshot(
      ResourceLocation resourceLocation, Component component, int i, int j, int k, float f, boolean bl, List<Component> list, List<Component> list1, int l
   ) {
      this(resourceLocation, component, i, j, k, f, bl, list, list1, l, null, 0, 0);
   }

   public boolean affects(EnumPlayerPart enumPlayerPart) {
      return (this.partsMask & 1 << enumPlayerPart.ordinal()) != 0;
   }

   public boolean hasFlag(int i) {
      return (this.displayFlags & i) != 0;
   }

   public boolean isRisk() {
      return this.hasFlag(1);
   }

   public EnumSet<EnumPlayerPart> parts() {
      EnumSet enumSet = EnumSet.noneOf(EnumPlayerPart.class);

      for (EnumPlayerPart enumPlayerPart : EnumPlayerPart.VALUES) {
         if (this.affects(enumPlayerPart)) {
            enumSet.add(enumPlayerPart);
         }
      }

      return enumSet;
   }

   public static int mask(EnumPlayerPart... enumPlayerParts) {
      int i = 0;

      for (EnumPlayerPart enumPlayerPart : enumPlayerParts) {
         i |= 1 << enumPlayerPart.ordinal();
      }

      return i;
   }

   public void write(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
      registryFriendlyByteBuf.writeResourceLocation(this.id);
      ComponentSerialization.TRUSTED_STREAM_CODEC.encode(registryFriendlyByteBuf, this.name);
      registryFriendlyByteBuf.writeInt(this.color);
      registryFriendlyByteBuf.writeVarInt(this.severity);
      registryFriendlyByteBuf.writeVarInt(this.partsMask);
      registryFriendlyByteBuf.writeFloat(this.progress);
      registryFriendlyByteBuf.writeBoolean(this.severe);
      writeComponents(registryFriendlyByteBuf, this.tooltipLines);
      writeComponents(registryFriendlyByteBuf, this.detailLines);
      registryFriendlyByteBuf.writeVarInt(this.clientFlags);
      registryFriendlyByteBuf.writeBoolean(this.icon != null);
      if (this.icon != null) {
         registryFriendlyByteBuf.writeResourceLocation(this.icon);
      }

      registryFriendlyByteBuf.writeInt(this.partTint);
      registryFriendlyByteBuf.writeVarInt(this.displayFlags);
   }

   public static ConditionSnapshot read(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
      return new ConditionSnapshot(
         registryFriendlyByteBuf.readResourceLocation(),
         (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(registryFriendlyByteBuf),
         registryFriendlyByteBuf.readInt(),
         registryFriendlyByteBuf.readVarInt(),
         registryFriendlyByteBuf.readVarInt(),
         registryFriendlyByteBuf.readFloat(),
         registryFriendlyByteBuf.readBoolean(),
         readComponents(registryFriendlyByteBuf),
         readComponents(registryFriendlyByteBuf),
         registryFriendlyByteBuf.readVarInt(),
         registryFriendlyByteBuf.readBoolean() ? registryFriendlyByteBuf.readResourceLocation() : null,
         registryFriendlyByteBuf.readInt(),
         registryFriendlyByteBuf.readVarInt()
      );
   }

   private static void writeComponents(RegistryFriendlyByteBuf registryFriendlyByteBuf, List<Component> list) {
      registryFriendlyByteBuf.writeVarInt(list.size());

      for (Component component : list) {
         ComponentSerialization.TRUSTED_STREAM_CODEC.encode(registryFriendlyByteBuf, component);
      }
   }

   private static List<Component> readComponents(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
      int i = registryFriendlyByteBuf.readVarInt();
      ArrayList arrayList = new ArrayList(i);

      for (int j = 0; j < i; j++) {
         arrayList.add((Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(registryFriendlyByteBuf));
      }

      return arrayList;
   }
}
