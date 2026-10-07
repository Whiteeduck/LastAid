package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.infection.Infection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Antibiotic pill: -1 infection stage and no worsening for a game day; only one per day counts. */
public class ItemAntibiotic extends ItemTooltip {
   public ItemAntibiotic(Properties properties, String descriptionKey) {
      super(properties, descriptionKey);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      return ItemUtils.startUsingInstantly(level, player, hand);
   }

   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.EAT;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 24;
   }

   @Override
   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
      if (entity instanceof ServerPlayer player && Infection.INSTANCE.takeAntibiotic(player)) {
         level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.3F);
         if (!player.getAbilities().instabuild) {
            stack.shrink(1);
         }
      }
      return stack;
   }
}
