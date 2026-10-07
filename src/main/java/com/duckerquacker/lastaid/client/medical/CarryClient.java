package com.duckerquacker.lastaid.client.medical;

import com.mojang.blaze3d.vertex.PoseStack;
import com.duckerquacker.lastaid.common.medical.carry.CarryManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client side of carrying: the carried player (a passenger of the carrier) is drawn lying across the carrier's
 * arms, the carrier holds both arms forward, and a hint over the hotbar shows what right click does.
 */
public final class CarryClient {
   private static float savedBody;
   private static float savedBodyO;
   private static float savedHead;
   private static float savedHeadO;
   private static boolean pushed;

   private CarryClient() {
   }

   @SubscribeEvent
   public static void onRenderPre(RenderPlayerEvent.Pre event) {
      Player target = event.getEntity();
      if (!(target.getVehicle() instanceof Player carrier)) {
         return;
      }
      float pt = event.getPartialTick();
      float yaw = Mth.lerp(pt, carrier.yBodyRotO, carrier.yBodyRot);
      double rad = Math.toRadians(yaw);
      Vec3 forward = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
      Vec3 carrierPos = carrier.getPosition(pt);
      Vec3 targetPos = target.getPosition(pt);
      // lying across the arms: body along the carrier's left-right axis, face up.
      // A passenger always gets the vehicle's body yaw, so the model faces `forward`; map forward -> up and up -> side.
      savedBody = target.yBodyRot;
      savedBodyO = target.yBodyRotO;
      savedHead = target.yHeadRot;
      savedHeadO = target.yHeadRotO;
      target.yHeadRot = target.yHeadRotO = yaw;
      Vector3f f = new Vector3f((float)forward.x, 0.0F, (float)forward.z);
      Vector3f y = new Vector3f(0.0F, 1.0F, 0.0F);
      Vector3f side = new Vector3f(f).cross(y);
      org.joml.Matrix3f from = new org.joml.Matrix3f(f, y, new Vector3f(f).cross(y));
      org.joml.Matrix3f to = new org.joml.Matrix3f(y, side, new Vector3f(y).cross(side));
      org.joml.Matrix3f rot = to.mul(from.transpose());
      Quaternionf roll = rot.getNormalizedRotation(new Quaternionf());
      Vector3f up = roll.transform(new Vector3f(0.0F, 1.0F, 0.0F));
      Vec3 chest = carrierPos.add(0.0, 1.05, 0.0).add(forward.scale(0.45));
      PoseStack pose = event.getPoseStack();
      pose.pushPose();
      pushed = true;
      pose.translate(chest.x - targetPos.x - up.x * 0.9, chest.y - targetPos.y - up.y * 0.9, chest.z - targetPos.z - up.z * 0.9);
      pose.mulPose(roll);
   }

   @SubscribeEvent
   public static void onRenderPost(RenderPlayerEvent.Post event) {
      if (!pushed) {
         return;
      }
      pushed = false;
      event.getPoseStack().popPose();
      Player target = event.getEntity();
      target.yBodyRot = savedBody;
      target.yBodyRotO = savedBodyO;
      target.yHeadRot = savedHead;
      target.yHeadRotO = savedHeadO;
   }

   /** Mixin: PlayerRenderer.setModelProperties TAIL. Carrier: both arms forward. */
   public static void poseCarrier(PlayerModel<?> model, Player player) {
      for (Entity passenger : player.getPassengers()) {
         if (passenger instanceof Player) {
            model.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            model.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
            return;
         }
      }
   }

   public static void registerLayers(RegisterGuiLayersEvent event) {
      event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("lastaid", "carry_hint"), CarryClient::renderHint);
   }

   private static void renderHint(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      Player self = mc.player;
      if (self == null || mc.options.hideGui || mc.screen != null || !CarryManager.enabled()) {
         return;
      }
      Component text = null;
      if (!self.getPassengers().isEmpty() && self.getPassengers().get(0) instanceof Player) {
         text = Component.translatable("lastaid.carry.hint.carrying");
      } else if (mc.crosshairPickEntity instanceof Player target
         && target.distanceTo(self) <= 3.0F
         && !target.isPassenger()
         && self.getMainHandItem().isEmpty()
         && CarryManager.canBeCarried(target)) {
         text = CarryManager.canCarry(self) ? Component.translatable("lastaid.carry.hint.both") : Component.translatable("lastaid.carry.hint.drag");
      }
      if (text == null) {
         return;
      }
      int w = mc.font.width(text);
      int x = (guiGraphics.guiWidth() - w) / 2;
      int y = guiGraphics.guiHeight() - 72;
      guiGraphics.fill(x - 3, y - 2, x + w + 3, y + 10, 0x90000000);
      guiGraphics.drawString(mc.font, text, x, y, 0xFFFFFF, false);
   }
}
