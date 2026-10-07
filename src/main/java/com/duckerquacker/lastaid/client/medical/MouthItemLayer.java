package com.duckerquacker.lastaid.client.medical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Purely visual: when both arms of a player are useless (amputated or gangrene) the held item is drawn in front of
 * the mouth, as if held in the teeth. Everyone sees it (the limb state is synced to all trackers).
 * Mechanics do not change: the item is still the one in the hand slot.
 */
public class MouthItemLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private final ItemInHandRenderer itemInHandRenderer;

   public MouthItemLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
      super(parent);
      this.itemInHandRenderer = itemInHandRenderer;
   }

   @Override
   public void render(
      PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
      float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      if (player.isInvisible() || !LimbRenderHelper.mouthMode(player)) {
         return;
      }
      ItemStack stack = LimbRenderHelper.mouthItem(player);
      if (stack.isEmpty()) {
         return;
      }
      poseStack.pushPose();
      this.getParentModel().head.translateAndRotate(poseStack);
      // head space: y grows downwards, the face looks to -z. Mouth is ~2px above the chin.
      float chew = player.isUsingItem() ? (float)Math.sin((player.tickCount + partialTick) * 1.4F) * 0.012F : 0.0F;
      poseStack.translate(0.0F, -0.11F + chew, -0.29F);
      // lay the item across the mouth, flat side to the front
      poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
      poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
      poseStack.scale(0.3F, 0.3F, 0.3F);
      this.itemInHandRenderer.renderItem(player, stack, ItemDisplayContext.FIXED, false, poseStack, buffer, packedLight);
      poseStack.popPose();
   }
}
