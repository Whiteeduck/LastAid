package com.duckerquacker.lastaid.client.gui.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

public class BodyPartWidget {
   public static final int FIGURE_WIDTH = BodyPartLayout.WIDTH;
   public static final int FIGURE_HEIGHT = BodyPartLayout.HEIGHT;
   public final EnumPlayerPart part;
   public final int x0;
   public final int y0;
   public final int x1;
   public final int y1;
   /** Left/right edge of the whole figure (icons of side parts go outside of it). */
   private final int figureX0;
   private final int figureX1;

   public BodyPartWidget(EnumPlayerPart enumPlayerPart, int i, int j, float f) {
      this.part = enumPlayerPart;
      int[] k = BodyPartLayout.rect(enumPlayerPart);
      this.x0 = i + Math.round(k[0] * f);
      this.y0 = j + Math.round(k[1] * f);
      this.x1 = i + Math.round((k[0] + k[2]) * f);
      this.y1 = j + Math.round((k[1] + k[3]) * f);
      this.figureX0 = i;
      this.figureX1 = i + Math.round(BodyPartLayout.WIDTH * f);
   }

   public boolean contains(double d, double e) {
      return d >= this.x0 && d < this.x1 && e >= this.y0 && e < this.y1;
   }

   public static List<ConditionSnapshot> conditionsOn(EnumPlayerPart enumPlayerPart, List<ConditionSnapshot> list) {
      ArrayList arrayList = new ArrayList();

      for (ConditionSnapshot conditionSnapshot : list) {
         if (conditionSnapshot.affects(enumPlayerPart)) {
            arrayList.add(conditionSnapshot);
         }
      }

      return arrayList;
   }

