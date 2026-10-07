package com.duckerquacker.lastaid.client.medical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Player model changes from the medical system (all players, synced through LimbStatePacket):
 * <ul>
 *    <li>amputated limbs are hidden (base model + sleeve/pants layer + armor + held item, 3rd and 1st person)</li>
 *    <li>frostbite stage 3: dark purple tint, stage 4 (gangrene): static almost-black purple</li>
 * </ul>
 * Visibility is set fresh every frame in PlayerRenderer.setModelProperties, so nothing leaks between players.
 */
public final class LimbRenderHelper {
   /** Plain white texture: the overlay color is then exactly the tint color, independent of the skin. */
   private static final net.minecraft.resources.ResourceLocation TINT_TEXTURE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("lastaid", "textures/misc/white.png");

   private LimbRenderHelper() {
   }

   private static boolean enabled() {
      return !MedicalConfig.CLIENT_SPEC.isLoaded() || MedicalConfig.CLIENT.limbVisuals.get();
   }

   public static byte status(Entity entity, Frostbite.Limb limb) {
      return entity instanceof Player ? LimbVisualState.client(entity.getId(), limb) : 0;
   }

   public static boolean amputated(Entity entity, Frostbite.Limb limb) {
      return status(entity, limb) == LimbVisualState.AMPUTATED;
   }

   /** Amputated, gangrene, open fracture, or both arms broken / dislocated: the arm can't hold anything. */
   public static boolean armDead(Entity entity, Frostbite.Limb limb) {
      if (status(entity, limb) >= 4 || ClientBones.fractureType(entity, limb) == 3) {
         return true;
      }
      return ClientBones.out(entity, Frostbite.Limb.LEFT_ARM) && ClientBones.out(entity, Frostbite.Limb.RIGHT_ARM);
   }

   /** Splint / cast: light grey, nearly opaque, drawn on the outer layer (sleeve / pants) so it sits on top. */
   private static final int SPLINT_COLOR = 0xE8C8C4BC;

