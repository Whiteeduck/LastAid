package com.duckerquacker.lastaid.client.medical;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.duckerquacker.lastaid.common.medical.HeadBandage;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Head bandage as real gauze wrapped around the head (instead of the item model sitting on the head):
 * light = one strip over the forehead and the back of the head, full = plus a cross over the top.
 * Blood stains are a second, fading texture. First person: a thin strip at the top of the screen.
 */
public class HeadBandageLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   private static final ResourceLocation GAUZE = ResourceLocation.fromNamespaceAndPath("lastaid", "textures/models/head_bandage.png");
   private static final ResourceLocation BLOOD = ResourceLocation.fromNamespaceAndPath("lastaid", "textures/models/head_bandage_blood.png");
   private static ModelPart light;
   private static ModelPart cross;

   public HeadBandageLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   private static void bake() {
      if (light != null) {
         return;
      }
      // head box: x -4..4, y -8..0, z -4..4 (pixels); the hat layer sits 0.5 px outside, the gauze just over it
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.5F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.62F)), PartPose.ZERO);
      light = LayerDefinition.create(mesh, 64, 32).bakeRoot();
      MeshDefinition mesh2 = new MeshDefinition();
      PartDefinition root2 = mesh2.getRoot();
      root2.addOrReplaceChild("front_back", CubeListBuilder.create().texOffs(0, 10).addBox(-1.0F, -8.0F, -4.0F, 2.0F, 1.5F, 8.0F, new CubeDeformation(0.7F)), PartPose.ZERO);
      root2.addOrReplaceChild("left_right", CubeListBuilder.create().texOffs(20, 10).addBox(-4.0F, -8.0F, -1.0F, 8.0F, 1.5F, 2.0F, new CubeDeformation(0.72F)), PartPose.ZERO);
      cross = LayerDefinition.create(mesh2, 64, 32).bakeRoot();
   }

   @Override
   public void render(
      PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
      float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch
   ) {
      ItemStack stack = player.getItemBySlot(EquipmentSlot.HEAD);
      if (player.isInvisible() || !HeadBandage.isBandage(stack) || !this.getParentModel().head.visible) {
         return;
      }
      bake();
      boolean full = HeadBandage.tier(stack) >= 2;
      float blood = HeadBandage.visibleBlood(stack, player.level().getGameTime());
      poseStack.pushPose();
      this.getParentModel().head.translateAndRotate(poseStack);
      var gauze = buffer.getBuffer(RenderType.entityCutoutNoCull(GAUZE));
      light.render(poseStack, gauze, packedLight, OverlayTexture.NO_OVERLAY);
      if (full) {
         cross.render(poseStack, gauze, packedLight, OverlayTexture.NO_OVERLAY);
      }
      if (blood > 0.01F) {
         var stains = buffer.getBuffer(RenderType.entityTranslucent(BLOOD));
         int color = Math.round(Math.min(1.0F, blood) * 255.0F) << 24 | 0xFFFFFF;
         poseStack.pushPose();
         poseStack.scale(1.002F, 1.002F, 1.002F);
         light.render(poseStack, stains, packedLight, OverlayTexture.NO_OVERLAY, color);
         if (full) {
            cross.render(poseStack, stains, packedLight, OverlayTexture.NO_OVERLAY, color);
         }
         poseStack.popPose();
      }
      poseStack.popPose();
   }

   // ---- first person ----

   public static void registerLayers(RegisterGuiLayersEvent event) {
      event.registerBelowAll(ResourceLocation.fromNamespaceAndPath("lastaid", "head_bandage_strip"), HeadBandageLayer::renderStrip);
   }

   private static void renderStrip(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) {
         return;
      }
      if (MedicalConfig.CLIENT_SPEC.isLoaded() && !MedicalConfig.CLIENT.headBandageFirstPerson.get()) {
         return;
      }
      ItemStack stack = mc.player.getItemBySlot(EquipmentSlot.HEAD);
      if (!HeadBandage.isBandage(stack)) {
         return;
      }
      int w = guiGraphics.guiWidth();
      int h = Math.max(4, Math.round(guiGraphics.guiHeight() * 0.05F));
      float blood = HeadBandage.visibleBlood(stack, mc.player.level().getGameTime());
      RenderSystem.enableBlend();
      // gauze edge hanging over the forehead: lighter body, uneven lower edge, faint weave
      for (int x = 0; x < w; x += 2) {
         int sag = (int)(Math.sin(x * 0.045) * 1.5 + Math.sin(x * 0.17) * 0.8);
         int bottom = h + sag;
         guiGraphics.fill(x, 0, x + 2, bottom, 0xD8E9E4D6);
         guiGraphics.fill(x, bottom - 1, x + 2, bottom, 0xC0BDB5A3);
         if ((x / 2) % 5 == 0) {
            guiGraphics.fill(x, 0, x + 1, bottom - 1, 0x30A09884);
         }
      }
      if (blood > 0.01F) {
         int a = Math.round(blood * 120.0F);
         for (int i = 0; i < 6; i++) {
            int cx = (int)(w * (0.12F + 0.15F * i + 0.03F * (float)Math.sin(i * 7.3)));
            int r = 3 + (i * 5) % 6;
            guiGraphics.fill(cx - r, 0, cx + r, Math.min(h - 1, r + 2), a << 24 | 0x8A1212);
         }
      }
      RenderSystem.disableBlend();
   }
}
