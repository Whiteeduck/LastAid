package com.duckerquacker.lastaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.duckerquacker.lastaid.client.medical.LimbRenderHelper;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No armor piece is drawn on an amputated limb. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
   @Unique
   private LivingEntity lastaid$entity;

   @Inject(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
      at = @At("HEAD")
   )
   private void lastaid$rememberEntity(
      PoseStack poseStack, MultiBufferSource buffer, int light, LivingEntity entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci
   ) {
      this.lastaid$entity = entity;
   }

   @Inject(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
      at = @At("RETURN")
   )
   private void lastaid$forgetEntity(
      PoseStack poseStack, MultiBufferSource buffer, int light, LivingEntity entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci
   ) {
      this.lastaid$entity = null;
   }

   @Inject(method = "setPartVisibility(Lnet/minecraft/client/model/HumanoidModel;Lnet/minecraft/world/entity/EquipmentSlot;)V", at = @At("TAIL"))
   private void lastaid$hideAmputated(HumanoidModel<?> model, EquipmentSlot slot, CallbackInfo ci) {
      LimbRenderHelper.hideArmor(model, this.lastaid$entity);
   }
}
