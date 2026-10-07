package com.duckerquacker.lastaid.client.medical;

import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Descriptions for the original First Aid items that have no tooltip of their own (bandage, plaster, rag, defibrillator). */
public final class ItemTooltips {
   private ItemTooltips() {
   }

   @SubscribeEvent
   public static void onTooltip(ItemTooltipEvent event) {
      Item item = event.getItemStack().getItem();
      if (item == RegistryObjects.BANDAGE.get() || item == RegistryObjects.PLASTER.get() || item == MedicalRegistry.RAG.get()
         || item == RegistryObjects.DEFIBRILLATOR.get()) {
         String key = "item.lastaid." + BuiltInRegistries.ITEM.getKey(item).getPath() + ".desc";
         event.getToolTip().add(1, Component.translatable(key).withStyle(ChatFormatting.GRAY));
      }
   }
}
