package com.duckerquacker.lastaid.common.items;

import com.duckerquacker.lastaid.FirstAidConfig;
import com.duckerquacker.lastaid.api.medicine.ItemMedicine;
import com.duckerquacker.lastaid.api.medicine.MedicineStatusContext;
import com.duckerquacker.lastaid.api.medicine.MedicineStatusDisplay;
import com.duckerquacker.lastaid.api.medicine.MedicineUseContext;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;

public class ItemAdrenalineInjector extends ItemMedicine {
   private static final ResourceLocation STATUS_ID = ResourceLocation.fromNamespaceAndPath("lastaid", "adrenaline");

   public ItemAdrenalineInjector(Properties properties) {
      super(properties.stacksTo(1).durability(4));
   }

   @Override
   public void applyMedicine(MedicineUseContext context) {
      context.applyAdrenalineInjection();
   }

   @Nonnull
   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.NONE;
   }

   @Override
   public SoundEvent getUseStartSound(ItemStack stack) {
      return (SoundEvent)RegistryObjects.ADRENALINE_INJECTOR_USE.value();
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return (Integer)FirstAidConfig.SERVER.adrenalineUseDuration.get();
   }

   @Override
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      int adrenalineLevel = context.getDamageModel() == null ? 0 : context.getDamageModel().getAdrenalineLevel();
      return adrenalineLevel > 0
         ? new MedicineStatusDisplay(
            STATUS_ID,
            Component.translatable("lastaid.gui.status.adrenaline", new Object[]{Component.translatable(getAdrenalineSeverityKey(adrenalineLevel))}),
            null,
            12637930
         )
         : null;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      tooltipComponents.add(
         Component.translatable(
               "lastaid.tooltip.adrenaline_injector",
               new Object[]{StringUtil.formatTickDuration(40, 20.0F), StringUtil.formatTickDuration(PlayerDamageModel.getAdrenalineDuration(), 20.0F)}
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }

   private static String getAdrenalineSeverityKey(int adrenalineLevel) {
      return switch (adrenalineLevel) {
         case 1 -> "lastaid.gui.adrenaline.low";
         case 2 -> "lastaid.gui.adrenaline.medium";
         default -> "lastaid.gui.adrenaline.high";
      };
   }
}
