package com.duckerquacker.lastaid.common.medical.wounds;

import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/** Body zones for wounds: head, torso, both arms, both legs (a leg zone includes its foot). */
public enum Zone {
   HEAD(EnumPlayerPart.HEAD, null, null),
   BODY(EnumPlayerPart.BODY, null, null),
   RIGHT_ARM(EnumPlayerPart.RIGHT_ARM, null, Frostbite.Limb.RIGHT_ARM),
   LEFT_ARM(EnumPlayerPart.LEFT_ARM, null, Frostbite.Limb.LEFT_ARM),
   RIGHT_LEG(EnumPlayerPart.RIGHT_LEG, EnumPlayerPart.RIGHT_FOOT, Frostbite.Limb.RIGHT_LEG),
   LEFT_LEG(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.LEFT_FOOT, Frostbite.Limb.LEFT_LEG);

   public static final Zone[] VALUES = values();
   public final EnumPlayerPart part;
   @Nullable
   public final EnumPlayerPart extra;
   /** arms and legs only (tourniquet, necrosis) */
   @Nullable
   public final Frostbite.Limb limb;

   Zone(EnumPlayerPart part, @Nullable EnumPlayerPart extra, @Nullable Frostbite.Limb limb) {
      this.part = part;
      this.extra = extra;
      this.limb = limb;
   }

   public boolean contains(EnumPlayerPart p) {
      return p == this.part || p == this.extra;
   }

   public int mask() {
      return this.extra == null ? ConditionSnapshot.mask(this.part) : ConditionSnapshot.mask(this.part, this.extra);
   }

   public String key() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public Component displayName() {
      return Component.translatable("lastaid.zone." + this.key());
   }

   public static Zone of(EnumPlayerPart p) {
      for (Zone zone : VALUES) {
         if (zone.contains(p)) {
            return zone;
         }
      }
      return BODY;
   }

   @Nullable
   public static Zone of(Frostbite.Limb limb) {
      for (Zone zone : VALUES) {
         if (zone.limb == limb) {
            return zone;
         }
      }
      return null;
   }
}
