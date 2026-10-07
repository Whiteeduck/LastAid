package com.duckerquacker.lastaid.common.medical.cold;

import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags.Items;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

public final class FrostbiteEvents {
   private FrostbiteEvents() {
   }

   private static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && (Boolean)MedicalConfig.SERVER.enabled.get() && (Boolean)MedicalConfig.SERVER.frostbite.enabled.get();
   }

   public static boolean needsWorkingArm(ItemStack itemStack) {
      return itemStack.isEmpty()
         ? false
         : itemStack.getItem() instanceof TieredItem
            || itemStack.getItem() instanceof BowItem
            || itemStack.getItem() instanceof CrossbowItem
            || itemStack.getItem() instanceof ShieldItem
            || itemStack.getItem() instanceof TridentItem
            || itemStack.getItem() instanceof MaceItem
            || itemStack.is(Items.TOOLS);
   }

   private static void refuse(Player player) {
      player.displayClientMessage(Component.translatable("lastaid.frostbite.arm_useless").withStyle(ChatFormatting.DARK_RED), true);
   }

   @SubscribeEvent
   public static void onAttack(AttackEntityEvent attackEntityEvent) {
      if (attackEntityEvent.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         if (com.duckerquacker.lastaid.common.medical.bone.Bones.armUseless(serverPlayer, InteractionHand.MAIN_HAND)) {
            attackEntityEvent.setCanceled(true);
            refuse(serverPlayer);
         }
      }
   }

   @SubscribeEvent
   public static void onLeftClickBlock(LeftClickBlock leftClickBlock) {
      if (leftClickBlock.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         if (com.duckerquacker.lastaid.common.medical.bone.Bones.armUseless(serverPlayer, InteractionHand.MAIN_HAND) && needsWorkingArm(serverPlayer.getMainHandItem())) {
            leftClickBlock.setCanceled(true);
            refuse(serverPlayer);
         }
      }
   }

   @SubscribeEvent
   public static void onBreakSpeed(BreakSpeed breakSpeed) {
      Player player = breakSpeed.getEntity();
      if (!player.level().isClientSide() && enabled()) {
         if (com.duckerquacker.lastaid.common.medical.bone.Bones.armUseless(player, InteractionHand.MAIN_HAND) && needsWorkingArm(player.getMainHandItem())) {
            breakSpeed.setNewSpeed(0.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onRightClickItem(RightClickItem rightClickItem) {
      if (rightClickItem.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         ItemStack itemStack = rightClickItem.getItemStack();
         if (needsWorkingArm(itemStack)) {
            boolean bl = itemStack.getItem() instanceof BowItem || itemStack.getItem() instanceof CrossbowItem;
            boolean bl1 = com.duckerquacker.lastaid.common.medical.bone.Bones.armUseless(serverPlayer, rightClickItem.getHand())
               || bl
                  && Frostbite.INSTANCE
                     .isArmDead(serverPlayer, rightClickItem.getHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (bl1) {
               rightClickItem.setCanceled(true);
               refuse(serverPlayer);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onShieldBlock(LivingShieldBlockEvent livingShieldBlockEvent) {
      if (livingShieldBlockEvent.getEntity() instanceof ServerPlayer serverPlayer && enabled() && livingShieldBlockEvent.getBlocked()) {
         InteractionHand interactionHand = serverPlayer.isUsingItem() ? serverPlayer.getUsedItemHand() : InteractionHand.OFF_HAND;
         if (Frostbite.INSTANCE.armStage(serverPlayer, interactionHand) >= 3) {
            livingShieldBlockEvent.setBlocked(false);
         }
      }
   }

   @SubscribeEvent
   public static void onArrowSpawn(EntityJoinLevelEvent entityJoinLevelEvent) {
      if (!entityJoinLevelEvent.getLevel().isClientSide() && enabled() && !entityJoinLevelEvent.loadedFromDisk()) {
         if (entityJoinLevelEvent.getEntity() instanceof AbstractArrow abstractArrow && abstractArrow.getOwner() instanceof ServerPlayer serverPlayer) {
            int i = Math.max(
               Frostbite.INSTANCE.armStage(serverPlayer, InteractionHand.MAIN_HAND), Frostbite.INSTANCE.armStage(serverPlayer, InteractionHand.OFF_HAND)
            );

            double d = switch (i) {
               case 2 -> MedicalConfig.SERVER.frostbite.bowInaccuracyStage2.get();
               case 3, 4 -> MedicalConfig.SERVER.frostbite.bowInaccuracyStage3.get();
               default -> 0.0;
            };
            if (!(d <= 0.0)) {
               Vec3 vec3 = abstractArrow.getDeltaMovement();
               double e = vec3.length();
               RandomSource randomSource = serverPlayer.getRandom();
               double f = 0.0172275 * d;
               Vec3 vec31 = vec3.normalize().add(randomSource.triangle(0.0, f), randomSource.triangle(0.0, f), randomSource.triangle(0.0, f));
               abstractArrow.setDeltaMovement(vec31.normalize().scale(e));
            }
         }
      }
   }
}
