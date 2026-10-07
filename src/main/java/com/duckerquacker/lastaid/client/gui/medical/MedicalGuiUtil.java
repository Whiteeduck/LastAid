package com.duckerquacker.lastaid.client.gui.medical;

import com.mojang.blaze3d.systems.RenderSystem;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class MedicalGuiUtil {
   private static List<? extends String> cachedColorConfig;
   private static Map<String, Integer> cachedColors = Map.of();

   private MedicalGuiUtil() {
   }

   public static float pulse(long l) {
      double d = (double)(System.currentTimeMillis() % l) / l;
      return (float)(0.5 + 0.5 * Math.sin(d * Math.PI * 2.0));
   }

   public static int withAlpha(int i, float f) {
      int j = Mth.clamp((int)(f * 255.0F), 0, 255);
      return j << 24 | i & 16777215;
   }

   public static int healthColor(float f, float g) {
      float h = g <= 0.0F ? 0.0F : f / g;
      if (h >= 0.999F) {
         return 4177727;
      } else if (h >= 0.75F) {
         return 10145074;
      } else if (h >= 0.5F) {
         return 15127354;
      } else if (h >= 0.25F) {
         return 15764520;
      } else {
         return h >= 0.125F ? 14168112 : 7997962;
      }
   }

   public static boolean isCritical(float f, float g) {
      return g > 0.0F && f / g < 0.125F;
   }

   public static int conditionColor(ConditionSnapshot conditionSnapshot) {
      Map map = colorOverrides();
      Integer integer = (Integer)map.get(conditionSnapshot.id().getPath());
      if (integer == null) {
         integer = (Integer)map.get(conditionSnapshot.id().toString());
      }

      return integer != null ? integer : conditionSnapshot.color() & 16777215;
   }

   private static Map<String, Integer> colorOverrides() {
      if (!MedicalConfig.CLIENT_SPEC.isLoaded()) {
         return Map.of();
      } else {
         List<? extends String> list = MedicalConfig.CLIENT.conditionColors.get();
         if (list != cachedColorConfig) {
            HashMap<String, Integer> hashMap = new HashMap<>();

            for (String string : list) {
               int i = string.indexOf(61);
               if (i > 0) {
                  String string1 = string.substring(i + 1).trim().replace("#", "");

                  try {
                     hashMap.put(string.substring(0, i).trim(), Integer.parseInt(string1, 16) & 16777215);
                  } catch (NumberFormatException numberFormatException) {
                  }
               }
            }

            cachedColors = hashMap;
            cachedColorConfig = list;
         }

         return cachedColors;
      }
   }

   public static void fillCircle(GuiGraphics guiGraphics, int i, int j, int k, int l) {
      for (int m = -k; m <= k; m++) {
         int n = (int)Math.floor(Math.sqrt(k * k - m * m + 0.5));
         guiGraphics.fill(i - n, j + m, i + n + 1, j + m + 1, l);
      }
   }

   public static void outline(GuiGraphics guiGraphics, int i, int j, int k, int l, int m) {
      guiGraphics.fill(i, j, k, j + 1, m);
      guiGraphics.fill(i, l - 1, k, l, m);
      guiGraphics.fill(i, j, i + 1, l, m);
      guiGraphics.fill(k - 1, j, k, l, m);
   }

   public static void bar(GuiGraphics guiGraphics, int i, int j, int k, int l, float f, int m) {
      guiGraphics.fill(i, j, i + k, j + l, Integer.MIN_VALUE);
      int n = Math.round(k * Mth.clamp(f, 0.0F, 1.0F));
      if (n > 0) {
         guiGraphics.fill(i, j, i + n, j + l, 0xFF000000 | m);
      }
   }

   public static void drawIcon(GuiGraphics guiGraphics, ResourceLocation resourceLocation, int i, int j, int k, float f) {
      RenderSystem.enableBlend();
      guiGraphics.setColor(1.0F, 1.0F, 1.0F, f);
      guiGraphics.blit(resourceLocation, i, j, k, k, 0.0F, 0.0F, 16, 16, 16, 16);
      guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
   }

   public static void cross(GuiGraphics guiGraphics, int i, int j, int k, int l, int m) {
      int n = k - i;
      int o = l - j;
      int p = Math.max(n, o);

      for (int q = 0; q <= p; q++) {
         int r = i + q * (n - 1) / Math.max(1, p);
         int s = j + q * (o - 1) / Math.max(1, p);
         int t = l - 1 - q * (o - 1) / Math.max(1, p);
         guiGraphics.fill(r, s, r + 2, s + 2, m);
         guiGraphics.fill(r, t, r + 2, t + 2, m);
      }
   }

   public static int bodyTempColor(float f) {
      if (f < 35.0F) {
         return 8377599;
      } else if (f < 36.5F) {
         return 4251856;
      } else if (f <= 37.5F) {
         return 5635925;
      } else {
         return f <= 38.5F ? 16777045 : 16733525;
      }
   }
}
