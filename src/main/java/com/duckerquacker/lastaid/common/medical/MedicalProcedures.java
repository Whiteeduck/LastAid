package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.network.AmputationFeedbackPacket;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class MedicalProcedures {
   private static final Map<UUID, MedicalProcedures.Procedure> ACTIVE = new HashMap<>();

   private MedicalProcedures() {
   }

   public static void start(ServerPlayer serverPlayer, ServerPlayer serverPlayer1, InteractionHand interactionHand) {
      if (serverPlayer == serverPlayer1) {
         serverPlayer.displayClientMessage(Component.translatable("lastaid.medical_kit.not_self").withStyle(ChatFormatting.RED), true);
      } else {
         boolean bl = Concussion.INSTANCE.state(serverPlayer1).severity > 0;
         boolean bl1 = Frostbite.INSTANCE.needsMedicalKit(serverPlayer1) || com.duckerquacker.lastaid.common.medical.bone.Bones.needsMedicalKit(serverPlayer1);
         if (!bl && !bl1) {
            serverPlayer.displayClientMessage(Component.translatable("lastaid.medical.not_needed_kit").withStyle(ChatFormatting.GRAY), true);
         } else {
            ACTIVE.put(
               serverPlayer.getUUID(), new MedicalProcedures.Procedure(MedicalProcedures.Type.MEDICAL_KIT, serverPlayer, serverPlayer1, interactionHand, null)
            );
            serverPlayer.displayClientMessage(
               Component.translatable("lastaid.medical_kit.started", new Object[]{serverPlayer1.getDisplayName()}).withStyle(ChatFormatting.AQUA), true
            );
            serverPlayer1.displayClientMessage(
               Component.translatable("lastaid.medical_kit.patient", new Object[]{serverPlayer.getDisplayName()}).withStyle(ChatFormatting.AQUA), true
            );
         }
      }
   }

   public static void startAmputation(ServerPlayer serverPlayer, ServerPlayer serverPlayer1, InteractionHand interactionHand) {
      if (!(Boolean)MedicalConfig.SERVER.frostbite.permanentDeath.get()) {
         serverPlayer.displayClientMessage(Component.translatable("lastaid.amputation.disabled").withStyle(ChatFormatting.GRAY), true);
      } else {
         Frostbite.Limb limb = Frostbite.INSTANCE.amputationTarget(serverPlayer1);
         if (limb == null) {
            serverPlayer.displayClientMessage(Component.translatable("lastaid.amputation.not_needed").withStyle(ChatFormatting.GRAY), true);
         } else {
            int i = (Integer)MedicalConfig.SERVER.frostbite.amputationBandages.get();
            if (serverPlayer.getAbilities().instabuild
               || count(serverPlayer, (Item)MedicalRegistry.MEDICAL_KIT.get()) >= 1 && count(serverPlayer, (Item)RegistryObjects.BANDAGE.get()) >= i) {
               ACTIVE.put(
                  serverPlayer.getUUID(),
                  new MedicalProcedures.Procedure(MedicalProcedures.Type.AMPUTATION, serverPlayer, serverPlayer1, interactionHand, limb)
               );
               Component component = Frostbite.limbName(limb);
               serverPlayer.displayClientMessage(
                  Component.translatable("lastaid.amputation.started", new Object[]{component}).withStyle(ChatFormatting.DARK_RED), true
               );
               amputationFeedback(ACTIVE.get(serverPlayer.getUUID()), serverPlayer1, (Integer)MedicalConfig.SERVER.frostbite.amputationSeconds.get() * 20);
               if (serverPlayer1 != serverPlayer) {
                  serverPlayer1.displayClientMessage(
                     Component.translatable("lastaid.amputation.patient", new Object[]{serverPlayer.getDisplayName(), component})
                        .withStyle(ChatFormatting.DARK_RED),
                     true
                  );
               }
            } else {
               serverPlayer.displayClientMessage(Component.translatable("lastaid.amputation.missing", new Object[]{i}).withStyle(ChatFormatting.RED), true);
            }
         }
      }
   }

   /** Splint on a limb (5 s, both stand still, the medic holds the splint). medic == target: on yourself. */
   public static void startSplint(ServerPlayer medic, ServerPlayer target, InteractionHand hand, Frostbite.Limb limb) {
      ACTIVE.put(medic.getUUID(), new MedicalProcedures.Procedure(MedicalProcedures.Type.SPLINT, medic, target, hand, limb));
      Component limbName = Frostbite.limbName(limb);
      medic.displayClientMessage(Component.translatable("lastaid.splint.started", limbName).withStyle(ChatFormatting.AQUA), true);
      if (medic != target) {
         target.displayClientMessage(Component.translatable("lastaid.splint.patient", medic.getDisplayName(), limbName).withStyle(ChatFormatting.AQUA), true);
      }
   }

   /** Reducing a dislocation (3 s, both stand still, no item). */
   public static void startReduce(ServerPlayer medic, ServerPlayer target, Frostbite.Limb limb) {
      ACTIVE.put(medic.getUUID(), new MedicalProcedures.Procedure(MedicalProcedures.Type.REDUCE, medic, target, InteractionHand.MAIN_HAND, limb));
      Component limbName = Frostbite.limbName(limb);
      medic.displayClientMessage(Component.translatable("lastaid.reduce.started", limbName).withStyle(ChatFormatting.AQUA), true);
      if (medic != target) {
         target.displayClientMessage(Component.translatable("lastaid.reduce.patient", medic.getDisplayName(), limbName).withStyle(ChatFormatting.AQUA), true);
      }
   }

   /** Pulling an arrow out (extract) or stitching a wound (suture) on yourself: stand still, no item in hand needed. */
   public static void startWoundProcedure(ServerPlayer player, com.duckerquacker.lastaid.common.medical.wounds.Zone zone, boolean extract) {
      MedicalProcedures.Procedure procedure = new MedicalProcedures.Procedure(extract ? MedicalProcedures.Type.EXTRACT : MedicalProcedures.Type.SUTURE, player, player, InteractionHand.MAIN_HAND, zone.limb);
      procedure.zone = zone;
      ACTIVE.put(player.getUUID(), procedure);
      player.displayClientMessage(Component.translatable(extract ? "lastaid.wound.extract_started" : "lastaid.wound.suture_started", zone.displayName()).withStyle(ChatFormatting.AQUA), true);
   }

   public static boolean isBusy(ServerPlayer medic) {
      return ACTIVE.containsKey(medic.getUUID());
   }

   private static int count(Player player, Item item) {
      int i = 0;

      for (ItemStack itemStack : player.getInventory().items) {
         if (itemStack.is(item)) {
            i += itemStack.getCount();
         }
      }

      for (ItemStack itemStack1 : player.getInventory().offhand) {
         if (itemStack1.is(item)) {
            i += itemStack1.getCount();
         }
      }

      return i;
   }

   private static void consume(Player player, Item item, int i) {
      if (!player.getAbilities().instabuild) {
         Inventory inventory = player.getInventory();

         for (int j = 0; j < inventory.getContainerSize() && i > 0; j++) {
            ItemStack itemStack = inventory.getItem(j);
            if (itemStack.is(item)) {
               int k = Math.min(i, itemStack.getCount());
               itemStack.shrink(k);
               i -= k;
            }
         }
      }
   }

   public static void tick(MinecraftServer minecraftServer) {
      if (!ACTIVE.isEmpty()) {
         Iterator iterator = ACTIVE.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry entry = (Entry)iterator.next();
            MedicalProcedures.Procedure procedure = (MedicalProcedures.Procedure)entry.getValue();
            ServerPlayer serverPlayer = minecraftServer.getPlayerList().getPlayer((UUID)entry.getKey());
            ServerPlayer serverPlayer1 = minecraftServer.getPlayerList().getPlayer(procedure.target);
            if (serverPlayer != null
               && serverPlayer1 != null
               && serverPlayer.isAlive()
               && serverPlayer1.isAlive()
               && serverPlayer.level() == serverPlayer1.level()) {
               Item item = switch (procedure.type) {
                  case MEDICAL_KIT -> (Item)MedicalRegistry.MEDICAL_KIT.get();
                  case AMPUTATION -> (Item)MedicalRegistry.SURGICAL_KNIFE.get();
                  case SPLINT -> (Item)MedicalRegistry.SPLINT.get();
                  case REDUCE, EXTRACT, SUTURE -> null;
               };
               ItemStack itemStack = serverPlayer.getItemInHand(procedure.hand);
               if (item != null && !itemStack.is(item)) {
                  cancel(serverPlayer, serverPlayer1, "lastaid.medical_kit.cancel_item");
                  iterator.remove();
               } else {
                  boolean bl = serverPlayer.position().distanceTo(procedure.medicStart) > 0.6
                     || serverPlayer1 != serverPlayer && serverPlayer1.position().distanceTo(procedure.targetStart) > 0.6;
                  if ((serverPlayer1 == serverPlayer || !(serverPlayer.distanceTo(serverPlayer1) > 3.0F)) && !bl) {
                     serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 5, false, false, false));
                     serverPlayer1.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 5, false, false, false));
                     if (procedure.type == MedicalProcedures.Type.MEDICAL_KIT
                        && Concussion.INSTANCE.state(serverPlayer1).severity >= 3
                        && (Boolean)MedicalConfig.SERVER.concussion.requireSecondPlayerForSeverity3.get()
                        && !hasHelper(serverPlayer, serverPlayer1)) {
                        if (!procedure.waitingForHelperNotified) {
                           serverPlayer.displayClientMessage(Component.translatable("lastaid.medical_kit.need_helper").withStyle(ChatFormatting.YELLOW), false);
                           procedure.waitingForHelperNotified = true;
                        }

                        serverPlayer.displayClientMessage(Component.translatable("lastaid.medical_kit.paused").withStyle(ChatFormatting.YELLOW), true);
                     } else {
                        int i = (switch (procedure.type) {
                              case MEDICAL_KIT -> (Integer)MedicalConfig.SERVER.items.medkitSeconds.get();
                              case AMPUTATION -> (Integer)MedicalConfig.SERVER.frostbite.amputationSeconds.get();
                              case SPLINT -> MedicalConfig.SERVER.splint.splintSeconds.get();
                              case REDUCE -> MedicalConfig.SERVER.splint.reduceSeconds.get();
                              case EXTRACT -> MedicalConfig.SERVER.wounds.arrowExtractSeconds.get();
                              case SUTURE -> MedicalConfig.SERVER.wounds.sutureSeconds.get();
                           })
                           * 20;
                        procedure.ticks++;
                        if (procedure.ticks % 20 == 0 && procedure.type == MedicalProcedures.Type.AMPUTATION && procedure.ticks < i) {
                           amputationFeedback(procedure, serverPlayer1, i - procedure.ticks);
                        }
                        if (procedure.ticks % 20 == 0) {
                           int j = procedure.ticks * 100 / i;
                           String string = switch (procedure.type) {
                              case MEDICAL_KIT -> "lastaid.medical_kit.progress";
                              case AMPUTATION -> "lastaid.amputation.progress";
                              case SPLINT -> "lastaid.splint.progress";
                              case REDUCE -> "lastaid.reduce.progress";
                              case EXTRACT -> "lastaid.wound.extract_progress";
                              case SUTURE -> "lastaid.wound.suture_progress";
                           };
                           serverPlayer.displayClientMessage(Component.translatable(string, new Object[]{j}).withStyle(ChatFormatting.AQUA), true);
                           serverPlayer.level()
                              .playSound(
                                 null,
                                 serverPlayer1.blockPosition(),
                                 procedure.type == MedicalProcedures.Type.AMPUTATION
                                    ? SoundEvents.SHEEP_SHEAR
                                    : (SoundEvent)SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                                 SoundSource.PLAYERS,
                                 procedure.numb ? 0.12F : 0.4F,
                                 1.2F
                              );
                        }

                        if (procedure.ticks >= i) {
                           switch (procedure.type) {
                              case MEDICAL_KIT -> finishMedicalKit(serverPlayer, serverPlayer1, itemStack);
                              case AMPUTATION -> finishAmputation(serverPlayer, serverPlayer1, itemStack, procedure);
                              case SPLINT -> com.duckerquacker.lastaid.common.medical.bone.Bones.finishSplint(serverPlayer, serverPlayer1, procedure.limb, itemStack);
                              case REDUCE -> com.duckerquacker.lastaid.common.medical.bone.Bones.finishReduce(serverPlayer, serverPlayer1, procedure.limb);
                              case EXTRACT -> com.duckerquacker.lastaid.common.medical.wounds.Wounds.finishProcedure(serverPlayer1, procedure.zone, true);
                              case SUTURE -> com.duckerquacker.lastaid.common.medical.wounds.Wounds.finishProcedure(serverPlayer1, procedure.zone, false);
                           }

                           iterator.remove();
                        }
                     }
                  } else {
                     cancel(serverPlayer, serverPlayer1, "lastaid.medical_kit.cancel_moved");
                     iterator.remove();
                  }
               }
            } else {
               if (serverPlayer1 != null && procedure.type == MedicalProcedures.Type.AMPUTATION) {
                  LastAid.NETWORKING.sendAmputationFeedback(
                     serverPlayer1, new AmputationFeedbackPacket(AmputationFeedbackPacket.STOP, AmputationFeedbackPacket.FULL, 0)
                  );
               }
               iterator.remove();
            }
         }
      }
   }

   /** Amputation in progress: feedback for the patient only; "you feel nothing" once if numbed. */
   private static void amputationFeedback(MedicalProcedures.Procedure procedure, ServerPlayer patient, int ticksLeft) {
      if (procedure == null) {
         return;
      }
      byte strength = Amputation.feedbackStrength(patient);
      procedure.numb = strength == AmputationFeedbackPacket.NUMB;
      if (procedure.numb && !procedure.numbNotified) {
         procedure.numbNotified = true;
         patient.sendSystemMessage(Component.translatable("lastaid.amputation.numb").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
      }
      LastAid.NETWORKING.sendAmputationFeedback(patient, new AmputationFeedbackPacket(AmputationFeedbackPacket.PROCEDURE, strength, ticksLeft + 30));
   }

   private static boolean hasHelper(ServerPlayer serverPlayer, ServerPlayer serverPlayer1) {
      for (Player player : serverPlayer1.level().players()) {
         if (player != serverPlayer && player != serverPlayer1 && player.isAlive() && !player.isSpectator() && player.distanceTo(serverPlayer1) <= 3.0F) {
            return true;
         }
      }

      return false;
   }

   private static void finishMedicalKit(ServerPlayer serverPlayer, ServerPlayer serverPlayer1, ItemStack itemStack) {
      MedicalConfig.Items items = MedicalConfig.SERVER.items;
      Concussion.State state = Concussion.INSTANCE.state(serverPlayer1);
      if (state.severity > 0) {
         serverPlayer1.removeEffect(MobEffects.CONFUSION);
         if ((Integer)items.medkitNauseaImmunitySeconds.get() > 0) {
            serverPlayer1.addEffect(new MobEffectInstance(MedicalRegistry.STABILIZED, (Integer)items.medkitNauseaImmunitySeconds.get() * 20, 0));
         }

         Concussion.INSTANCE.treat(serverPlayer1, state, ((Double)items.medkitProgress.get()).floatValue());
      }

      Frostbite.INSTANCE.onMedicalKit(serverPlayer1);
      com.duckerquacker.lastaid.common.medical.bone.Bones.onMedicalKit(serverPlayer1);
      itemStack.consume(1, serverPlayer);
      serverPlayer.displayClientMessage(
         Component.translatable("lastaid.medical_kit.done", new Object[]{serverPlayer1.getDisplayName()}).withStyle(ChatFormatting.GREEN), true
      );
      serverPlayer1.displayClientMessage(Component.translatable("lastaid.medical_kit.done_patient").withStyle(ChatFormatting.GREEN), true);
      MedicalAdvancements.award(serverPlayer, "doctor_said");
   }

   private static void finishAmputation(ServerPlayer serverPlayer, ServerPlayer serverPlayer1, ItemStack itemStack, MedicalProcedures.Procedure procedure) {
      int i = (Integer)MedicalConfig.SERVER.frostbite.amputationBandages.get();
      if (serverPlayer.getAbilities().instabuild
         || count(serverPlayer, (Item)MedicalRegistry.MEDICAL_KIT.get()) >= 1 && count(serverPlayer, (Item)RegistryObjects.BANDAGE.get()) >= i) {
         consume(serverPlayer, (Item)MedicalRegistry.MEDICAL_KIT.get(), 1);
         consume(serverPlayer, (Item)RegistryObjects.BANDAGE.get(), i);
         itemStack.hurtAndBreak(1, serverPlayer, procedure.hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
         boolean numb = Amputation.feedbackStrength(serverPlayer1) == AmputationFeedbackPacket.NUMB;
         Frostbite.INSTANCE.amputate(serverPlayer1, procedure.limb);
         AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer1);
         if (abstractPlayerDamageModel != null) {
            AbstractDamageablePart abstractDamageablePart = abstractPlayerDamageModel.getFromEnum(EnumPlayerPart.BODY);
            abstractDamageablePart.damage(1.0F, serverPlayer1, true, 1.0F);
            CommonUtils.syncDamageModel(serverPlayer1);
         }

         serverPlayer1.level().playSound(null, serverPlayer1.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, numb ? 0.3F : 1.0F, 0.8F);
         Component component = Frostbite.limbName(procedure.limb);
         serverPlayer.displayClientMessage(Component.translatable("lastaid.amputation.done", new Object[]{component}).withStyle(ChatFormatting.DARK_RED), true);
         if (serverPlayer1 != serverPlayer) {
            serverPlayer1.displayClientMessage(
               Component.translatable("lastaid.amputation.done", new Object[]{component}).withStyle(ChatFormatting.DARK_RED), true
            );
         }
      } else {
         cancel(serverPlayer, serverPlayer1, "lastaid.medical_kit.cancel_item");
      }
   }

   private static void cancel(ServerPlayer serverPlayer, ServerPlayer serverPlayer1, String string) {
      LastAid.NETWORKING.sendAmputationFeedback(serverPlayer1, new AmputationFeedbackPacket(AmputationFeedbackPacket.STOP, AmputationFeedbackPacket.FULL, 0));
      serverPlayer.displayClientMessage(Component.translatable(string).withStyle(ChatFormatting.RED), true);
      if (serverPlayer1 != serverPlayer) {
         serverPlayer1.displayClientMessage(Component.translatable(string).withStyle(ChatFormatting.RED), true);
      }
   }

   public static void clear() {
      ACTIVE.clear();
   }

   private static final class Procedure {
      final MedicalProcedures.Type type;
      final UUID target;
      final InteractionHand hand;
      final Vec3 medicStart;
      final Vec3 targetStart;
      final Frostbite.Limb limb;
      int ticks;
      boolean waitingForHelperNotified;
      boolean numb;
      boolean numbNotified;
      com.duckerquacker.lastaid.common.medical.wounds.Zone zone;

      Procedure(MedicalProcedures.Type type, ServerPlayer serverPlayer, ServerPlayer serverPlayer1, InteractionHand interactionHand, Frostbite.Limb limb) {
         this.type = type;
         this.target = serverPlayer1.getUUID();
         this.hand = interactionHand;
         this.medicStart = serverPlayer.position();
         this.targetStart = serverPlayer1.position();
         this.limb = limb;
      }
   }

   private static enum Type {
      MEDICAL_KIT,
      AMPUTATION,
      SPLINT,
      REDUCE,
      EXTRACT,
      SUTURE;
   }
}
