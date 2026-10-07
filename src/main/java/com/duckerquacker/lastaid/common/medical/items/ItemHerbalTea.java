package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import com.duckerquacker.lastaid.common.medical.TemperatureRegulator;
import com.duckerquacker.lastaid.common.medical.cold.CommonCold;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import com.duckerquacker.lastaid.common.util.CommonUtils;
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

public class ItemHerbalTea extends ItemTooltip {
   public ItemHerbalTea(Properties properties) {
      super(properties, "item.lastaid.herbal_tea.desc");
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
      return ItemUtils.startUsingInstantly(level, player, interactionHand);
   }

   public UseAnim getUseAnimation(ItemStack itemStack) {
      return UseAnim.DRINK;
   }

   public int getUseDuration(ItemStack itemStack, LivingEntity livingEntity) {
      return 60;
   }

   public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity livingEntity) {
      if (livingEntity instanceof ServerPlayer serverPlayer) {
         MedicalConfig.Items items = MedicalConfig.SERVER.items;
         AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer);
         if (abstractPlayerDamageModel != null && !MedicalHooks.blocksPassiveHeadRegen(serverPlayer)) {
            AbstractDamageablePart abstractDamageablePart = abstractPlayerDamageModel.getFromEnum(EnumPlayerPart.HEAD);
            if (abstractDamageablePart.currentHealth > 0.0F) {
               abstractDamageablePart.heal(((Double)items.teaHeadHeal.get()).floatValue(), serverPlayer, true);
               CommonUtils.syncDamageModel(serverPlayer);
            }
         }

         serverPlayer.addEffect(new MobEffectInstance(MedicalRegistry.CALM, (Integer)items.calmSeconds.get() * 20, 0));
         Concussion.State state = Concussion.INSTANCE.state(serverPlayer);
         if (state.severity > 0) {
            Concussion.INSTANCE.treat(serverPlayer, state, ((Double)items.teaProgress.get()).floatValue());
         }

         TemperatureRegulator.warmInstantly(serverPlayer, (Double)items.teaWarmthC.get());
         if ((Integer)items.warmProtectionSeconds.get() > 0) {
            serverPlayer.removeEffect(MedicalRegistry.WARM_PROTECTION);
            serverPlayer.addEffect(new MobEffectInstance(MedicalRegistry.WARM_PROTECTION, (Integer)items.warmProtectionSeconds.get() * 20, 0));
         }

         CommonCold.INSTANCE.delaySneeze(serverPlayer);
      }

      return livingEntity instanceof Player player && player.getAbilities().instabuild
         ? itemStack
         : ItemUtils.createFilledResult(itemStack, livingEntity instanceof Player player1 ? player1 : null, new ItemStack(Items.GLASS_BOTTLE));
   }
}
