package com.duckerquacker.lastaid.common.medical.items;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A used rag / bandage. Right click water (any water block) to wash the whole stack, or right click anywhere with a
 * water bottle in the inventory to wash up to 4 (the bottle is used up).
 */
public class ItemDirtyDressing extends ItemTooltip {
   private static final int PER_BOTTLE = 4;
   private final Supplier<? extends Item> clean;

   public ItemDirtyDressing(Properties properties, Supplier<? extends Item> clean, String descriptionKey) {
      super(properties, descriptionKey);
      this.clean = clean;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
      boolean water = hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER);
      int amount;
      if (water) {
         amount = stack.getCount();
      } else {
         ItemStack bottle = findWaterBottle(player);
         if (bottle.isEmpty()) {
            if (!level.isClientSide()) {
               player.displayClientMessage(Component.translatable("lastaid.dressing.wash_need_water").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
         }
         amount = Math.min(PER_BOTTLE, stack.getCount());
         if (!level.isClientSide() && !player.getAbilities().instabuild) {
            bottle.shrink(1);
            ItemStack glass = new ItemStack(Items.GLASS_BOTTLE);
            if (!player.getInventory().add(glass)) {
               player.drop(glass, false);
            }
         }
      }
      if (!level.isClientSide()) {
         BlockPos pos = water ? hit.getBlockPos() : player.blockPosition();
         level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.6F, 1.4F);
         ItemStack washed = new ItemStack(this.clean.get(), amount);
         stack.shrink(amount);
         player.displayClientMessage(Component.translatable("lastaid.dressing.washed", amount).withStyle(ChatFormatting.AQUA), true);
         if (stack.isEmpty()) {
            return InteractionResultHolder.success(washed);
         }
         if (!player.getInventory().add(washed)) {
            player.drop(washed, false);
         }
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
   }

   private static ItemStack findWaterBottle(Player player) {
      for (ItemStack stack : player.getInventory().items) {
         if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents != null && contents.is(Potions.WATER)) {
               return stack;
            }
         }
      }
      return ItemStack.EMPTY;
   }
}
