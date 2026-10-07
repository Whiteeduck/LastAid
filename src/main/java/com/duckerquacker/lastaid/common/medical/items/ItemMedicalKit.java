package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalProcedures;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ItemMedicalKit extends ItemTooltip {
   public ItemMedicalKit(Properties properties) {
      super(properties, "item.lastaid.medical_kit.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      if (!level.isClientSide()) {
         player.displayClientMessage(Component.translatable("lastaid.medical_kit.not_self").withStyle(ChatFormatting.RED), true);
      }

      return InteractionResultHolder.fail(player.getItemInHand(interactionHand));
   }

   public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
      if (!(livingEntity instanceof Player)) {
         return InteractionResult.PASS;
      } else if (player.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      } else {
         ServerPlayer serverPlayer = MedicalItemHelper.asTarget(player, livingEntity);
         if (serverPlayer != null) {
            MedicalProcedures.start((ServerPlayer)player, serverPlayer, interactionHand);
         }

         return InteractionResult.CONSUME;
      }
   }
}
