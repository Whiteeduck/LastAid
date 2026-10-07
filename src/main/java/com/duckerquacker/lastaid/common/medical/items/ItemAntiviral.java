package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import com.duckerquacker.lastaid.common.medical.cold.CommonCold;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ItemAntiviral extends ItemTooltip {
   public ItemAntiviral(Properties properties) {
      super(properties, "item.lastaid.antiviral.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         CommonCold.State state = CommonCold.INSTANCE.state(serverPlayer);
         long l = level.getGameTime() - state.lastAntiviralTick;
         long m = ((Integer)MedicalConfig.SERVER.commonCold.antiviralDoseTicks.get()).intValue();
         if (l >= 0L && l < m) {
            player.displayClientMessage(
               Component.translatable("lastaid.antiviral.too_early", new Object[]{CommonCold.formatTicks(m - l)}).withStyle(ChatFormatting.YELLOW), true
            );
            return InteractionResultHolder.fail(player.getItemInHand(interactionHand));
         }
      }

      return ItemUtils.startUsingInstantly(level, player, interactionHand);
   }

   public UseAnim getUseAnimation(ItemStack itemStack) {
      return UseAnim.DRINK;
   }

   public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
      return 32;
   }

   public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
      if (livingEntity instanceof ServerPlayer serverPlayer) {
         int i = (Integer)MedicalConfig.SERVER.commonCold.antiviralDoseTicks.get();
         serverPlayer.addEffect(new MobEffectInstance(MedicalRegistry.ANTIVIRAL_EFFECT, i, 0));
         CommonCold.State state = CommonCold.INSTANCE.state(serverPlayer);
         state.lastAntiviralTick = level.getGameTime();
         state.markDirty();
         serverPlayer.displayClientMessage(
            Component.translatable("lastaid.antiviral.taken", new Object[]{itemStack.getMaxDamage() - itemStack.getDamageValue() - 1})
               .withStyle(ChatFormatting.GREEN),
            true
         );
         if (!serverPlayer.getAbilities().instabuild) {
            itemStack.hurtAndBreak(
               1, serverPlayer, livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND
            );
         }
      }

      return itemStack;
   }
}
