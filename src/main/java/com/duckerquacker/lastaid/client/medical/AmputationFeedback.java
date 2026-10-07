package com.duckerquacker.lastaid.client.medical;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.network.AmputationFeedbackPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Client side of an amputation, only on the patient's screen: a light camera shake while the limb is being cut,
 * red text fragments that pop up at random places and fade, and the stronger shake right after the cut.
 * The server decides the strength (painkiller / morphine = nothing, painkiller not active yet = half).
 */
public final class AmputationFeedback {
   public static final int PHRASES = 12;
   private static final RandomSource RANDOM = RandomSource.create();
   private static final List<Text> TEXTS = new ArrayList<>();
   private static byte phase = AmputationFeedbackPacket.STOP;
   private static byte strength = AmputationFeedbackPacket.FULL;
   /** client tick counter until which the procedure phase lasts */
   private static int procedureLeft;
   private static int nextText;
   private static int boundEntity = -1;

   private AmputationFeedback() {
   }

   public static void registerLayers(RegisterGuiLayersEvent event) {
      event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("lastaid", "amputation_text"), AmputationFeedback::render);
   }

   public static void receive(byte newPhase, byte newStrength, int ticks) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         return;
      }
      boundEntity = mc.player.getId();
      strength = newStrength;
      switch (newPhase) {
         case AmputationFeedbackPacket.PROCEDURE -> {
            phase = newPhase;
            procedureLeft = ticks;
         }
         case AmputationFeedbackPacket.AGONY -> {
            phase = AmputationFeedbackPacket.STOP;
            procedureLeft = 0;
            float factor = strengthFactor();
            if (factor > 0.0F) {
               LimbVisualState.startShake(mc.level.getGameTime() + ticks, ticks, factor);
            }
         }
         default -> clear();
      }
   }

   private static float strengthFactor() {
      return switch (strength) {
         case AmputationFeedbackPacket.FULL -> 1.0F;
         case AmputationFeedbackPacket.PARTIAL -> 0.5F;
         default -> 0.0F;
      };
   }

   public static void clear() {
      phase = AmputationFeedbackPacket.STOP;
      procedureLeft = 0;
      TEXTS.clear();
      boundEntity = -1;
   }

   private static boolean active() {
      return phase == AmputationFeedbackPacket.PROCEDURE && procedureLeft > 0 && strength != AmputationFeedbackPacket.NUMB;
   }

   /** Camera shake amount (degrees) while the limb is being cut. */
   public static float procedureShake(float partialTick) {
      if (!active()) {
         return 0.0F;
      }
      // slowly pulsing, never as violent as the shake after the cut
      Minecraft mc = Minecraft.getInstance();
      float t = mc.player == null ? 0.0F : mc.player.tickCount + partialTick;
      return (0.9F + 0.5F * Mth.sin(t * 0.21F)) * strengthFactor();
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         if (phase != AmputationFeedbackPacket.STOP || !TEXTS.isEmpty()) {
            clear();
         }
         return;
      }
      if (boundEntity != -1 && (mc.player.getId() != boundEntity || !mc.player.isAlive())) {
         // died, respawned or changed dimension
         clear();
         LimbVisualState.startShake(0L, 1, 0.0F);
         return;
      }
      if (mc.isPaused()) {
         return;
      }
      Iterator<Text> iterator = TEXTS.iterator();
      while (iterator.hasNext()) {
         Text text = iterator.next();
         if (++text.age >= text.life) {
            iterator.remove();
         }
      }
      if (procedureLeft > 0) {
         procedureLeft--;
      }
      if (!active()) {
         if (procedureLeft <= 0) {
            phase = AmputationFeedbackPacket.STOP;
         }
         return;
      }
      if (--nextText <= 0) {
         boolean full = strength == AmputationFeedbackPacket.FULL;
         // full: every 1-2 s, partial: every 2-4 s
         nextText = full ? 20 + RANDOM.nextInt(21) : 40 + RANDOM.nextInt(41);
         if (com.duckerquacker.lastaid.common.medical.MedicalConfig.SERVER_SPEC.isLoaded()
            && !com.duckerquacker.lastaid.common.medical.MedicalConfig.SERVER.amputation.scaryText.get()) {
            return;
         }
         Text text = new Text();
         text.phrase = Component.translatable("lastaid.amputation.scream." + RANDOM.nextInt(PHRASES));
         text.x = 0.08F + RANDOM.nextFloat() * 0.84F;
         text.y = 0.08F + RANDOM.nextFloat() * 0.80F;
         text.angle = (RANDOM.nextFloat() * 30.0F - 15.0F);
         text.size = 18.0F + RANDOM.nextFloat() * 24.0F;
         text.alpha = (0.35F + RANDOM.nextFloat() * 0.45F) * (full ? 1.0F : 0.7F);
         text.life = 40 + RANDOM.nextInt(30);
         TEXTS.add(text);
      }
   }

   @SubscribeEvent
   public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
      clear();
   }

   private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      if (TEXTS.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      int w = guiGraphics.guiWidth();
      int h = guiGraphics.guiHeight();
      double guiScale = mc.getWindow().getGuiScale();
      float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
      RenderSystem.enableBlend();
      for (Text text : TEXTS) {
         float age = text.age + partial;
         float fadeIn = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
         float fadeOut = Mth.clamp((text.life - age) / (text.life * 0.6F), 0.0F, 1.0F);
         float alpha = text.alpha * Math.min(fadeIn, fadeOut);
         if (alpha < 0.02F) {
            continue;
         }
         // size is in screen pixels, the font is 9 GUI units high
         float scale = (float)(text.size / guiScale / 9.0);
         int width = font.width(text.phrase);
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(text.x * w, text.y * h, 0.0F);
         guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(text.angle));
         guiGraphics.pose().scale(scale, scale, 1.0F);
         int a = Mth.clamp((int)(alpha * 255.0F), 4, 255);
         guiGraphics.drawString(font, text.phrase, -width / 2, -4, a << 24 | 0xB00808, false);
         guiGraphics.pose().popPose();
      }
      RenderSystem.disableBlend();
   }

   private static final class Text {
      Component phrase;
      float x;
      float y;
      float angle;
      float size;
      float alpha;
      int age;
      int life;
   }
}
