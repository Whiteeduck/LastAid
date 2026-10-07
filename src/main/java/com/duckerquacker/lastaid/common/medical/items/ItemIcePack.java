package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import com.duckerquacker.lastaid.common.medical.compat.TemperatureCompat;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ItemIcePack extends ItemTooltip {
   public ItemIcePack(Properties properties) {
      super(properties, "item.lastaid.ice_pack.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      return ItemUtils.startUsingInstantly(level, player, interactionHand);
   }

   public UseAnim getUseAnimation(ItemStack itemStack) {
      return UseAnim.BOW;
   }

   public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
      return 30;
   }

   public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
      if (livingEntity instanceof ServerPlayer serverPlayer) {
         this.apply(itemStack, serverPlayer, serverPlayer, serverPlayer.getUsedItemHand());
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
            this.apply(itemStack, (ServerPlayer)player, serverPlayer, interactionHand);
         }

         return InteractionResult.CONSUME;
      }
   }

   private void apply(ItemStack itemStack, ServerPlayer serverPlayer, ServerPlayer serverPlayer1, InteractionHand interactionHand) {
      Concussion.State state = Concussion.INSTANCE.state(serverPlayer1);
      if (state.severity <= 0) {
         serverPlayer.displayClientMessage(Component.translatable("lastaid.medical.not_needed").withStyle(ChatFormatting.GRAY), true);
      } else if (!MedicalItemHelper.needsCompanion(serverPlayer, serverPlayer1)) {
         MedicalConfig.Items items = MedicalConfig.SERVER.items;
         Concussion.INSTANCE.treat(serverPlayer1, state, ((Double)items.icePackProgress.get()).floatValue());
         serverPlayer1.removeEffect(MobEffects.CONFUSION);
         boolean bl = serverPlayer1.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
            || serverPlayer1.getItemBySlot(EquipmentSlot.HEAD).is((Item)MedicalRegistry.HEAD_BANDAGE.get());
         TemperatureCompat temperatureCompat = TemperatureCompat.get();
         if (bl && temperatureCompat.active()) {
            temperatureCompat.addCoreUnits(serverPlayer1, -(Double)items.icePackColdUnits.get());
         }

         serverPlayer1.level().playSound(null, serverPlayer1.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
         serverPlayer.getCooldowns().addCooldown(this, (Integer)items.icePackCooldownSeconds.get() * 20);
         itemStack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(interactionHand));
         serverPlayer1.displayClientMessage(Component.translatable("lastaid.ice_pack.applied").withStyle(ChatFormatting.AQUA), true);
      }
   }
}
