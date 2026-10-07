package com.duckerquacker.lastaid.common.medical.items;

import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/** A rag that caused an infection: can't be washed, only burned (works as furnace fuel). */
public class ItemInfectedRag extends ItemTooltip {
   public ItemInfectedRag(Properties properties, String descriptionKey) {
      super(properties, descriptionKey);
   }

   @Override
   public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
      return 100;
   }
}
