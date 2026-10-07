package com.duckerquacker.lastaid.common.items;

import com.duckerquacker.lastaid.FirstAidConfig;
import com.duckerquacker.lastaid.api.medicine.ItemMedicine;
import com.duckerquacker.lastaid.api.medicine.MedicineStatusContext;
import com.duckerquacker.lastaid.api.medicine.MedicineStatusDisplay;
import com.duckerquacker.lastaid.api.medicine.MedicineUseContext;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;

public class ItemMorphine extends ItemMedicine {
   private static final ResourceLocation STATUS_ID = ResourceLocation.fromNamespaceAndPath("lastaid", "morphine");

   public ItemMorphine(Properties properties) {
      super(properties.stacksTo(16));
   }

   @Override
   public void applyMedicine(MedicineUseContext context) {
      context.queueMorphineActivation();
   }

   @Override
   public SoundEvent getUseStartSound(ItemStack stack) {
      // Play once on start; looping the full pill clip caused repeated playback mid-use.
      return RegistryObjects.PILLS_USE.value();
   }

   @Override
   public SoundEvent getUseLoopSound(ItemStack stack) {
      return null;
   }

   @Override
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      int morphineTicks = context.getDamageModel() == null ? 0 : context.getDamageModel().getMorphineTicks();
      return morphineTicks >= 20
         ? new MedicineStatusDisplay(
            STATUS_ID, Component.translatable("lastaid.gui.morphine_left", new Object[]{StringUtil.formatTickDuration(morphineTicks, 20.0F)}), null, 16777215
         )
         : null;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return (Integer)FirstAidConfig.SERVER.morphineUseDuration.get();
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      tooltipComponents.add(
         Component.translatable(
               "lastaid.tooltip.morphine", new Object[]{StringUtil.formatTickDuration(PlayerDamageModel.getMorphineActivationDelay(), 20.0F), "7:30-8:30"}
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }
}
