package com.duckerquacker.lastaid.common.medical.carry;

import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.bone.Bones;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Carrying and dragging a player who can't walk.
 * <ul>
 *    <li>Carry: empty hand, right click. The patient rides the carrier (vanilla passenger sync), the client draws
 *    them across the carrier's arms. Carrier: -40 % speed, no sprint/jump/attack/items/blocks (doors and buttons work).
 *    Getting hurt: 15 % to drop the patient.</li>
 *    <li>Drag: sneak + empty hand, right click. The patient crawls and is pulled behind the carrier; -60 % speed;
 *    every 3 s 0.2 (soft ground) / 0.4 (hard ground) damage to the patient.</li>
 *    <li>Release: sneak (carry) / right click the patient again. Everything ends on death, logout, teleport,
 *    dimension change and when the carrier mounts something.</li>
 * </ul>
 */
public final class CarryManager {
   public enum Mode {
      CARRY,
      DRAG
   }

   private static final class Link {
      final UUID target;
      final Mode mode;
      final long startTick;
      int timer;
      boolean releasing;
      @Nullable
      Vec3 lastPos;

      Link(UUID target, Mode mode, long startTick) {
         this.target = target;
         this.mode = mode;
         this.startTick = startTick;
      }
   }

   private static final Map<UUID, Link> BY_CARRIER = new HashMap<>();
   private static final Map<UUID, UUID> BY_TARGET = new HashMap<>();
   private static final ResourceLocation SPEED_ID = EffectUtil.id("carry_speed");

   private CarryManager() {
   }

   private static MedicalConfig.Carry cfg() {
      return MedicalConfig.SERVER.carry;
   }

