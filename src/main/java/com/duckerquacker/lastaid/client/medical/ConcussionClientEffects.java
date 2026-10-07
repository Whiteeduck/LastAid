package com.duckerquacker.lastaid.client.medical;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.medical.ClientMedicalState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import com.duckerquacker.lastaid.common.network.BodyStatusSyncPacket;
import java.util.Locale;
import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.DebugText;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;

public final class ConcussionClientEffects {
   private static final Random RANDOM = new Random();
   private static boolean sprintBlockedUntilRelease;
   private static boolean wasSprinting;
   private static float vignetteStrength;
   private static final String FROM = "аеоисрхукАЕОСРХКТМНВaeoilsgбзч";
   private static final String TO = "@30uсpxyk430CPXKTMH8@3011596З4";

   private ConcussionClientEffects() {
   }

   public static void init() {
      ClientMedicalState.setListener(ConcussionClientEffects::onStatusUpdate);
   }

   public static void registerLayers(RegisterGuiLayersEvent registerGuiLayersEvent) {
      registerGuiLayersEvent.registerBelowAll(ResourceLocation.fromNamespaceAndPath("lastaid", "concussion_vignette"), ConcussionClientEffects::renderVignette);
      MedicalNotifications.registerLayers(registerGuiLayersEvent);
   }

   private static int severity() {
      ConditionSnapshot conditionSnapshot = ClientMedicalState.find(Concussion.ID);
      return conditionSnapshot == null ? 0 : conditionSnapshot.severity();
   }

   private static int flags() {
      ConditionSnapshot conditionSnapshot = ClientMedicalState.find(Concussion.ID);
      return conditionSnapshot == null ? 0 : conditionSnapshot.clientFlags();
   }

   private static boolean clientConfig() {
      return MedicalConfig.CLIENT_SPEC.isLoaded();
   }

