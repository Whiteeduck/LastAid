package com.duckerquacker.lastaid.common.medical;

import net.minecraft.resources.ResourceLocation;

public final class MedicalIcons {
   public static final ResourceLocation BRAIN = icon("brain");
   public static final ResourceLocation THERMOMETER = icon("thermometer");
   public static final ResourceLocation SNOWFLAKE = icon("snowflake");
   public static final ResourceLocation CUP = icon("cup");
   public static final ResourceLocation SWEATER = icon("sweater");
   public static final ResourceLocation SHIELD = icon("shield");
   public static final ResourceLocation DROP = icon("drop");
   public static final ResourceLocation PILL = icon("pill");

   private MedicalIcons() {
   }

   private static ResourceLocation icon(String string) {
      return ResourceLocation.fromNamespaceAndPath("lastaid", "textures/gui/conditions/" + string + ".png");
   }
}
