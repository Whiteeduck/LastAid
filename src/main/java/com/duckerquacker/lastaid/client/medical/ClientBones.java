package com.duckerquacker.lastaid.client.medical;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.client.gui.medical.BodyStatusScreen;
import com.duckerquacker.lastaid.common.medical.LimbVisualState;
import com.duckerquacker.lastaid.common.medical.bone.Bones;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.network.MedicalActionPacket;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/** Client helpers for fractures / dislocations (panel actions, synced limb bits). */
public final class ClientBones {
   private ClientBones() {
   }

   public static void openSplintPanel(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      AbstractPlayerDamageModel model = CommonUtils.getDamageModel(mc.player);
      if (model != null) {
         mc.setScreen(new BodyStatusScreen(model, hand));
      }
   }

   public static int bits(Entity entity, Frostbite.Limb limb) {
      return LimbVisualState.clientBones(entity.getId(), limb);
   }

   public static int fractureType(Entity entity, Frostbite.Limb limb) {
      return bits(entity, limb) & 3;
   }

   public static boolean splinted(Entity entity, Frostbite.Limb limb) {
      return (bits(entity, limb) & Bones.BIT_SPLINT) != 0;
   }

   public static boolean dislocated(Entity entity, Frostbite.Limb limb) {
      return (bits(entity, limb) & Bones.BIT_DISLOCATED) != 0;
   }

   /** Closed / open fracture or dislocation. */
   public static boolean out(Entity entity, Frostbite.Limb limb) {
      return fractureType(entity, limb) >= 2 || dislocated(entity, limb);
   }

   public static void sendSplint(Frostbite.Limb limb, InteractionHand hand) {
      LastAid.NETWORKING.sendToServer(new MedicalActionPacket(MedicalActionPacket.SPLINT, (byte)limb.ordinal(), (byte)(hand == InteractionHand.OFF_HAND ? 1 : 0)));
   }

   public static void sendReduce(Frostbite.Limb limb) {
      LastAid.NETWORKING.sendToServer(new MedicalActionPacket(MedicalActionPacket.REDUCE, (byte)limb.ordinal(), (byte)0));
   }

   /** Panel button on a wound: extract arrow, tourniquet, suture, alcohol, take off the dressing. */
   public static void sendWoundAction(int zone, int action) {
      LastAid.NETWORKING.sendToServer(new MedicalActionPacket(MedicalActionPacket.WOUND, (byte)zone, (byte)action));
   }
}
