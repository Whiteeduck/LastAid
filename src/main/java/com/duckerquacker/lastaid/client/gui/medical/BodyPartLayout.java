package com.duckerquacker.lastaid.client.gui.medical;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;

/**
 * One body layout for everything that draws or hit-tests the body: the H panel, the First Aid HUD figure and the
 * click logic. It is the First Aid HUD figure (Minecraft skin proportions, 32 x 64 units).
 * <p>
 * Orientation: seen from BEHIND, like the third person camera. The player's LEFT arm/leg is on the LEFT side of the
 * screen, so "my left arm" is always on the left - in the panel and in the HUD.
 */
public final class BodyPartLayout {
   public static final int WIDTH = 32;
   public static final int HEIGHT = 64;
   private static final int[][] RECTS = new int[EnumPlayerPart.VALUES.length][];

   private BodyPartLayout() {
   }

   /** {x, y, width, height} in layout units. */
   public static int[] rect(EnumPlayerPart part) {
      return RECTS[part.ordinal()];
   }

   /** True for parts drawn on the left half of the figure. */
   public static boolean onLeftSide(EnumPlayerPart part) {
      return switch (part) {
         case LEFT_ARM, LEFT_LEG, LEFT_FOOT -> true;
         default -> false;
      };
   }

   public static boolean isSidePart(EnumPlayerPart part) {
      return part != EnumPlayerPart.HEAD && part != EnumPlayerPart.BODY;
   }

   static {
      RECTS[EnumPlayerPart.HEAD.ordinal()] = new int[]{8, 0, 16, 16};
      RECTS[EnumPlayerPart.BODY.ordinal()] = new int[]{8, 16, 16, 24};
      RECTS[EnumPlayerPart.LEFT_ARM.ordinal()] = new int[]{0, 16, 8, 24};
      RECTS[EnumPlayerPart.RIGHT_ARM.ordinal()] = new int[]{24, 16, 8, 24};
      RECTS[EnumPlayerPart.LEFT_LEG.ordinal()] = new int[]{8, 40, 8, 16};
      RECTS[EnumPlayerPart.RIGHT_LEG.ordinal()] = new int[]{16, 40, 8, 16};
      RECTS[EnumPlayerPart.LEFT_FOOT.ordinal()] = new int[]{8, 56, 8, 8};
      RECTS[EnumPlayerPart.RIGHT_FOOT.ordinal()] = new int[]{16, 56, 8, 8};
   }
}
