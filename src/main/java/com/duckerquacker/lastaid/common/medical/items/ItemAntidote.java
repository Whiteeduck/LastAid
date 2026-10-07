package com.duckerquacker.lastaid.common.medical.items;

import com.duckerquacker.lastaid.common.medical.overdose.Overdose;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

/**
 * Naloxone (morphine) and beta blocker (adrenaline): empties that overdose pool at once.
 * Right click: yourself (1 s). Right click a player within reach: that player, instantly.
 */
public class ItemAntidote extends ItemTooltip {
   private final Overdose.Drug drug;

   public ItemAntidote(Properties properties, Overdose.Drug drug, String descriptionKey) {
      super(properties, descriptionKey);
      this.drug = drug;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && Overdose.INSTANCE.state(serverPlayer).pool(this.drug) <= 0.0F) {
         player.displayClientMessage(Component.translatable("lastaid.antidote.not_needed").withStyle(ChatFormatting.GRAY), true);
         return InteractionResultHolder.fail(player.getItemInHand(hand));
      }
      return ItemUtils.startUsingInstantly(level, player, hand);
   }

   @Override
   public UseAnim getUseAnimation(ItemStack stack) {
      return this.drug == Overdose.Drug.MORPHINE ? UseAnim.NONE : UseAnim.EAT;
   }

   @Override
   public int getUseDuration(ItemStack stack, LivingEntity entity) {
      return 20;
   }

   @Override
   public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
      if (entity instanceof ServerPlayer serverPlayer) {
         this.apply(serverPlayer, serverPlayer, stack);
      }
      return stack;
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
      if (patient != null && player instanceof ServerPlayer medic) {
         if (Overdose.INSTANCE.state(patient).pool(this.drug) <= 0.0F) {
            player.displayClientMessage(Component.translatable("lastaid.antidote.not_needed_other", patient.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
         } else {
            this.apply(medic, patient, player.getItemInHand(hand));
         }
      }
      return InteractionResult.CONSUME;
   }

   private void apply(ServerPlayer medic, ServerPlayer patient, ItemStack stack) {
      if (!Overdose.applyAntidote(patient, this.drug)) {
         return;
      }
      patient.level().playSound(null, patient.blockPosition(), this.drug == Overdose.Drug.MORPHINE ? SoundEvents.BOTTLE_EMPTY : SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 1.3F);
      patient.displayClientMessage(Component.translatable("lastaid.antidote.used." + (this.drug == Overdose.Drug.MORPHINE ? "morphine" : "adrenaline")).withStyle(ChatFormatting.GREEN), true);
      if (medic != patient) {
         medic.displayClientMessage(Component.translatable("lastaid.antidote.used_other", patient.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
      }
      if (!medic.getAbilities().instabuild) {
         stack.shrink(1);
      }
   }
}
