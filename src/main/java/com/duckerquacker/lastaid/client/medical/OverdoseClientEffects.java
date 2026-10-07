package com.duckerquacker.lastaid.client.medical;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.medical.ClientMedicalState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.overdose.Overdose;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Camera effects of an overdose (own player only, read from the hidden "drugs" snapshot):
 * adrenaline stage I a fine tremor (±0.5°), stage II a strong one (±2°); morphine stage I and mixed intoxication a
 * slow, drunk-like sway.
 */
public final class OverdoseClientEffects {
   private OverdoseClientEffects() {
   }

   @SubscribeEvent
   public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.isPaused()) {
         return;
      }
      if (MedicalConfig.CLIENT_SPEC.isLoaded() && !MedicalConfig.CLIENT.cameraSway.get()) {
         return;
      }
      ConditionSnapshot drugs = ClientMedicalState.find(Overdose.DRUGS_ID);
      if (drugs == null) {
         return;
      }
      int packed = drugs.clientFlags();
      int morphineStage = packed >> 12 & 3;
      int adrenalineStage = packed >> 14 & 3;
      boolean mixed = (packed >> 16 & 1) != 0;
      float t = (float)(mc.player.tickCount + event.getPartialTick());
      float tremor = adrenalineStage >= 2 ? 2.0F : (adrenalineStage == 1 ? 0.5F : 0.0F);
      if (tremor > 0.0F) {
         // fast, irregular jitter
         event.setPitch(event.getPitch() + (Mth.sin(t * 3.7F) * 0.6F + Mth.sin(t * 5.3F + 2.0F) * 0.4F) * tremor);
         event.setYaw(event.getYaw() + (Mth.cos(t * 4.1F) * 0.6F + Mth.sin(t * 6.1F) * 0.4F) * tremor);
      }
      float sway = (mixed ? 1.5F : 0.0F) + (morphineStage == 1 ? 1.0F : 0.0F);
      if (sway > 0.0F) {
         event.setRoll(event.getRoll() + Mth.sin(t * 0.045F) * sway * 1.6F);
         event.setYaw(event.getYaw() + Mth.sin(t * 0.031F + 1.3F) * sway);
      }
   }
}
