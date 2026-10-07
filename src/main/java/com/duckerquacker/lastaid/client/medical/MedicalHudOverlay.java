package com.duckerquacker.lastaid.client.medical;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.client.gui.medical.BodyPartLayout;
import com.duckerquacker.lastaid.client.gui.medical.MedicalGuiUtil;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * Draws amputations and frostbite on top of First Aid's HUD figure, using the same {@link BodyPartLayout}
 * as the H panel, so both always show the same limb on the same side.
 */
public final class MedicalHudOverlay {
   private MedicalHudOverlay() {
   }

   public static void render(GuiGraphics guiGraphics, int x, int y, int color) {
      Player player = Minecraft.getInstance().player;
      if (player == null || !LimbVisualState.clientHasAny(player.getId())) {
         return;
      }
      float alpha = Math.max(0.25F, (color >>> 24) / 255.0F);
      for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
         Frostbite.Limb limb = Frostbite.Limb.of(part);
         if (limb == null) {
            continue;
         }
         byte status = LimbVisualState.client(player.getId(), limb);
         int[] r = BodyPartLayout.rect(part);
         int x0 = x + r[0];
         int y0 = y + r[1];
         int x1 = x0 + r[2];
         int y1 = y0 + r[3];
         if (status == LimbVisualState.AMPUTATED) {
            guiGraphics.fill(x0, y0, x1, y1, MedicalGuiUtil.withAlpha(0x101010, 0.85F * alpha));
            MedicalGuiUtil.cross(guiGraphics, x0, y0, x1, y1, MedicalGuiUtil.withAlpha(0xB01818, alpha));
         } else if (status == 4) {
            float pulse = MedicalGuiUtil.pulse(2600L);
            guiGraphics.fill(x0, y0, x1, y1, MedicalGuiUtil.withAlpha(0x140A10, 0.85F * alpha));
            guiGraphics.fill(x0, y0, x1, y1, MedicalGuiUtil.withAlpha(0xA01010, 0.55F * pulse * alpha));
         } else if (status == 3) {
            guiGraphics.fill(x0, y0, x1, y1, MedicalGuiUtil.withAlpha(0x6A2AA8, 0.75F * alpha));
         }
      }
   }
}
