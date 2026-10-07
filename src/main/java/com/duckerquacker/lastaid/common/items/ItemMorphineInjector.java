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

public class ItemMorphineInjector extends ItemMedicine {
   private static final ResourceLocation STATUS_ID = ResourceLocation.fromNamespaceAndPath("lastaid", "morphine");

   public ItemMorphineInjector(Properties properties) {
      super(properties.stacksTo(1).durability(2));
   }

   @Override
   public void applyMedicine(MedicineUseContext context) {
      context.applyMorphineInjection();
   }

   @Nonnull
   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return UseAnim.NONE;
   }

   @Override
   public SoundEvent getUseStartSound(ItemStack stack) {
      return RegistryObjects.ADRENALINE_INJECTOR_USE.value();
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return FirstAidConfig.SERVER.morphineInjectorUseDuration.get();
   }

   @Override
   public MedicineStatusDisplay getActiveStatus(MedicineStatusContext context) {
      int morphineTicks = context.getDamageModel() == null ? 0 : context.getDamageModel().getMorphineTicks();
      return morphineTicks >= 20
         ? new MedicineStatusDisplay(
            STATUS_ID,
            Component.translatable("lastaid.gui.morphine_left", StringUtil.formatTickDuration(morphineTicks, 20.0F)),
            null,
            0xC8A2C8
         )
         : null;
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
      tooltipComponents.add(
         Component.translatable(
               "lastaid.tooltip.morphine",
               StringUtil.formatTickDuration(FirstAidConfig.SERVER.morphineInjectorUseDuration.get(), 20.0F),
               "15:56-18:04",
               StringUtil.formatTickDuration(PlayerDamageModel.MORPHINE_INJECTOR_REGEN_TICKS, 20.0F)
            )
            .withStyle(ChatFormatting.GRAY)
      );
   }
}
