package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ItemNeckBrace extends ItemTooltip {
   public ItemNeckBrace(Properties properties) {
      super(properties, "item.lastaid.neck_brace.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      return ItemUtils.startUsingInstantly(level, player, interactionHand);
   }

   public UseAnim getUseAnimation(ItemStack itemStack) {
      return UseAnim.BOW;
   }

   public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
      return 40;
   }

   public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
      if (livingEntity instanceof ServerPlayer serverPlayer) {
         apply(itemStack, serverPlayer, serverPlayer);
      }

      return itemStack;
   }

   public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity, InteractionHand interactionHand) {
      if (!(livingEntity instanceof Player)) {
         return InteractionResult.PASS;
      } else if (player.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      } else {
         ServerPlayer serverPlayer = MedicalItemHelper.asTarget(player, livingEntity);
         if (serverPlayer != null) {
            apply(player.getItemInHand(interactionHand), (ServerPlayer)player, serverPlayer);
         }

         return InteractionResult.CONSUME;
      }
   }

   private static void apply(ItemStack itemStack, ServerPlayer serverPlayer, ServerPlayer serverPlayer1) {
      int i = (Integer)MedicalConfig.SERVER.items.neckBraceMinutes.get() * 60 * 20;
      serverPlayer1.addEffect(new MobEffectInstance(MedicalRegistry.NECK_BRACED, i, 0));
      serverPlayer1.level()
         .playSound(null, serverPlayer1.blockPosition(), (SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
      serverPlayer1.displayClientMessage(Component.translatable("lastaid.neck_brace.applied").withStyle(ChatFormatting.AQUA), true);
      if (serverPlayer != serverPlayer1) {
         serverPlayer.displayClientMessage(
            Component.translatable("lastaid.neck_brace.applied_other", new Object[]{serverPlayer1.getDisplayName()}).withStyle(ChatFormatting.AQUA), true
         );
      }

      itemStack.consume(1, serverPlayer);
   }
}
