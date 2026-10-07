package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class ItemBarkBroth extends ItemTooltip {
   public ItemBarkBroth(Properties properties) {
      super(properties, "item.lastaid.bark_broth.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      return ItemUtils.startUsingInstantly(level, player, interactionHand);
   }

   public UseAnim getUseAnimation(ItemStack itemStack) {
      return UseAnim.DRINK;
   }

   public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
      return 40;
   }

   public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
      if (livingEntity instanceof ServerPlayer serverPlayer) {
         serverPlayer.addEffect(new MobEffectInstance(MedicalRegistry.HEAD_REGEN, (Integer)MedicalConfig.SERVER.items.brothSeconds.get() * 20 + 1, 0));
      }

      return livingEntity instanceof Player player && player.getAbilities().instabuild
         ? itemStack
         : ItemUtils.createFilledResult(itemStack, livingEntity instanceof Player player1 ? player1 : null, new ItemStack(Items.GLASS_BOTTLE));
   }
}
