package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.MedicalProcedures;
import com.duckerquacker.lastaid.common.medical.bone.Bones;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
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

/**
 * Splint: right click opens the body panel, click the broken limb, stand still for 5 s.
 * Right click another player: their worst unsplinted fracture (legs first).
 */
public class ItemSplint extends ItemTooltip {
   public ItemSplint(Properties properties) {
      super(properties, "item.lastaid.splint.desc");
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (level.isClientSide()) {
         com.duckerquacker.lastaid.client.medical.ClientBones.openSplintPanel(hand);
      }
      return InteractionResultHolder.consume(player.getItemInHand(hand));
   }

   @Override
   public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
      if (!(target instanceof Player)) {
         return InteractionResult.PASS;
      }
      if (player.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      }
      ServerPlayer patient = MedicalItemHelper.asTarget(player, target);
      if (patient != null && player instanceof ServerPlayer medic && !MedicalProcedures.isBusy(medic)) {
         Frostbite.Limb limb = Bones.splintTarget(patient);
         if (limb == null) {
            player.displayClientMessage(Component.translatable("lastaid.splint.not_needed_other", patient.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
         } else {
            MedicalProcedures.startSplint(medic, patient, hand, limb);
         }
      }
      return InteractionResult.CONSUME;
   }
}
