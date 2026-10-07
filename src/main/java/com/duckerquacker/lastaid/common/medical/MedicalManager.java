package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.event.FirstAidLivingDamageEvent;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.DamageContext;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalConditions;
import com.duckerquacker.lastaid.common.medical.cold.CommonCold;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.medical.cold.Hypothermia;
import com.duckerquacker.lastaid.common.medical.compat.TemperatureCompat;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import com.duckerquacker.lastaid.common.network.BodyStatusSyncPacket;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player.BedSleepingProblem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BlockToolModificationEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;
import net.neoforged.neoforge.event.level.NoteBlockEvent.Play;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

public final class MedicalManager {
   private static final Map<UUID, PlayerMedicalContext> CONTEXTS = new HashMap<>();

   private MedicalManager() {
   }

   public static void init() {
      MedicalConditions.register(Concussion.INSTANCE);
      MedicalConditions.register(Hypothermia.INSTANCE);
      MedicalConditions.register(CommonCold.INSTANCE);
      MedicalConditions.register(Frostbite.INSTANCE);
      MedicalConditions.register(com.duckerquacker.lastaid.common.medical.overdose.Overdose.INSTANCE);
      MedicalConditions.register(com.duckerquacker.lastaid.common.medical.bone.FractureCondition.INSTANCE);
      MedicalConditions.register(com.duckerquacker.lastaid.common.medical.bone.DislocationCondition.INSTANCE);
      MedicalConditions.register(com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE);
      MedicalConditions.register(com.duckerquacker.lastaid.common.medical.infection.Infection.INSTANCE);
      MedicalConditions.register(com.duckerquacker.lastaid.common.medical.infection.Anemia.INSTANCE);
   }

   static PlayerMedicalContext context(ServerPlayer serverPlayer) {
      return CONTEXTS.computeIfAbsent(serverPlayer.getUUID(), uUID -> new PlayerMedicalContext());
   }

