package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalProcedures;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ItemSurgicalKnife extends ItemTooltip {
   public ItemSurgicalKnife(Properties properties) {
      super(properties, "item.lastaid.surgical_knife.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         MedicalProcedures.startAmputation(serverPlayer, serverPlayer, interactionHand);
      }

      return InteractionResultHolder.consume(player.getItemInHand(interactionHand));
   }

   public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
      if (!(livingEntity instanceof Player)) {
         return InteractionResult.PASS;
      } else if (player.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      } else {
         ServerPlayer serverPlayer = MedicalItemHelper.asTarget(player, livingEntity);
         if (serverPlayer != null) {
            MedicalProcedures.startAmputation((ServerPlayer)player, serverPlayer, interactionHand);
         }

         return InteractionResult.CONSUME;
      }
   }
}
