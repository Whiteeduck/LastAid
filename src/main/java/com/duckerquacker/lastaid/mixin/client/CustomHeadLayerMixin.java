package com.duckerquacker.lastaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.duckerquacker.lastaid.common.medical.HeadBandage;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The head bandage is drawn as gauze by HeadBandageLayer, not as an item model on the head. */
@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {
   @Inject(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void lastaid$skipHeadBandage(
      PoseStack poseStack, MultiBufferSource buffer, int light, LivingEntity entity,
      float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci
   ) {
      if (HeadBandage.isBandage(entity.getItemBySlot(EquipmentSlot.HEAD))) {
         ci.cancel();
      }
   }
}