   public void render(GuiGraphics guiGraphics, AbstractDamageablePart abstractDamageablePart, List<ConditionSnapshot> list, boolean bl, int i) {
      float f = abstractDamageablePart.getMaxHealth();
      float g = abstractDamageablePart.currentHealth;
      int j = MedicalGuiUtil.healthColor(g, f);
      float h = 0.9F;
      boolean bl1 = false;
      boolean bl2 = false;
      int k = 0;

      for (ConditionSnapshot conditionSnapshot : list) {
         bl1 |= conditionSnapshot.hasFlag(4);
         bl2 |= conditionSnapshot.hasFlag(2);
         if (conditionSnapshot.partTint() >>> 24 > k >>> 24) {
            k = conditionSnapshot.partTint();
         }
      }

      if (MedicalGuiUtil.isCritical(g, f) && !bl1) {
         h = 0.45F + 0.55F * MedicalGuiUtil.pulse(1000L);
      }

      if (i == 2) {
         h *= 0.35F;
      }

      // parts touch each other in the layout; the 1px dark frame inside each rect keeps them apart
      int a0 = this.x0 + 1;
      int b0 = this.y0 + 1;
      int a1 = this.x1 - 1;
      int b1 = this.y1 - 1;
      guiGraphics.fill(this.x0, this.y0, this.x1, this.y1, -15724528);
      if (bl1) {
         guiGraphics.fill(a0, b0, a1, b1, 1612718112);
         MedicalGuiUtil.cross(guiGraphics, a0, b0, a1, b1, -5234656);
      } else {
         // three tones: lit top / left edge, base, shaded bottom / right edge (pixel bevel)
         int bw = Math.max(1, Math.min(a1 - a0, b1 - b0) / 10);
         int lit = MedicalGuiUtil.withAlpha(shade(j, 1.3F), h);
         int dark = MedicalGuiUtil.withAlpha(shade(j, 0.7F), h);
         guiGraphics.fill(a0, b0, a1 - bw, b0 + bw, lit);
         guiGraphics.fill(a0, b0 + bw, a0 + bw, b1 - bw, lit);
         guiGraphics.fill(a1 - bw, b0, a1, b1, dark);
         guiGraphics.fill(a0, b1 - bw, a1 - bw, b1, dark);
         guiGraphics.fill(a0 + bw, b0 + bw, a1 - bw, b1 - bw, MedicalGuiUtil.withAlpha(j, h));
         if (k != 0) {
            float n = (k >>> 24) / 255.0F;
            if (bl2) {
               n = Math.min(1.0F, n * (0.8F + 0.2F * MedicalGuiUtil.pulse(1400L)));
            }

            guiGraphics.fill(a0, b0, a1, b1, MedicalGuiUtil.withAlpha(k & 16777215, n));
         }

      }

      if (abstractDamageablePart.activeHealer != null && !bl1) {
         int o = (this.x0 + this.x1) / 2;
         int r = (this.y0 + this.y1) / 2;
         guiGraphics.fill(o - 2, r, o + 3, r + 1, -1);
         guiGraphics.fill(o, r - 2, o + 1, r + 3, -1);
      }

      if (i == 1) {
         float p = 0.4F + 0.6F * MedicalGuiUtil.pulse(1200L);
         MedicalGuiUtil.outline(guiGraphics, this.x0 - 2, this.y0 - 2, this.x1 + 2, this.y1 + 2, MedicalGuiUtil.withAlpha(5635925, p));
      }

      if (bl) {
         MedicalGuiUtil.outline(guiGraphics, this.x0 - 1, this.y0 - 1, this.x1 + 1, this.y1 + 1, -1);
      }
      // condition icons: outside the figure on the part's own side, inside for the body
      int q = switch (this.part) {
         case LEFT_ARM, LEFT_LEG, LEFT_FOOT -> this.figureX0 - 11;
         case BODY -> this.x0 + 3;
         case HEAD -> this.x1 + 2;
         default -> this.figureX1 + 2;
      };
      int s = this.part == EnumPlayerPart.BODY ? this.y0 + 3 : this.y0;

      for (ConditionSnapshot conditionSnapshot1 : list) {
         if ((conditionSnapshot1.icon() != null || !conditionSnapshot1.hasFlag(4))
            && (this.part != EnumPlayerPart.LEFT_FOOT || !conditionSnapshot1.affects(EnumPlayerPart.LEFT_LEG))
            && (this.part != EnumPlayerPart.RIGHT_FOOT || !conditionSnapshot1.affects(EnumPlayerPart.RIGHT_LEG))) {
            float l = conditionSnapshot1.isRisk() ? 0.25F + 0.75F * MedicalGuiUtil.pulse(600L) : 0.45F + 0.55F * MedicalGuiUtil.pulse(1000L);
            if (conditionSnapshot1.hasFlag(com.duckerquacker.lastaid.common.medical.bone.Bones.FLAG_BLINK)) {
               // fracture without a splint: hard blink as a warning
               l = System.currentTimeMillis() / 350L % 2L == 0L ? 1.0F : 0.12F;
            }
            if (conditionSnapshot1.icon() != null) {
               MedicalGuiUtil.drawIcon(guiGraphics, conditionSnapshot1.icon(), q, s, 9, l);
            } else {
               int m = MedicalGuiUtil.conditionColor(conditionSnapshot1);
               MedicalGuiUtil.fillCircle(guiGraphics, q + 4, s + 4, 3, MedicalGuiUtil.withAlpha(0, 0.6F * l));
               MedicalGuiUtil.fillCircle(guiGraphics, q + 4, s + 4, 2, MedicalGuiUtil.withAlpha(m, l));
            }

            s += 10;
         }
      }
   }


   /** Same hue, brighter (> 1) or darker (< 1); RGB only. */
   private static int shade(int rgb, float factor) {
      int r = Math.min(255, Math.round((rgb >> 16 & 255) * factor));
      int g = Math.min(255, Math.round((rgb >> 8 & 255) * factor));
      int b = Math.min(255, Math.round((rgb & 255) * factor));
      return r << 16 | g << 8 | b;
   }
}
