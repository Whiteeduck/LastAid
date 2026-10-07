package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Data of the head bandage while it is worn (item custom data, so every client can draw it):
 * tier 1 = light (one strip), 2 = full (plus a cross over the top), blood 0-1 and the game time it was put on
 * (the blood fades over {@code [bandage_head] bloodDecayDays} but never fully). Taking it off makes it clean again.
 */
public final class HeadBandage {
   public static final String TAG_TIER = "lastaid_tier";
   public static final String TAG_BLOOD = "lastaid_blood";
   public static final String TAG_BLOOD_AT = "lastaid_blood_at";

   private HeadBandage() {
   }

   public static boolean isBandage(ItemStack stack) {
      return !stack.isEmpty() && stack.is(MedicalRegistry.HEAD_BANDAGE.get());
   }

   public static boolean wearing(Player player) {
      return isBandage(player.getItemBySlot(EquipmentSlot.HEAD));
   }

   /** 0 = not worn / unknown, 1 light, 2 full. */
   public static int tier(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data == null ? 0 : data.copyTag().getInt(TAG_TIER);
   }

   public static float blood(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data == null ? 0.0F : data.copyTag().getFloat(TAG_BLOOD);
   }

   public static long bloodAt(ItemStack stack) {
      CustomData data = stack.get(DataComponents.CUSTOM_DATA);
      return data == null ? 0L : data.copyTag().getLong(TAG_BLOOD_AT);
   }

   /** Visible blood 0-1: fades to 35 % over bloodDecayDays. */
   public static float visibleBlood(ItemStack stack, long gameTime) {
      float blood = blood(stack);
      if (blood <= 0.0F) {
         return 0.0F;
      }
      double days = MedicalConfig.SERVER_SPEC.isLoaded() ? MedicalConfig.SERVER.bandageHead.bloodDecayDays.get() : 1.0;
      float t = days <= 0.0 ? 1.0F : (float)Math.min(1.0, Math.max(0.0, (gameTime - bloodAt(stack)) / (days * 24000.0)));
      return blood * (1.0F - 0.65F * t);
   }

   /** Once per second (server). */
   public static void tick(ServerPlayer player) {
      ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
      if (isBandage(head) && tier(head) == 0) {
         boolean bySeverity = MedicalConfig.SERVER.bandageHead.bandageTierFromSeverity.get();
         int tier = bySeverity && Concussion.INSTANCE.state(player).severity >= 2 ? 2 : 1;
         float blood = 0.0F;
         AbstractPlayerDamageModel model = CommonUtils.getDamageModel(player);
         if (model != null) {
            AbstractDamageablePart part = model.getFromEnum(EnumPlayerPart.HEAD);
            float missing = 1.0F - part.currentHealth / Math.max(1.0F, part.getMaxHealth());
            if (missing > 0.05F) {
               // a wounded head bleeds into the fresh gauze
               blood = Math.min(1.0F, 0.35F + missing);
            }
         }
         CompoundTag tag = new CompoundTag();
         tag.putInt(TAG_TIER, tier);
         tag.putFloat(TAG_BLOOD, blood);
         tag.putLong(TAG_BLOOD_AT, player.level().getGameTime());
         CustomData.set(DataComponents.CUSTOM_DATA, head, tag);
      }
      // taken off: a fresh, clean bandage again
      for (ItemStack stack : player.getInventory().items) {
         if (isBandage(stack) && stack.has(DataComponents.CUSTOM_DATA)) {
            stack.remove(DataComponents.CUSTOM_DATA);
         }
      }
      for (ItemStack stack : player.getInventory().offhand) {
         if (isBandage(stack) && stack.has(DataComponents.CUSTOM_DATA)) {
            stack.remove(DataComponents.CUSTOM_DATA);
         }
      }
   }
}
