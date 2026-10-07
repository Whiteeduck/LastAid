package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import com.duckerquacker.lastaid.common.network.AmputationFeedbackPacket;
import com.duckerquacker.lastaid.common.network.LimbStatePacket;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Permanent consequences of an amputation:
 * <ul>
 *    <li>-maxHpLossPerLimb max health per amputated limb (attribute modifier, recomputed from the limb state every
 *    second, so it is also restored after a relog and removed by /medical restore)</li>
 *    <li>pain spike: agony for amputationPeakPainDuration seconds, then linear fade over amputationPainDecayDuration</li>
 *    <li>Slowness II + Weakness I and a camera shake while the spike lasts</li>
 * </ul>
 */
public final class Amputation {
   public static final ResourceLocation MAX_HEALTH_ID = ResourceLocation.fromNamespaceAndPath("lastaid", "amputation_max_health");

   private Amputation() {
   }

   private static MedicalConfig.Amputation cfg() {
      return MedicalConfig.SERVER.amputation;
   }

   private static MedicalConfig.Pain painCfg() {
      return MedicalConfig.SERVER.pain;
   }

   public static int count(Player player) {
      int count = 0;
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         if (MedicalHooks.limbStatus(player, limb) == LimbVisualState.AMPUTATED) {
            count++;
         }
      }
      return count;
   }

   /** Max health currently removed by our modifier (works on both sides, the attribute is synced). */
   public static float maxHealthLoss(@Nullable Player player) {
      if (player == null) {
         return 0.0F;
      }
      AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
      if (instance == null) {
         return 0.0F;
      }
      AttributeModifier modifier = instance.getModifier(MAX_HEALTH_ID);
      return modifier == null ? 0.0F : (float)Math.max(0.0, -modifier.amount());
   }

   public static void updateMaxHealth(ServerPlayer player) {
      AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
      if (instance == null) {
         return;
      }
      AttributeModifier existing = instance.getModifier(MAX_HEALTH_ID);
      double current = existing == null ? 0.0 : existing.amount();
      double target = 0.0;
      int count = MedicalConfig.SERVER.enabled.get() ? count(player) : 0;
      if (count > 0) {
         double withoutOurs = instance.getValue() - current;
         double allowed = Math.max(0.0, withoutOurs - cfg().minMaxHp.get());
         target = -Math.min(count * cfg().maxHpLossPerLimb.get(), allowed);
      }
      if (existing != null && Math.abs(current - target) < 1.0E-4) {
         return;
      }
      if (existing == null && target == 0.0) {
         return;
      }
      instance.removeModifier(MAX_HEALTH_ID);
      if (target < 0.0) {
         instance.addPermanentModifier(new AttributeModifier(MAX_HEALTH_ID, target, AttributeModifier.Operation.ADD_VALUE));
      }
   }

   /** Pain level (0-5) caused by fresh amputations right now. */
   public static int spikePainLevel(ServerPlayer player) {
      Frostbite.State state = Frostbite.INSTANCE.state(player);
      long now = player.level().getGameTime();
      long peak = painCfg().amputationPeakPainDuration.get() * 20L;
      long decay = painCfg().amputationPainDecayDuration.get() * 20L;
      int level = 0;
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         Frostbite.LimbState limbState = state.get(limb);
         if (!limbState.amputated || limbState.amputatedAt < 0L) {
            continue;
         }
         long dt = now - limbState.amputatedAt;
         if (dt < 0L) {
            continue;
         }
         if (dt < peak) {
            level = 5;
         } else if (dt < peak + decay && decay > 0L) {
            float left = 1.0F - (float)(dt - peak) / (float)decay;
            level = Math.max(level, (int)Math.ceil(5.0F * left));
         }
      }
      return level;
   }

   public static void onAmputated(ServerPlayer player, Frostbite.Limb limb) {
      int peakTicks = painCfg().amputationPeakPainDuration.get() * 20;
      if (peakTicks > 0) {
         player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, peakTicks, 1, false, false, true));
         player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, peakTicks, 0, false, false, true));
      }
      updateMaxHealth(player);
      syncLimbs(player, 0);
      // the shake after the cut is only for the patient, and a painkiller dulls it
      sendFeedback(player, AmputationFeedbackPacket.AGONY, Math.max(peakTicks, 20));
   }

   /** How much of the amputation feedback the patient gets (painkiller / morphine dull it). */
   public static byte feedbackStrength(ServerPlayer patient) {
      if (!cfg().painkillerBlocksAmputationFeedback.get()) {
         return AmputationFeedbackPacket.FULL;
      }
      // morphine in the body in any dose (overdose pool) also numbs
      if (patient.hasEffect(RegistryObjects.PAINKILLER_EFFECT) || patient.hasEffect(RegistryObjects.MORPHINE_EFFECT)
         || com.duckerquacker.lastaid.common.medical.overdose.Overdose.INSTANCE.state(patient).morphine > 0.0F) {
         return AmputationFeedbackPacket.NUMB;
      }
      if (CommonUtils.getDamageModel(patient) instanceof PlayerDamageModel model
         && (model.getPendingPainkillerTicks() > 0 || model.getPendingMorphineDelayTicks() > 0)) {
         return AmputationFeedbackPacket.PARTIAL;
      }
      return AmputationFeedbackPacket.FULL;
   }

   /** Sends the feedback phase to the patient only. */
   public static void sendFeedback(ServerPlayer patient, byte phase, int ticks) {
      LastAid.NETWORKING.sendAmputationFeedback(patient, new AmputationFeedbackPacket(phase, feedbackStrength(patient), ticks));
   }

   /** Sends the limb state to the player and everyone tracking them. */
   public static void syncLimbs(ServerPlayer player, int shakeTicks) {
      byte[] bytes = LimbVisualState.encode(player);
      MedicalManager.context(player).lastLimbState = bytes;
      LastAid.NETWORKING.sendLimbStateToAll(player, new LimbStatePacket(player.getId(), bytes, shakeTicks));
   }

   /** Called every second: resend only when something changed. */
   public static void tick(ServerPlayer player, PlayerMedicalContext context) {
      byte[] bytes = LimbVisualState.encode(player);
      if (context.lastLimbState == null || !java.util.Arrays.equals(bytes, context.lastLimbState)) {
         context.lastLimbState = bytes;
         LastAid.NETWORKING.sendLimbStateToAll(player, new LimbStatePacket(player.getId(), bytes, 0));
      }
      updateMaxHealth(player);
   }
}
