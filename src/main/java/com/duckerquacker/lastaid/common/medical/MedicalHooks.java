package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalConditions;
import com.duckerquacker.lastaid.common.init.FirstAidDataAttachments;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class MedicalHooks {
   private MedicalHooks() {
   }

   public static MedicalData data(Player player) {
      return (MedicalData)player.getData(FirstAidDataAttachments.MEDICAL);
   }

   private static boolean serverActive(@Nullable Player player) {
      return player instanceof ServerPlayer && MedicalConfig.SERVER_SPEC.isLoaded() && (Boolean)MedicalConfig.SERVER.enabled.get();
   }

   public static boolean blocksPassiveHeadRegen(Player player) {
      return !serverActive(player) ? false : Concussion.INSTANCE.state(player).severity >= 3;
   }

   public static boolean blocksHealing(@Nullable Player player, EnumPlayerPart enumPlayerPart) {
      if (player == null) {
         return false;
      } else if (player.level().isClientSide()) {
         for (ConditionSnapshot conditionSnapshot : ClientMedicalState.get().conditions()) {
            if (conditionSnapshot.affects(enumPlayerPart) && conditionSnapshot.hasFlag(2)) {
               return true;
            }
         }

         return false;
      } else if (!serverActive(player)) {
         return false;
      } else {
         MedicalData medicalData = data(player);

         for (MedicalCondition medicalCondition : MedicalConditions.all()) {
            if (blocks((ServerPlayer)player, medicalCondition, medicalData, enumPlayerPart)) {
               return true;
            }
         }

         return false;
      }
   }

   private static <S extends ConditionState> boolean blocks(
      ServerPlayer serverPlayer, MedicalCondition<S> medicalCondition, MedicalData medicalData, EnumPlayerPart enumPlayerPart
   ) {
      return medicalCondition.blocksHealing(serverPlayer, medicalData.getState(medicalCondition), enumPlayerPart);
   }

   public static float partDamageMultiplier(@Nullable Player player, EnumPlayerPart enumPlayerPart) {
      if (!serverActive(player)) {
         return 1.0F;
      } else {
         MedicalData medicalData = data(player);
         float f = 1.0F;

         for (MedicalCondition medicalCondition : MedicalConditions.all()) {
            f *= damageMultiplier((ServerPlayer)player, medicalCondition, medicalData, enumPlayerPart);
         }

         return f;
      }
   }

   private static <S extends ConditionState> float damageMultiplier(
      ServerPlayer serverPlayer, MedicalCondition<S> medicalCondition, MedicalData medicalData, EnumPlayerPart enumPlayerPart
   ) {
      return medicalCondition.incomingDamageMultiplier(serverPlayer, medicalData.getState(medicalCondition), enumPlayerPart);
   }

   public static int movementFlags(ServerPlayer serverPlayer) {
      if (!serverActive(serverPlayer)) {
         return 0;
      } else {
         MedicalData medicalData = data(serverPlayer);
         int i = 0;

         for (MedicalCondition medicalCondition : MedicalConditions.all()) {
            i |= moveFlags(serverPlayer, medicalCondition, medicalData);
         }

         return i | com.duckerquacker.lastaid.common.medical.carry.CarryManager.movementFlags(serverPlayer);
      }
   }

   private static <S extends ConditionState> int moveFlags(ServerPlayer serverPlayer, MedicalCondition<S> medicalCondition, MedicalData medicalData) {
      return medicalCondition.movementFlags(serverPlayer, medicalData.getState(medicalCondition));
   }

   @Nullable
   public static Pose idleForcedPose(Player player) {
      if (player.isPassenger()) {
         return null; // carried in someone's arms: no crawling pose
      }
      int i;
      if (player.level().isClientSide()) {
         i = ClientMedicalState.movementFlagsFor(player);
      } else if (player instanceof ServerPlayer serverPlayer) {
         i = movementFlags(serverPlayer);
      } else {
         i = 0;
      }

      return (i & 4) != 0 ? Pose.SWIMMING : null;
   }

   private static boolean medicalEnabledAnySide() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && (Boolean)MedicalConfig.SERVER.enabled.get();
   }

   /** 0-4 frostbite stage or {@link LimbVisualState#AMPUTATED}. Works on both sides (client uses the synced copy). */
   public static byte limbStatus(@Nullable Player player, com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb limb) {
      if (player == null || limb == null) {
         return 0;
      } else if (player.level().isClientSide()) {
         return LimbVisualState.client(player.getId(), limb);
      } else if (!serverActive(player)) {
         return 0;
      } else {
         com.duckerquacker.lastaid.common.medical.cold.Frostbite.LimbState limbState = com.duckerquacker.lastaid.common.medical.cold.Frostbite.INSTANCE
            .state(player)
            .get(limb);
         return limbState.amputated ? LimbVisualState.AMPUTATED : (byte)limbState.stage;
      }
   }

   public static boolean isAmputated(@Nullable Player player, EnumPlayerPart part) {
      return limbStatus(player, com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.of(part)) == LimbVisualState.AMPUTATED;
   }

   /** Amputated or gangrenous (frostbite stage 4). Such parts are left out of First Aid's own pain formula. */
   public static boolean isLimbDead(@Nullable Player player, EnumPlayerPart part) {
      return limbStatus(player, com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.of(part)) >= 4;
   }

   /**
    * Minimum pain level from medical conditions:
    * gangrene (stage 4, not amputated), frostbite stage 3, a healed stump and the fresh amputation spike.
    */
   public static int painFloor(@Nullable Player player) {
      if (player == null || !medicalEnabledAnySide()) {
         return 0;
      }
      MedicalConfig.Pain cfg = MedicalConfig.SERVER.pain;
      int floor = 0;
      int stage3 = 0;
      for (com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb limb : com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.VALUES) {
         byte status = limbStatus(player, limb);
         if (status == LimbVisualState.AMPUTATED) {
            floor = Math.max(floor, cfg.deadLimbPainLevel.get());
         } else if (status == 4) {
            floor = Math.max(floor, cfg.gangrenePainLevel.get());
         } else if (status == 3) {
            stage3++;
         }
      }
      if (stage3 > 0 && cfg.frostbiteStage3PainLevel.get() > 0) {
         floor = Math.max(floor, Math.min(5, cfg.frostbiteStage3PainLevel.get() + (stage3 >= 2 ? 1 : 0)));
      }
      if (player instanceof ServerPlayer serverPlayer) {
         floor = Math.max(floor, Amputation.spikePainLevel(serverPlayer));
      }
      floor = Math.max(floor, com.duckerquacker.lastaid.common.medical.bone.Bones.painFloor(player));
      floor = Math.max(floor, com.duckerquacker.lastaid.common.medical.wounds.Wounds.painFloor(player));
      return Math.min(5, floor);
   }

   /** Painkillers (not morphine, First Aid handles that itself) lower the pain level by painkillerReduction. */
   public static int applyPainkiller(@Nullable Player player, int level) {
      if (player == null || level <= 0 || !player.hasEffect(com.duckerquacker.lastaid.common.RegistryObjects.PAINKILLER_EFFECT)) {
         return level;
      }
      double reduction = MedicalConfig.SERVER_SPEC.isLoaded() ? MedicalConfig.SERVER.pain.painkillerReduction.get() : 0.6;
      return Math.max(0, Math.min(5, (int)Math.round(level * (1.0 - reduction))));
   }

}
