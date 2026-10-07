package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-limb status that every client needs (to hide amputated limbs and tint frostbitten ones on player models,
 * and to keep the local pain calculation in line with the server).
 * <p>
 * One byte per {@link Frostbite.Limb} (ordinal order): 0-4 = frostbite stage, {@link #AMPUTATED} = amputated.
 * This class holds only plain data so it can live in common code; the client side is filled by
 * {@link com.duckerquacker.lastaid.common.network.LimbStatePacket}.
 */
public final class LimbVisualState {
   public static final byte AMPUTATED = 5;
   public static final byte[] NONE = new byte[Frostbite.Limb.VALUES.length];
   private static final Map<Integer, byte[]> CLIENT = new ConcurrentHashMap<>();
   /** Client: game tick (client level time) until which the local camera shakes after an amputation. */
   private static volatile long shakeUntil;
   private static volatile int shakeDuration;
   private static volatile float shakeStrength = 1.0F;

   private LimbVisualState() {
   }

   /**
    * Bits 0-2: frostbite stage 0-4 or {@link #AMPUTATED}; bits 3-6: bones (see
    * {@link com.duckerquacker.lastaid.common.medical.bone.Bones#encode}: fracture type, splinted, dislocated).
    */
   public static byte[] encode(net.minecraft.world.entity.player.Player player) {
      Frostbite.State state = Frostbite.INSTANCE.state(player);
      byte[] bytes = new byte[Frostbite.Limb.VALUES.length + 1];
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         Frostbite.LimbState limbState = state.get(limb);
         int base = limbState.amputated ? AMPUTATED : Math.max(0, Math.min(4, limbState.stage));
         int bones = limbState.amputated ? 0 : com.duckerquacker.lastaid.common.medical.bone.Bones.encode(player, limb);
         bytes[limb.ordinal()] = (byte)(base | (bones & 31) << 3);
      }
      // last byte: highest bleeding stage (blood drops on the ground for every client)
      bytes[Frostbite.Limb.VALUES.length] = (byte)(com.duckerquacker.lastaid.common.medical.wounds.Wounds.enabled()
         ? com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.bleedLevel(player) : 0);
      return bytes;
   }

   public static void setClient(int entityId, byte[] bytes) {
      boolean empty = true;
      for (byte b : bytes) {
         if (b != 0) {
            empty = false;
            break;
         }
      }
      if (empty) {
         CLIENT.remove(entityId);
      } else {
         CLIENT.put(entityId, bytes);
      }
   }

   /** Frostbite stage 0-4 or {@link #AMPUTATED}. */
   public static byte client(int entityId, Frostbite.Limb limb) {
      byte[] bytes = CLIENT.get(entityId);
      return bytes == null ? 0 : (byte)(bytes[limb.ordinal()] & 7);
   }

   /** Bone bits: fracture type (0-3) | 4 splinted | 8 dislocated | 16 bleeding. */
   public static int clientBones(int entityId, Frostbite.Limb limb) {
      byte[] bytes = CLIENT.get(entityId);
      return bytes == null ? 0 : (bytes[limb.ordinal()] >> 3) & 31;
   }

   /** Highest bleeding stage 0-4 of that player (client copy). */
   public static int clientBleed(int entityId) {
      byte[] bytes = CLIENT.get(entityId);
      return bytes == null || bytes.length <= Frostbite.Limb.VALUES.length ? 0 : bytes[Frostbite.Limb.VALUES.length];
   }

   public static java.util.Set<Integer> clientEntities() {
      return CLIENT.keySet();
   }

   public static boolean clientHasAny(int entityId) {
      return CLIENT.containsKey(entityId);
   }

   public static void startShake(long untilGameTime, int duration) {
      startShake(untilGameTime, duration, 1.0F);
   }

   public static void startShake(long untilGameTime, int duration, float strength) {
      shakeUntil = untilGameTime;
      shakeDuration = Math.max(1, duration);
      shakeStrength = strength;
   }

   public static float shakeStrength() {
      return shakeStrength;
   }

   public static long shakeUntil() {
      return shakeUntil;
   }

   public static int shakeDuration() {
      return shakeDuration;
   }

   public static void clearClient() {
      CLIENT.clear();
      shakeUntil = 0L;
   }
}
