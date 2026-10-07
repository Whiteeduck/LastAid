package com.duckerquacker.lastaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.duckerquacker.lastaid.client.medical.LimbRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No held item on an amputated arm (third person). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
   @Inject(
      method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void lastaid$skipAmputatedArm(
      LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int light, CallbackInfo ci
   ) {
      // amputated arm: nothing to hold it; both arms useless: the item is drawn in the mouth instead (MouthItemLayer)
      if (LimbRenderHelper.amputated(entity, LimbRenderHelper.armLimb(arm)) || LimbRenderHelper.mouthMode(entity)) {
         ci.cancel();
      }
   }
}