   private static void onStatusUpdate(BodyStatusSyncPacket bodyStatusSyncPacket) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         ClientMedicalState.setLocalPlayer(minecraft.player.getUUID());
      }

      MedicalNotifications.onStatusUpdate(bodyStatusSyncPacket);
   }

   private static int movementFlags() {
      return ClientMedicalState.get().movementFlags();
   }

   @SubscribeEvent
   public static void onLogout(LoggingOut loggingOut) {
      ClientMedicalState.reset();
      MedicalNotifications.resetSession();
      sprintBlockedUntilRelease = false;
   }

   private static void renderVignette(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && !minecraft.options.hideGui && (!clientConfig() || (Boolean)MedicalConfig.CLIENT.vignette.get())) {
         int i = severity();

         float f = switch (i) {
            case 1 -> 0.3F;
            case 2 -> 0.5F;
            case 3 -> 0.72F;
            default -> 0.0F;
         };
         vignetteStrength = vignetteStrength + (f - vignetteStrength) * 0.05F;
         if (!(vignetteStrength < 0.01F)) {
            float g = (minecraft.player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20.0F;
            float h = vignetteStrength * (0.85F + 0.15F * Mth.sin(g * 1.3F));
            int j = guiGraphics.guiWidth();
            int k = guiGraphics.guiHeight();
            int l = Math.max(24, Math.min(j, k) / 3);
            byte b = 24;

            for (int m = 0; m < b; m++) {
               float n = 1.0F - (float)m / b;
               int o = Mth.clamp((int)(h * n * n * 255.0F / 2.2F), 0, 255);
               if (o > 0) {
                  int p = o << 24 | 657934;
                  int q = l * m / b;
                  int r = l * (m + 1) / b;
                  guiGraphics.fill(q, 0, r, k, p);
                  guiGraphics.fill(j - r, 0, j - q, k, p);
                  guiGraphics.fill(0, q, j, r, p);
                  guiGraphics.fill(0, k - r, j, k - q, p);
               }
            }
         }
      } else {
         vignetteStrength = 0.0F;
      }
   }

   @SubscribeEvent
   public static void onCameraAngles(ComputeCameraAngles computeCameraAngles) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null && (!clientConfig() || (Boolean)MedicalConfig.CLIENT.cameraSway.get())) {
         int i = severity();
         if (i >= 2) {
            float f = (float)((minecraft.player.tickCount + computeCameraAngles.getPartialTick()) / 20.0);
            float g = i >= 3 ? 6.5F : 1.5F;
            if ((flags() & 2) != 0) {
               g *= 0.6F;
            }

            float h = g * (Mth.sin(f * 0.9F) * 0.7F + Mth.sin(f * 2.3F) * 0.3F);
            float j = g * 0.25F * Mth.sin(f * 0.53F);
            float k = g * 0.15F * Mth.sin(f * 0.71F + 1.0F);
            if ((flags() & 1) != 0) {
               float l = f % 15.0F;
               if (l < 0.6F) {
                  float m = Mth.sin(l / 0.6F * (float) Math.PI) * 4.0F;
                  j += m * Mth.sin(f * 40.0F);
                  k += m * Mth.cos(f * 35.0F);
               }
            }

            computeCameraAngles.setRoll(computeCameraAngles.getRoll() + h);
            computeCameraAngles.setYaw(computeCameraAngles.getYaw() + j);
            computeCameraAngles.setPitch(computeCameraAngles.getPitch() + k);
         }
      }
   }

   @SubscribeEvent
   public static void onMovementInput(MovementInputUpdateEvent movementInputUpdateEvent) {
      Minecraft minecraft = Minecraft.getInstance();
      if (movementInputUpdateEvent.getEntity() == minecraft.player) {
         if (sprintBlocked()) {
            minecraft.options.keySprint.setDown(false);
         }
      }
   }

   @SubscribeEvent
   public static void onClientTick(Post post) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer localPlayer = minecraft.player;
      if (localPlayer != null) {
         int i = severity();
         if (i <= 0) {
            sprintBlockedUntilRelease = false;
            if (sprintBlocked() && localPlayer.isSprinting()) {
               localPlayer.setSprinting(false);
            }

            wasSprinting = localPlayer.isSprinting();
         } else {
            if (i == 1 && localPlayer.isSprinting() && !wasSprinting && !sprintBlockedUntilRelease) {
               double d = MedicalConfig.SERVER_SPEC.isLoaded() ? (Double)MedicalConfig.SERVER.concussion.sprintBlockChanceSeverity1.get() : 0.5;
               if (RANDOM.nextDouble() < d) {
                  sprintBlockedUntilRelease = true;
                  localPlayer.displayClientMessage(Component.translatable("lastaid.concussion.cant_run"), true);
               }
            }

            if (sprintBlockedUntilRelease && !minecraft.options.keySprint.isDown() && !localPlayer.input.hasForwardImpulse()) {
               sprintBlockedUntilRelease = false;
            }

            if (sprintBlocked() && localPlayer.isSprinting()) {
               localPlayer.setSprinting(false);
            }

            wasSprinting = localPlayer.isSprinting();
         }
      }
   }

   private static boolean sprintBlocked() {
      if ((movementFlags() & 1) != 0) {
         return true;
      } else {
         int i = severity();
         return i >= 2 || i == 1 && sprintBlockedUntilRelease;
      }
   }

   @SubscribeEvent
   public static void onJump(LivingJumpEvent livingJumpEvent) {
      Minecraft minecraft = Minecraft.getInstance();
      if (livingJumpEvent.getEntity() == minecraft.player && livingJumpEvent.getEntity().level().isClientSide()) {
         if ((movementFlags() & 2) != 0) {
            Vec3 vec31 = livingJumpEvent.getEntity().getDeltaMovement();
            livingJumpEvent.getEntity().setDeltaMovement(vec31.x * 0.3, Math.min(0.0, vec31.y), vec31.z * 0.3);
         } else if (severity() >= 2) {
            double d = MedicalConfig.SERVER_SPEC.isLoaded() ? (Double)MedicalConfig.SERVER.concussion.jumpFailChanceSeverity2.get() : 0.4;
            if (RANDOM.nextDouble() < d) {
               Vec3 vec3 = livingJumpEvent.getEntity().getDeltaMovement();
               livingJumpEvent.getEntity().setDeltaMovement(vec3.x * 0.3, Math.min(0.0, vec3.y), vec3.z * 0.3);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onChat(Player player) {
      if (severity() >= 3 && (!clientConfig() || (Boolean)MedicalConfig.CLIENT.chatGarble.get())) {
         double d = MedicalConfig.SERVER_SPEC.isLoaded() ? (Double)MedicalConfig.SERVER.concussion.chatGarbleChance.get() : 0.3;
         if (RANDOM.nextDouble() < d) {
            player.setMessage(Component.literal(garble(player.getMessage().getString())));
         }
      }
   }

   static String garble(String string) {
      StringBuilder stringBuilder = new StringBuilder(string.length());

      for (char c : string.toCharArray()) {
         int i = "аеоисрхукАЕОСРХКТМНВaeoilsgбзч".indexOf(c);
         if (i >= 0 && RANDOM.nextFloat() < 0.35F) {
            stringBuilder.append("@30uсpxyk430CPXKTMH8@3011596З4".charAt(i));
         } else if (Character.isLetter(c) && RANDOM.nextFloat() < 0.06F) {
            stringBuilder.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
         } else {
            stringBuilder.append(c);
         }
      }

      return stringBuilder.toString();
   }

   @SubscribeEvent
   public static void onDebugText(DebugText debugText) {
      if (!clientConfig() || (Boolean)MedicalConfig.CLIENT.debugOverlayLine.get()) {
         ConditionSnapshot conditionSnapshot = ClientMedicalState.find(Concussion.ID);
         if (conditionSnapshot != null) {
            debugText.getLeft()
               .add(
                  String.format(
                     Locale.ROOT, "[Last Aid] Concussion: severity %d, recovery %.0f%%", conditionSnapshot.severity(), conditionSnapshot.progress() * 100.0F
                  )
               );
         }
      }
   }
}
