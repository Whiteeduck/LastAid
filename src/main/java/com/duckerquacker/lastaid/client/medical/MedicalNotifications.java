package com.duckerquacker.lastaid.client.medical;

import com.mojang.blaze3d.systems.RenderSystem;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.client.ClientHooks;
import com.duckerquacker.lastaid.client.gui.medical.MedicalGuiUtil;
import com.duckerquacker.lastaid.common.medical.ClientMedicalState;
import com.duckerquacker.lastaid.common.network.BodyStatusSyncPacket;
import java.util.HashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class MedicalNotifications {
   private static final long POPUP_MS = 5000L;
   private static final long FADE_MS = 600L;
   private static boolean hintShownThisSession;
   private static long popupStart = -1L;
   private static Component popupCondition;
   private static ResourceLocation popupIcon;
   private static int popupColor;

   private MedicalNotifications() {
   }

   public static void registerLayers(RegisterGuiLayersEvent registerGuiLayersEvent) {
      registerGuiLayersEvent.registerAboveAll(ResourceLocation.fromNamespaceAndPath("lastaid", "medical_hint"), MedicalNotifications::render);
   }

   public static void resetSession() {
      hintShownThisSession = false;
      popupStart = -1L;
   }

   static void onStatusUpdate(BodyStatusSyncPacket bodyStatusSyncPacket) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         HashMap hashMap = new HashMap();

         for (ConditionSnapshot conditionSnapshot : bodyStatusSyncPacket.conditions()) {
            if (!conditionSnapshot.isRisk() && !conditionSnapshot.hasFlag(com.duckerquacker.lastaid.common.medical.overdose.Overdose.FLAG_HIDDEN)) {
               hashMap.put(conditionSnapshot.id(), conditionSnapshot.severity());
            }
         }

         ConditionSnapshot conditionSnapshot2 = null;

         for (ConditionSnapshot conditionSnapshot1 : ClientMedicalState.get().conditions()) {
            if (!conditionSnapshot1.isRisk() && !conditionSnapshot1.hasFlag(com.duckerquacker.lastaid.common.medical.overdose.Overdose.FLAG_HIDDEN)) {
               Integer integer = (Integer)hashMap.get(conditionSnapshot1.id());
               if ((integer == null || conditionSnapshot1.severity() > integer)
                  && (conditionSnapshot2 == null || conditionSnapshot1.severity() > conditionSnapshot2.severity())) {
                  conditionSnapshot2 = conditionSnapshot1;
               }
            }
         }

         if (conditionSnapshot2 != null) {
            MutableComponent mutableComponent = conditionSnapshot2.name().copy();
            if (!conditionSnapshot2.tooltipLines().isEmpty()) {
               mutableComponent.append(" (").append(conditionSnapshot2.tooltipLines().get(0)).append(")");
            }

            if (!hintShownThisSession) {
               hintShownThisSession = true;
               popupStart = System.currentTimeMillis();
               popupCondition = mutableComponent;
               popupIcon = conditionSnapshot2.icon();
               popupColor = MedicalGuiUtil.conditionColor(conditionSnapshot2);
            } else {
               minecraft.player
                  .displayClientMessage(
                     Component.translatable("lastaid.notify.actionbar", new Object[]{mutableComponent, ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage()})
                        .withStyle(ChatFormatting.YELLOW),
                     true
                  );
            }
         }
      }
   }

   private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
      if (popupStart >= 0L) {
         Minecraft minecraft = Minecraft.getInstance();
         long l = System.currentTimeMillis() - popupStart;
         if (l > 5000L || minecraft.player == null) {
            popupStart = -1L;
         } else if (!minecraft.options.hideGui) {
            float f = 1.0F;
            if (l < 200L) {
               f = (float)l / 200.0F;
            } else if (l > 4400L) {
               f = (float)(5000L - l) / 600.0F;
            }

            f = Mth.clamp(f, 0.0F, 1.0F);
            if (!(f <= 0.02F)) {
               Font font = minecraft.font;
               MutableComponent mutableComponent = Component.translatable(
                  "lastaid.notify.hint", new Object[]{ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage()}
               );
               Component component = popupCondition;
               int i = Math.max(font.width(mutableComponent), font.width(component));
               int j = i + 34;
               byte b = 30;
               int k = guiGraphics.guiWidth() - j - 6;
               int m = guiGraphics.guiHeight() - b - 6;
               guiGraphics.fill(k, m, k + j, m + b, MedicalGuiUtil.withAlpha(1052948, 0.85F * f));
               MedicalGuiUtil.outline(guiGraphics, k, m, k + j, m + b, MedicalGuiUtil.withAlpha(5922150, f));
               guiGraphics.fill(k, m, k + 2, m + b, MedicalGuiUtil.withAlpha(popupColor, f));
               if (popupIcon != null) {
                  guiGraphics.setColor(1.0F, 1.0F, 1.0F, f);
                  RenderSystem.enableBlend();
                  guiGraphics.blit(popupIcon, k + 8, m + 7, 0.0F, 0.0F, 16, 16, 16, 16);
                  guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
               } else {
                  MedicalGuiUtil.fillCircle(guiGraphics, k + 16, m + 15, 4, MedicalGuiUtil.withAlpha(popupColor, f));
               }

               int n = Mth.clamp((int)(f * 255.0F), 4, 255) << 24;
               guiGraphics.drawString(font, mutableComponent, k + 30, m + 5, n | 16777215, false);
               guiGraphics.drawString(font, component, k + 30, m + 17, n | 12303291, false);
            }
         }
      }
   }
}