   private static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && (Boolean)MedicalConfig.SERVER.enabled.get();
   }

   @SubscribeEvent
   public static void onPlayerTick(Post post) {
      if (post.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         PlayerMedicalContext playerMedicalContext = context(serverPlayer);
         if (serverPlayer.isSprinting()) {
            playerMedicalContext.sprintTicks++;
         }

         MedicalData medicalData = MedicalHooks.data(serverPlayer);
         boolean bl = (serverPlayer.tickCount + serverPlayer.getId()) % 20 == 0;
         if (bl) {
            AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer);
            if (abstractPlayerDamageModel == null) {
               return;
            }

            if (serverPlayer.isAlive() && !serverPlayer.isSpectator()) {
               TemperatureRegulator.tick(serverPlayer, playerMedicalContext);
            }

            playerMedicalContext.refresh(serverPlayer, abstractPlayerDamageModel, medicalData);
            boolean bl1 = serverPlayer.isAlive() && !serverPlayer.getAbilities().invulnerable && !serverPlayer.isSpectator();
            if (bl1) {
               for (MedicalCondition medicalCondition : MedicalConditions.all()) {
                  tickCondition(serverPlayer, medicalCondition, medicalData, playerMedicalContext);
               }

               processTemperatureQueue(serverPlayer, medicalData);
               if (serverPlayer.isAlive()) {
                  playerMedicalContext.coldDeathSeconds = com.duckerquacker.lastaid.common.medical.cold.ColdDeath.tick(serverPlayer, playerMedicalContext.coldDeathSeconds);
               }
            } else {
               playerMedicalContext.coldDeathSeconds = 0;
            }

            // Limb state for player models (amputation / frostbite tint) + max health of amputees
            Amputation.tick(serverPlayer, playerMedicalContext);
            HeadBandage.tick(serverPlayer);
         }

         boolean bl2 = false;

         for (MedicalCondition medicalCondition1 : MedicalConditions.all()) {
            bl2 |= medicalData.<ConditionState>getState(medicalCondition1).consumeDirty();
         }

         if ((bl || bl2) && playerMedicalContext.damageModel != null) {
            sync(serverPlayer, medicalData, playerMedicalContext);
         }
      }
   }

   private static <S extends ConditionState> void tickCondition(
      ServerPlayer serverPlayer, MedicalCondition<S> medicalCondition, MedicalData medicalData, PlayerMedicalContext playerMedicalContext
   ) {
      medicalCondition.tick(serverPlayer, medicalData.getState(medicalCondition), playerMedicalContext);
   }

   private static void processTemperatureQueue(ServerPlayer serverPlayer, MedicalData medicalData) {
      if (medicalData.temperatureQueue != 0.0F) {
         TemperatureCompat temperatureCompat = TemperatureCompat.get();
         float f = Math.min(Math.abs(medicalData.temperatureQueue), Math.max(0.01F, Math.abs(medicalData.temperaturePerSecond)));
         f = Math.copySign(f, medicalData.temperatureQueue);
         if (temperatureCompat.active()) {
            temperatureCompat.addCoreUnits(serverPlayer, f);
         }

         medicalData.temperatureQueue -= f;
         if (Math.abs(medicalData.temperatureQueue) < 0.001F) {
            medicalData.temperatureQueue = 0.0F;
            medicalData.temperaturePerSecond = 0.0F;
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post post) {
      if (enabled()) {
         MedicalProcedures.tick(post.getServer());
      }
   }

   public static void sync(ServerPlayer serverPlayer) {
      AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer);
      if (abstractPlayerDamageModel != null && enabled()) {
         PlayerMedicalContext playerMedicalContext = context(serverPlayer);
         MedicalData medicalData = MedicalHooks.data(serverPlayer);
         playerMedicalContext.refresh(serverPlayer, abstractPlayerDamageModel, medicalData);
         sync(serverPlayer, medicalData, playerMedicalContext);
      }
   }

   private static void sync(ServerPlayer serverPlayer, MedicalData medicalData, PlayerMedicalContext playerMedicalContext) {
      ArrayList arrayList = new ArrayList();

      for (MedicalCondition medicalCondition : MedicalConditions.all()) {
         collect(serverPlayer, medicalCondition, medicalData, playerMedicalContext, arrayList);
      }

      MedicalConfig.Server server = MedicalConfig.SERVER;
      byte c = 0;
      float f = playerMedicalContext.bodyC + com.duckerquacker.lastaid.common.medical.infection.Infection.feverC(serverPlayer);
      if (playerMedicalContext.hasTempMod) {
         if (f < (Double)server.hypothermiaBelowC.get()) {
            c = 3;
         } else if (f < (Double)server.normalFromC.get()) {
            c = 1;
         } else if (f <= (Double)server.normalToC.get()) {
            c = 0;
         } else if (f <= (Double)server.feverAboveC.get()) {
            c = 2;
         } else {
            c = 4;
         }
      }

      byte b = (byte)(playerMedicalContext.worldTrendC > 0.05 ? 1 : (playerMedicalContext.worldTrendC < -0.05 ? -1 : 0));
      float g = playerMedicalContext.insulationFactor < 0.995 ? (float)((1.0 - playerMedicalContext.insulationFactor) * 100.0) : -1.0F;
      BodyStatusSyncPacket.Temperature temperature = new BodyStatusSyncPacket.Temperature(
         playerMedicalContext.hasTempMod,
         f,
         (float)playerMedicalContext.worldC,
         b,
         c,
         (float)playerMedicalContext.feelsLikeC,
         g,
         playerMedicalContext.wet,
         playerMedicalContext.grace,
         playerMedicalContext.warmProtection
      );
      int i = serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
      LastAid.NETWORKING.sendBodyStatus(serverPlayer, new BodyStatusSyncPacket(arrayList, temperature, i, MedicalHooks.movementFlags(serverPlayer)));
   }

   private static <S extends ConditionState> void collect(
      ServerPlayer serverPlayer,
      MedicalCondition<S> medicalCondition,
      MedicalData medicalData,
      PlayerMedicalContext playerMedicalContext,
      List<ConditionSnapshot> list
   ) {
      medicalCondition.collectSnapshots(serverPlayer, medicalData.getState(medicalCondition), playerMedicalContext, list);
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent playerLoggedInEvent) {
      if (playerLoggedInEvent.getEntity() instanceof ServerPlayer serverPlayer) {
         sync(serverPlayer);
         if (enabled()) {
            Amputation.updateMaxHealth(serverPlayer);
            Amputation.syncLimbs(serverPlayer, 0);
         }
      }
   }

   @SubscribeEvent
   public static void onStartTracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
      if (enabled() && event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker) {
         byte[] bytes = com.duckerquacker.lastaid.common.medical.LimbVisualState.encode(target);
         LastAid.NETWORKING.sendLimbState(tracker, new com.duckerquacker.lastaid.common.network.LimbStatePacket(target.getId(), bytes, 0));
      }
   }

   @SubscribeEvent
   public static void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
      if (playerRespawnEvent.getEntity() instanceof ServerPlayer serverPlayer) {
         CONTEXTS.remove(serverPlayer.getUUID());
         sync(serverPlayer);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent playerLoggedOutEvent) {
      CONTEXTS.remove(playerLoggedOutEvent.getEntity().getUUID());
      PlayerCommentTrigger.forget(playerLoggedOutEvent.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent serverStoppedEvent) {
      CONTEXTS.clear();
      MedicalProcedures.clear();
   }

   @SubscribeEvent
   public static void onFall(LivingFallEvent livingFallEvent) {
      if (livingFallEvent.getEntity() instanceof ServerPlayer serverPlayer) {
         MedicalData medicalData = MedicalHooks.data(serverPlayer);
         medicalData.pendingFallDistance = livingFallEvent.getDistance();
         medicalData.pendingFallCrouching = serverPlayer.isShiftKeyDown();
         medicalData.pendingFallTick = serverPlayer.level().getGameTime();
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void onFirstAidDamage(FirstAidLivingDamageEvent firstAidLivingDamageEvent) {
      if (!firstAidLivingDamageEvent.isCanceled() && firstAidLivingDamageEvent.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         MedicalData medicalData = MedicalHooks.data(serverPlayer);
         long l = serverPlayer.level().getGameTime();
         medicalData.lastDamageTick = l;
         if (firstAidLivingDamageEvent.getSource().getEntity() instanceof LivingEntity) {
            medicalData.lastFightTick = l;
         }

         float f = 0.0F;
         if (firstAidLivingDamageEvent.getSource().is(DamageTypeTags.IS_FALL) && l - medicalData.pendingFallTick <= 2L) {
            f = medicalData.pendingFallDistance;
         }

         DamageContext damageContext = new DamageContext(
            firstAidLivingDamageEvent.getSource(), firstAidLivingDamageEvent.getBeforeDamage(), firstAidLivingDamageEvent.getAfterDamage(), f
         );
         if (damageContext.damageTo(EnumPlayerPart.HEAD) > 0.0F) {
            ItemStack itemStack = serverPlayer.getItemBySlot(EquipmentSlot.HEAD);
            if (itemStack.is((Item)MedicalRegistry.HEAD_BANDAGE.get())) {
               serverPlayer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
               serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
               serverPlayer.displayClientMessage(Component.translatable("lastaid.head_bandage.torn").withStyle(ChatFormatting.RED), true);
            }
         }

         PlayerMedicalContext playerMedicalContext = context(serverPlayer);
         if (playerMedicalContext.damageModel == null) {
            playerMedicalContext.refresh(serverPlayer, firstAidLivingDamageEvent.getAfterDamage(), medicalData);
         }

         for (MedicalCondition medicalCondition : MedicalConditions.all()) {
            damageCondition(serverPlayer, medicalCondition, medicalData, damageContext, playerMedicalContext);
         }
      }
   }

   private static <S extends ConditionState> void damageCondition(
      ServerPlayer serverPlayer,
      MedicalCondition<S> medicalCondition,
      MedicalData medicalData,
      DamageContext damageContext,
      PlayerMedicalContext playerMedicalContext
   ) {
      medicalCondition.onDamage(serverPlayer, medicalData.getState(medicalCondition), damageContext, playerMedicalContext);
   }

   @SubscribeEvent
   public static void onAttack(AttackEntityEvent attackEntityEvent) {
      if (attackEntityEvent.getEntity() instanceof ServerPlayer serverPlayer) {
         MedicalHooks.data(serverPlayer).lastFightTick = serverPlayer.level().getGameTime();
      }
   }

   @SubscribeEvent
   public static void onJump(LivingJumpEvent livingJumpEvent) {
      if (livingJumpEvent.getEntity() instanceof ServerPlayer serverPlayer) {
         MedicalHooks.data(serverPlayer).lastJumpTick = serverPlayer.level().getGameTime();
      }
   }

   @SubscribeEvent
   public static void onExplosion(Detonate detonate) {
      if (detonate.getLevel() instanceof ServerLevel serverLevel) {
         markNoise(serverLevel, detonate.getExplosion().center());
      }
   }

   @SubscribeEvent
   public static void onNoteBlock(Play play) {
      if (play.getLevel() instanceof ServerLevel serverLevel) {
         markNoise(serverLevel, Vec3.atCenterOf(play.getPos()));
      }
   }

   private static void markNoise(ServerLevel serverLevel, Vec3 vec3) {
      long l = serverLevel.getGameTime();

      for (ServerPlayer serverPlayer : serverLevel.players()) {
         if (serverPlayer.position().distanceToSqr(vec3) <= 576.0) {
            MedicalHooks.data(serverPlayer).lastNoiseTick = l;
         }
      }
   }

   @SubscribeEvent
   public static void onCanSleep(CanPlayerSleepEvent canPlayerSleepEvent) {
      if (enabled() && canPlayerSleepEvent.getProblem() == null) {
         ServerPlayer serverPlayer = canPlayerSleepEvent.getEntity();
         Component component = Concussion.INSTANCE.sleepProblem(serverPlayer);
         if (component != null) {
            canPlayerSleepEvent.setProblem(BedSleepingProblem.OTHER_PROBLEM);
            serverPlayer.displayClientMessage(component.copy().withStyle(ChatFormatting.RED), true);
         }
      }
   }

   @SubscribeEvent
   public static void onSleepFinished(SleepFinishedTimeEvent sleepFinishedTimeEvent) {
      if (enabled() && sleepFinishedTimeEvent.getLevel() instanceof ServerLevel serverLevel) {
         for (ServerPlayer serverPlayer : serverLevel.players()) {
            if (serverPlayer.isSleepingLongEnough()) {
               MedicalData medicalData = MedicalHooks.data(serverPlayer);
               PlayerMedicalContext playerMedicalContext = context(serverPlayer);
               AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(serverPlayer);
               if (abstractPlayerDamageModel != null) {
                  playerMedicalContext.refresh(serverPlayer, abstractPlayerDamageModel, medicalData);

                  for (MedicalCondition medicalCondition : MedicalConditions.all()) {
                     sleepCondition(serverPlayer, medicalCondition, medicalData, playerMedicalContext);
                  }
               }
            }
         }
      }
   }

   private static <S extends ConditionState> void sleepCondition(
      ServerPlayer serverPlayer, MedicalCondition<S> medicalCondition, MedicalData medicalData, PlayerMedicalContext playerMedicalContext
   ) {
      medicalCondition.onSleepFinished(serverPlayer, medicalData.getState(medicalCondition), playerMedicalContext);
   }

   @SubscribeEvent
   public static void onStripLog(BlockToolModificationEvent blockToolModificationEvent) {
      if (!blockToolModificationEvent.isSimulated()
         && blockToolModificationEvent.getItemAbility() == ItemAbilities.AXE_STRIP
         && blockToolModificationEvent.getPlayer() != null
         && blockToolModificationEvent.getLevel() instanceof ServerLevel serverLevel
         && enabled()) {
         if (blockToolModificationEvent.getState().is(BlockTags.LOGS)
            && serverLevel.random.nextDouble() < (Double)MedicalConfig.SERVER.items.barkFromStrippingChance.get()) {
            Block.popResource(serverLevel, blockToolModificationEvent.getPos(), new ItemStack((ItemLike)MedicalRegistry.BARK.get()));
         }
      }
   }

   @SubscribeEvent
   public static void onEffectApplicable(Applicable applicable) {
      if (applicable.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         if (applicable.getEffectInstance() != null && applicable.getEffectInstance().is(MedicalRegistry.CLARITY)) {
            if (Concussion.INSTANCE.state(serverPlayer).severity >= 3) {
               applicable.setResult(Result.DO_NOT_APPLY);
               serverPlayer.displayClientMessage(Component.translatable("lastaid.clarity.too_weak").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEffectAdded(Added added) {
      if (added.getEntity() instanceof ServerPlayer serverPlayer && enabled()) {
         if (added.getEffectInstance().is(MedicalRegistry.CLARITY)) {
            Concussion.State state = Concussion.INSTANCE.state(serverPlayer);
            if (state.severity == 1 && (Boolean)MedicalConfig.SERVER.allowMagicHealing.get()) {
               Concussion.INSTANCE.treat(serverPlayer, state, Concussion.threshold(1));
            } else if (state.severity > 0) {
               state.treatedWithItems = true;
               state.markDirty();
            }
         }
      }
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent registerCommandsEvent) {
      MedicalCommands.register(registerCommandsEvent.getDispatcher());
   }

   @SubscribeEvent
   public static void onBrewing(RegisterBrewingRecipesEvent registerBrewingRecipesEvent) {
      MedicalRegistry.onBrewingRecipes(registerBrewingRecipesEvent);
   }
}

