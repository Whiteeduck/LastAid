package com.duckerquacker.lastaid.common.medical.items;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public class ItemTooltip extends Item {
   private final String descriptionKey;

   public ItemTooltip(Properties properties, String string) {
      super(properties);
      this.descriptionKey = string;
   }

   public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list, TooltipFlag tooltipFlag) {
      list.add(Component.translatable(this.descriptionKey).withStyle(ChatFormatting.GRAY));
      super.appendHoverText(itemStack, tooltipContext, list, tooltipFlag);
   }
}