   private static void renderSplint(ModelPart part, ModelPart outer, PoseStack poseStack, VertexConsumer consumer, int light, boolean arm) {
      if (!part.visible) {
         return;
      }
      ModelPart target = outer != null ? outer : part;
      boolean wasVisible = target.visible;
      target.visible = true;
      poseStack.pushPose();
      // a bit thicker than the sleeve itself
      target.translateAndRotate(poseStack);
      // lower ~60 % of the limb (arm cuboid: y -2..10 px, leg: 0..12 px from the pivot)
      poseStack.translate(0.0F, arm ? 0.2375F : 0.285F, 0.0F);
      poseStack.scale(1.04F, 0.62F, 1.04F);
      float x = target.x, y = target.y, z = target.z, xr = target.xRot, yr = target.yRot, zr = target.zRot;
      target.setPos(0.0F, 0.0F, 0.0F);
      target.setRotation(0.0F, 0.0F, 0.0F);
      target.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, SPLINT_COLOR);
      target.setPos(x, y, z);
      target.setRotation(xr, yr, zr);
      poseStack.popPose();
      target.visible = wasVisible;
   }

   /** Both arms useless: the held item is drawn in the mouth (purely visual). */
   public static boolean mouthMode(Entity entity) {
      return enabled()
         && (!MedicalConfig.CLIENT_SPEC.isLoaded() || MedicalConfig.CLIENT.mouthItem.get())
         && entity instanceof Player
         && LimbVisualState.clientHasAny(entity.getId())
         && armDead(entity, Frostbite.Limb.RIGHT_ARM)
         && armDead(entity, Frostbite.Limb.LEFT_ARM);
   }

   /** The item shown in the mouth: the one being used, else the main hand, else the off hand. */
   public static net.minecraft.world.item.ItemStack mouthItem(Player player) {
      if (player.isUsingItem()) {
         return player.getUseItem();
      }
      net.minecraft.world.item.ItemStack main = player.getMainHandItem();
      return main.isEmpty() ? player.getOffhandItem() : main;
   }

   public static Frostbite.Limb armLimb(HumanoidArm arm) {
      return arm == HumanoidArm.RIGHT ? Frostbite.Limb.RIGHT_ARM : Frostbite.Limb.LEFT_ARM;
   }

   /** Mixin: PlayerRenderer.setModelProperties TAIL */
   public static void hideAmputated(PlayerModel<?> model, AbstractClientPlayer player) {
      if (!enabled() || !LimbVisualState.clientHasAny(player.getId())) {
         return;
      }
      if (amputated(player, Frostbite.Limb.RIGHT_ARM)) {
         model.rightArm.visible = false;
         model.rightSleeve.visible = false;
      }
      if (amputated(player, Frostbite.Limb.LEFT_ARM)) {
         model.leftArm.visible = false;
         model.leftSleeve.visible = false;
      }
      if (amputated(player, Frostbite.Limb.RIGHT_LEG)) {
         model.rightLeg.visible = false;
         model.rightPants.visible = false;
      }
      if (amputated(player, Frostbite.Limb.LEFT_LEG)) {
         model.leftLeg.visible = false;
         model.leftPants.visible = false;
      }
   }

   /** Mixin: HumanoidArmorLayer.setPartVisibility TAIL (no floating sleeves/boots on a missing limb). */
   public static void hideArmor(HumanoidModel<?> model, Entity entity) {
      if (entity == null || !enabled() || !LimbVisualState.clientHasAny(entity.getId())) {
         return;
      }
      if (amputated(entity, Frostbite.Limb.RIGHT_ARM)) {
         model.rightArm.visible = false;
      }
      if (amputated(entity, Frostbite.Limb.LEFT_ARM)) {
         model.leftArm.visible = false;
      }
      if (amputated(entity, Frostbite.Limb.RIGHT_LEG)) {
         model.rightLeg.visible = false;
      }
      if (amputated(entity, Frostbite.Limb.LEFT_LEG)) {
         model.leftLeg.visible = false;
      }
   }

   /** ARGB overlay color for a frostbite stage, 0 = none. */
   public static int tint(byte status, float time) {
      if (status == 3) {
         return 0x80481A70; // dark purple, 50%
      } else if (status == 4) {
         return 0xD8140A1A; // dead tissue: almost black with a hint of purple, static (the pulse lives in the body panel only)
      } else {
         return 0;
      }
   }

   static void renderTint(ModelPart part, ModelPart outer, PoseStack poseStack, VertexConsumer consumer, int light, int color) {
      if (part.visible) {
         part.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
      }
      if (outer != null && outer.visible) {
         outer.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
      }
   }

   /** Render layer body: re-draws frostbitten limbs with a translucent colored copy of the skin. */
   public static void renderTintLayer(PlayerModel<AbstractClientPlayer> model, PoseStack poseStack, MultiBufferSource buffer, int light, AbstractClientPlayer player, float partialTick) {
      if (!enabled() || !LimbVisualState.clientHasAny(player.getId()) || player.isInvisible()) {
         return;
      }
      float time = player.tickCount + partialTick;
      VertexConsumer consumer = null;
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         int color = tint(status(player, limb), time);
         if (color == 0) {
            continue;
         }
         if (consumer == null) {
            consumer = buffer.getBuffer(RenderType.entityTranslucent(TINT_TEXTURE));
         }
         switch (limb) {
            case RIGHT_ARM -> renderTint(model.rightArm, model.rightSleeve, poseStack, consumer, light, color);
            case LEFT_ARM -> renderTint(model.leftArm, model.leftSleeve, poseStack, consumer, light, color);
            case RIGHT_LEG -> renderTint(model.rightLeg, model.rightPants, poseStack, consumer, light, color);
            case LEFT_LEG -> renderTint(model.leftLeg, model.leftPants, poseStack, consumer, light, color);
         }
      }
      VertexConsumer splint = null;
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         if (!ClientBones.splinted(player, limb) || amputated(player, limb)) {
            continue;
         }
         if (splint == null) {
            splint = buffer.getBuffer(RenderType.entityTranslucent(TINT_TEXTURE));
         }
         switch (limb) {
            case RIGHT_ARM -> renderSplint(model.rightArm, model.rightSleeve, poseStack, splint, light, true);
            case LEFT_ARM -> renderSplint(model.leftArm, model.leftSleeve, poseStack, splint, light, true);
            case RIGHT_LEG -> renderSplint(model.rightLeg, model.rightPants, poseStack, splint, light, false);
            case LEFT_LEG -> renderSplint(model.leftLeg, model.leftPants, poseStack, splint, light, false);
         }
      }
   }

   // ---- first person ----

   /** Both arms useless and something "held": no hands, the item sits low in the middle of the view (in the teeth). */
   @SubscribeEvent
   public static void onRenderHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      Player player = mc.player;
      if (player == null || !enabled()) {
         return;
      }
      if (!mouthMode(player)) {
         // an amputated arm holds nothing in first person either
         HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         if (amputated(player, armLimb(arm))) {
            event.setCanceled(true);
         }
         return;
      }
      net.minecraft.world.item.ItemStack stack = mouthItem(player);
      if (stack.isEmpty()) {
         return;
      }
      event.setCanceled(true);
      if (event.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }
      PoseStack poseStack = event.getPoseStack();
      poseStack.pushPose();
      float time = player.tickCount + event.getPartialTick();
      float chew = player.isUsingItem() ? Mth.sin(time * 1.4F) * 0.02F : 0.0F;
      poseStack.translate(0.0F, -0.36F + chew, -0.62F);
      poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-35.0F));
      poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90.0F));
      poseStack.scale(0.32F, 0.32F, 0.32F);
      mc.getEntityRenderDispatcher().getItemInHandRenderer()
         .renderItem(player, stack, net.minecraft.world.item.ItemDisplayContext.FIXED, false, poseStack, event.getMultiBufferSource(), event.getPackedLight());
      poseStack.popPose();
   }

   /** renderRightHand/renderLeftHand fire RenderArmEvent again; this guard lets that inner call render normally. */
   private static boolean renderingArm;

   @SubscribeEvent
   public static void onRenderArm(RenderArmEvent event) {
      if (!enabled() || renderingArm) {
         return;
      }
      AbstractClientPlayer player = event.getPlayer();
      Frostbite.Limb limb = armLimb(event.getArm());
      byte status = status(player, limb);
      if (status == LimbVisualState.AMPUTATED) {
         event.setCanceled(true);
         return;
      }
      int color = tint(status, player.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
      if (color == 0) {
         return;
      }
      EntityRenderer<? super AbstractClientPlayer> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
      if (!(renderer instanceof PlayerRenderer playerRenderer)) {
         return;
      }
      // Draw the vanilla arm ourselves, then the tint on top with the same pose
      event.setCanceled(true);
      renderingArm = true;
      try {
         if (event.getArm() == HumanoidArm.RIGHT) {
            playerRenderer.renderRightHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player);
         } else {
            playerRenderer.renderLeftHand(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player);
         }
      } finally {
         renderingArm = false;
      }
      PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
      VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TINT_TEXTURE));
      if (event.getArm() == HumanoidArm.RIGHT) {
         renderTint(model.rightArm, model.rightSleeve, event.getPoseStack(), consumer, event.getPackedLight(), color);
      } else {
         renderTint(model.leftArm, model.leftSleeve, event.getPoseStack(), consumer, event.getPackedLight(), color);
      }
   }

   /** Camera shake for the first seconds after an amputation (strong at first, fading out). */
   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }
      long left = LimbVisualState.shakeUntil() - mc.level.getGameTime();
      float amount = 0.0F;
      if (left > 0L) {
         float strength = Mth.clamp((float)left / LimbVisualState.shakeDuration(), 0.0F, 1.0F);
         amount = 2.2F * strength * LimbVisualState.shakeStrength();
      }
      amount = Math.max(amount, AmputationFeedback.procedureShake((float)event.getPartialTick()));
      if (amount <= 0.0F) {
         return;
      }
      float t = (float)(mc.player.tickCount + event.getPartialTick());
      event.setRoll(event.getRoll() + Mth.sin(t * 1.7F) * amount);
      event.setPitch(event.getPitch() + Mth.sin(t * 2.3F + 1.0F) * amount * 0.6F);
      event.setYaw(event.getYaw() + Mth.cos(t * 1.9F) * amount * 0.5F);
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      LimbVisualState.clearClient();
   }

   /** Used by the HUD / panel to tell which hand is meant. */
   public static InteractionHand handFor(Player player, HumanoidArm arm) {
      return player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
   }
}