   public static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && MedicalConfig.SERVER.enabled.get() && cfg().enabled.get();
   }

   // ---------------------------------------------------------------- rules

   private static boolean legUseless(Player player, Frostbite.Limb leg) {
      return MedicalHooks.limbStatus(player, leg) >= 4 || Bones.legOut(player, leg);
   }

   /** Can't walk: both legs out, or unconscious. Works on both sides (client: synced limb bits / damage model). */
   public static boolean canBeCarried(Player player) {
      if (!player.isAlive() || player.isSpectator()) {
         return false;
      }
      if (legUseless(player, Frostbite.Limb.LEFT_LEG) && legUseless(player, Frostbite.Limb.RIGHT_LEG)) {
         return true;
      }
      return CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model && model.isUnconscious();
   }

   private static boolean armWorks(Player player, InteractionHand hand) {
      return !Bones.armUseless(player, hand);
   }

   public static boolean canCarry(Player carrier) {
      if (canBeCarried(carrier)) {
         return false;
      }
      boolean main = armWorks(carrier, InteractionHand.MAIN_HAND);
      boolean off = armWorks(carrier, InteractionHand.OFF_HAND);
      return MedicalConfig.SERVER_SPEC.isLoaded() && cfg().allowCarryOnlyBothArmsAlive.get() ? main && off : main || off;
   }

   public static boolean canDrag(Player carrier) {
      if (canBeCarried(carrier)) {
         return false; // crawling yourself
      }
      boolean main = armWorks(carrier, InteractionHand.MAIN_HAND);
      boolean off = armWorks(carrier, InteractionHand.OFF_HAND);
      return MedicalConfig.SERVER_SPEC.isLoaded() && cfg().allowDragWithOneArm.get() ? main || off : main && off;
   }

   public static boolean isCarrying(Player player) {
      return BY_CARRIER.containsKey(player.getUUID());
   }

   public static boolean isCarried(Player player) {
      return BY_TARGET.containsKey(player.getUUID());
   }

   @Nullable
   public static Mode mode(Player carrier) {
      Link link = BY_CARRIER.get(carrier.getUUID());
      return link == null ? null : link.mode;
   }

   private static boolean dragged(Player target) {
      UUID carrier = BY_TARGET.get(target.getUUID());
      Link link = carrier == null ? null : BY_CARRIER.get(carrier);
      return link != null && link.mode == Mode.DRAG;
   }

   /** Extra movement flags (MedicalHooks): the carrier can't sprint/jump, a dragged player crawls. */
   public static int movementFlags(ServerPlayer player) {
      if (!enabled()) {
         return 0;
      }
      if (isCarrying(player)) {
         return MedicalCondition.MOVE_NO_SPRINT | MedicalCondition.MOVE_NO_JUMP;
      }
      return dragged(player) ? MedicalCondition.MOVE_NO_SPRINT | MedicalCondition.MOVE_NO_JUMP | MedicalCondition.MOVE_CRAWL : 0;
   }

   // ---------------------------------------------------------------- start / stop

   private static void start(ServerPlayer carrier, ServerPlayer target, Mode mode) {
      BY_CARRIER.put(carrier.getUUID(), new Link(target.getUUID(), mode, carrier.level().getGameTime()));
      BY_TARGET.put(target.getUUID(), carrier.getUUID());
      double mult = mode == Mode.CARRY ? cfg().carrySpeedMultiplier.get() : cfg().dragSpeedMultiplier.get();
      EffectUtil.setMultiplier(carrier, Attributes.MOVEMENT_SPEED, SPEED_ID, mult - 1.0);
      carrier.setSprinting(false);
      if (mode == Mode.CARRY) {
         target.stopRiding();
         target.startRiding(carrier, true);
         syncPassengers(carrier);
      }
      String key = mode == Mode.CARRY ? "lastaid.carry.start" : "lastaid.carry.drag_start";
      carrier.displayClientMessage(Component.translatable(key, target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
      target.displayClientMessage(Component.translatable(key + ".target", carrier.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
   }

   /** Ends whatever link this player is part of. dropDamage: the patient falls (0.5 HP). */
   public static void release(Player player, boolean dropDamage) {
      UUID carrierId = BY_CARRIER.containsKey(player.getUUID()) ? player.getUUID() : BY_TARGET.get(player.getUUID());
      if (carrierId == null) {
         return;
      }
      Link link = BY_CARRIER.remove(carrierId);
      if (link == null) {
         return;
      }
      BY_TARGET.remove(link.target);
      MinecraftServer server = player.getServer();
      if (server == null) {
         return;
      }
      ServerPlayer carrier = server.getPlayerList().getPlayer(carrierId);
      ServerPlayer target = server.getPlayerList().getPlayer(link.target);
      if (carrier != null) {
         EffectUtil.setMultiplier(carrier, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0);
      }
      if (target != null) {
         if (link.mode == Mode.CARRY && target.getVehicle() == carrier && carrier != null) {
            link.releasing = true;
            target.stopRiding();
            syncPassengers(carrier);
            // put the patient down in front of the carrier
            Vec3 front = carrier.position().add(carrier.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(0.8));
            target.teleportTo(front.x, carrier.getY(), front.z);
         }
         if (dropDamage && target.isAlive()) {
            target.hurt(target.damageSources().fall(), 0.5F);
            target.displayClientMessage(Component.translatable("lastaid.carry.dropped").withStyle(ChatFormatting.RED), true);
         }
      }
      if (carrier != null) {
         carrier.displayClientMessage(Component.translatable(dropDamage ? "lastaid.carry.dropped_carrier" : "lastaid.carry.released").withStyle(ChatFormatting.GRAY), true);
      }
   }

   /** Vanilla only tells the trackers of a vehicle about its passengers; the carrier's own client needs it too. */
   private static void syncPassengers(ServerPlayer carrier) {
      carrier.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(carrier));
   }

   // ---------------------------------------------------------------- events

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void onInteract(EntityInteract event) {
      if (!(event.getEntity() instanceof ServerPlayer carrier) || !(event.getTarget() instanceof ServerPlayer target) || !enabled()) {
         return;
      }
      if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND || !carrier.getMainHandItem().isEmpty()) {
         return;
      }
      // right click the patient again: put them down
      Link own = BY_CARRIER.get(carrier.getUUID());
      if (own != null) {
         // the same click can arrive twice (interact + interact-at); only a later click puts the patient down
         if (own.target.equals(target.getUUID()) && carrier.level().getGameTime() - own.startTick > 10L) {
            release(carrier, false);
            event.setCanceled(true);
         }
         return;
      }
      if (isCarried(target) || isCarried(carrier) || !canBeCarried(target) || carrier.distanceTo(target) > 3.0F || carrier.isPassenger()) {
         return;
      }
      if (carrier.isShiftKeyDown()) {
         if (canDrag(carrier)) {
            start(carrier, target, Mode.DRAG);
         } else {
            carrier.displayClientMessage(Component.translatable("lastaid.carry.cant_drag").withStyle(ChatFormatting.RED), true);
         }
      } else if (canCarry(carrier)) {
         start(carrier, target, Mode.CARRY);
      } else {
         carrier.displayClientMessage(Component.translatable("lastaid.carry.cant_carry").withStyle(ChatFormatting.RED), true);
      }
      event.setCanceled(true);
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent.Post event) {
      if (BY_CARRIER.isEmpty()) {
         return;
      }
      MinecraftServer server = event.getServer();
      for (Map.Entry<UUID, Link> entry : Map.copyOf(BY_CARRIER).entrySet()) {
         ServerPlayer carrier = server.getPlayerList().getPlayer(entry.getKey());
         Link link = entry.getValue();
         ServerPlayer target = server.getPlayerList().getPlayer(link.target);
         if (carrier == null || target == null || !carrier.isAlive() || !target.isAlive() || carrier.level() != target.level() || !enabled()) {
            if (carrier != null) {
               release(carrier, false);
            } else if (target != null) {
               release(target, false);
            } else {
               BY_CARRIER.remove(entry.getKey());
               BY_TARGET.remove(link.target);
            }
            continue;
         }
         if (!canBeCarried(target)) {
            release(carrier, false); // the patient can walk again
            continue;
         }
         carrier.setSprinting(false);
         if (link.mode == Mode.CARRY) {
            if (target.getVehicle() != carrier) {
               release(carrier, false);
               continue;
            }
            if (carrier.isShiftKeyDown()) {
               release(carrier, false);
            } else if (carrier.tickCount % 20 == 0) {
               syncPassengers(carrier);
            }
            continue;
         }
         // drag: pull the patient along behind the carrier
         Vec3 toCarrier = carrier.position().subtract(target.position());
         double dist = toCarrier.horizontalDistance();
         if (dist > 6.0 || Math.abs(toCarrier.y) > 3.0) {
            release(carrier, false);
            continue;
         }
         // keep the patient ~1.4 blocks away: pull when further, push back when they slide into the carrier
         Vec3 motion = target.getDeltaMovement();
         double error = dist - 1.4;
         if (Math.abs(error) > 0.15 && dist > 1.0E-3) {
            Vec3 dir = new Vec3(toCarrier.x, 0.0, toCarrier.z).normalize();
            double speed = Mth.clamp(error * 0.45, -0.12, 0.32);
            double y = error > 0.0 ? Math.min(0.1, motion.y + (toCarrier.y > 0.4 ? 0.25 : 0.0)) : motion.y;
            target.setDeltaMovement(dir.x * speed, y, dir.z * speed);
            target.hurtMarked = true;
         }
         if (link.lastPos == null) {
            link.lastPos = target.position();
         }
         if (++link.timer >= cfg().dragDamageInterval.get() * 20) {
            link.timer = 0;
            // server-side player motion is client driven, so judge by how far the patient actually slid
            double moved = target.position().subtract(link.lastPos).horizontalDistance();
            link.lastPos = target.position();
            if (moved > 0.5) {
               BlockState below = target.level().getBlockState(target.getOnPos());
               BlockState in = target.level().getBlockState(target.blockPosition());
               boolean soft = isSoft(below) || isSoft(in);
               float damage = (soft ? cfg().dragDamageSoftBlock.get() : cfg().dragDamageHardBlock.get()).floatValue();
               if (damage > 0.0F) {
                  target.hurt(target.damageSources().generic(), damage);
               }
            }
         }
      }
   }

   private static boolean isSoft(BlockState state) {
      return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.SNOW) || state.is(BlockTags.WOOL)
         || state.is(BlockTags.WOOL_CARPETS) || state.is(net.minecraft.world.level.block.Blocks.SNOW) || state.is(BlockTags.REPLACEABLE_BY_TREES)
         || state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK) || state.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET);
   }

   private static boolean busyCarrier(Player player) {
      return player instanceof ServerPlayer && BY_CARRIER.containsKey(player.getUUID()) && BY_CARRIER.get(player.getUUID()).mode == Mode.CARRY;
   }

   private static boolean helpless(Player player) {
      return player instanceof ServerPlayer && isCarried(player);
   }

   @SubscribeEvent
   public static void onAttack(AttackEntityEvent event) {
      if (busyCarrier(event.getEntity()) || helpless(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onLeftClickBlock(LeftClickBlock event) {
      if (busyCarrier(event.getEntity()) || helpless(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRightClickItem(RightClickItem event) {
      if (busyCarrier(event.getEntity()) || helpless(event.getEntity())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onRightClickBlock(RightClickBlock event) {
      Player player = event.getEntity();
      if (busyCarrier(player) || helpless(player)) {
         BlockState state = player.level().getBlockState(event.getPos());
         boolean simple = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
            || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock;
         if (!simple || helpless(player)) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onPlace(BlockEvent.EntityPlaceEvent event) {
      if (event.getEntity() instanceof Player player && (busyCarrier(player) || helpless(player))) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onBreak(BlockEvent.BreakEvent event) {
      if (busyCarrier(event.getPlayer()) || helpless(event.getPlayer())) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onMount(EntityMountEvent event) {
      if (!(event.getEntityMounting() instanceof ServerPlayer player)) {
         return;
      }
      if (event.isDismounting()) {
         // the patient can't climb down by themselves (sneak)
         UUID carrierId = BY_TARGET.get(player.getUUID());
         Link link = carrierId == null ? null : BY_CARRIER.get(carrierId);
         if (link != null && link.mode == Mode.CARRY && !link.releasing && event.getEntityBeingMounted() instanceof Player && player.isAlive()) {
            event.setCanceled(true);
         }
      } else if (event.isMounting() && isCarrying(player)) {
         release(player, false); // boats, minecarts, horses: put the patient down first
      }
   }

   @SubscribeEvent
   public static void onDamage(LivingDamageEvent.Post event) {
      if (event.getEntity() instanceof ServerPlayer carrier && busyCarrier(carrier) && event.getNewDamage() > 0.0F) {
         if (carrier.getRandom().nextDouble() < cfg().carryDropChanceOnDamage.get()) {
            release(carrier, true);
         }
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         release(player, false);
      }
   }

   @SubscribeEvent
   public static void onTeleport(EntityTeleportEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && (isCarrying(player) || isCarried(player))) {
         release(player, false);
      }
   }

   @SubscribeEvent
   public static void onDimension(EntityTravelToDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         release(player, false);
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      release(event.getEntity(), false);
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      // a stale speed modifier from a crash / old session
      if (event.getEntity() instanceof ServerPlayer player) {
         EffectUtil.setMultiplier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0);
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      BY_CARRIER.clear();
      BY_TARGET.clear();
   }
}
