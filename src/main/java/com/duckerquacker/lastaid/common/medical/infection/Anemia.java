package com.duckerquacker.lastaid.common.medical.infection;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalLog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/** Anemia: lost a lot of blood in a short time. Slowness I, Weakness I, less damage; food and sleep help. */
public final class Anemia implements MedicalCondition<Anemia.State> {
   public static final Anemia INSTANCE = new Anemia();
   public static final ResourceLocation ID = EffectUtil.id("anemia");
   private static final ResourceLocation ATTACK_ID = EffectUtil.id("anemia_attack");
   private static final double DAY = 24000.0;

   private Anemia() {
   }

   static MedicalConfig.Anemia cfg() {
      return MedicalConfig.SERVER.anemia;
   }

   public static boolean enabled() {
      return MedicalConfig.SERVER_SPEC.isLoaded() && MedicalConfig.SERVER.enabled.get() && cfg().enabled.get();
   }

   public State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   @Override
   public State createState() {
      return new State();
   }

   @Override
   public int getDefaultColor() {
      return 0xFFE0A0A0;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return EffectUtil.id("textures/gui/conditions/blood_drop.png");
   }

   @Override
   public boolean isActive(State state) {
      return state.remaining > 0.0;
   }

   /** Bleeding damage just taken (real time window). */
   public void recordBloodLoss(ServerPlayer player, float amount) {
      if (!enabled() || player.getServer() == null) {
         return;
      }
      State state = this.state(player);
      long now = player.getServer().getTickCount();
      state.losses.addLast(new long[]{now, Float.floatToIntBits(amount)});
      long window = cfg().windowSeconds.get() * 20L;
      float sum = 0.0F;
      while (!state.losses.isEmpty() && now - state.losses.peekFirst()[0] > window) {
         state.losses.removeFirst();
      }
      for (long[] loss : state.losses) {
         sum += Float.intBitsToFloat((int)loss[1]);
      }
      if (sum >= cfg().hpThreshold.get()) {
         boolean fresh = state.remaining <= 0.0;
         state.remaining = cfg().durationDays.get() * DAY;
         state.losses.clear();
         if (fresh) {
            player.sendSystemMessage(Component.translatable("lastaid.anemia.start").withStyle(ChatFormatting.RED));
            MedicalLog.add(player, Component.translatable("lastaid.log.anemia"));
         }
         state.markDirty();
      }
   }

   @Override
   public void tick(ServerPlayer player, State state, MedicalContext context) {
      if (!enabled() || state.remaining <= 0.0) {
         EffectUtil.setMultiplier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, 0.0);
         return;
      }
      state.remaining -= 20.0;
      if (state.remaining <= 0.0) {
         state.remaining = 0.0;
         EffectUtil.setMultiplier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, 0.0);
         player.displayClientMessage(Component.translatable("lastaid.anemia.end").withStyle(ChatFormatting.GREEN), true);
         state.markDirty();
         return;
      }
      EffectUtil.keepEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
      EffectUtil.keepEffect(player, MobEffects.WEAKNESS, 0);
      EffectUtil.setMultiplier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, -cfg().attackPenalty.get());
      if (player.tickCount % 200 < 20) {
         state.markDirty();
      }
   }

   @SubscribeEvent
   public static void onEat(LivingEntityUseItemEvent.Finish event) {
      if (!(event.getEntity() instanceof ServerPlayer player) || !enabled()) {
         return;
      }
      ItemStack stack = event.getItem();
      if (!stack.is(ItemTags.MEAT) && !stack.is(Items.BREAD)) {
         return;
      }
      State state = INSTANCE.state(player);
      if (state.remaining > 0.0) {
         state.remaining *= 1.0 - cfg().foodRecoveryBonus.get();
         player.displayClientMessage(Component.translatable("lastaid.anemia.food").withStyle(ChatFormatting.GREEN), true);
         state.markDirty();
      }
   }

   @SubscribeEvent
   public static void onSleepFinished(SleepFinishedTimeEvent event) {
      if (!enabled() || !(event.getLevel() instanceof ServerLevel level)) {
         return;
      }
      long skipped = event.getNewTime() - level.getDayTime();
      for (ServerPlayer player : level.players()) {
         State state = INSTANCE.state(player);
         if (player.isSleepingLongEnough() && state.remaining > 0.0 && skipped > 0L) {
            // rest: the night counts double
            state.remaining = Math.max(0.0, state.remaining - skipped * 2.0);
            state.markDirty();
         }
      }
   }

   @Override
   public void clear(ServerPlayer player, State state) {
      state.remaining = 0.0;
      state.losses.clear();
      EffectUtil.setMultiplier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, 0.0);
      state.markDirty();
   }

   @Override
   public ConditionSnapshot snapshot(ServerPlayer player, State state, MedicalContext context) {
      double total = cfg().durationDays.get() * DAY;
      float progress = (float)Mth.clamp(1.0 - state.remaining / total, 0.0, 1.0);
      String hours = String.format(Locale.ROOT, "%.1f", state.remaining / 1000.0);
      List<Component> tooltip = new ArrayList<>();
      tooltip.add(Component.translatable("lastaid.anemia.line", hours));
      tooltip.add(Component.translatable("lastaid.anemia.hint").withStyle(ChatFormatting.YELLOW));
      List<Component> detail = new ArrayList<>();
      detail.add(Component.translatable("lastaid.anemia.detail"));
      detail.add(Component.empty());
      detail.addAll(tooltip);
      return new ConditionSnapshot(ID, Component.translatable("lastaid.condition.anemia"), 0xFFE0A0A0, 1, 0, progress, false, tooltip, detail, 0);
   }

   public static final class State extends ConditionState {
      double remaining;
      final ArrayDeque<long[]> losses = new ArrayDeque<>();

      @Override
      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putDouble("remaining", this.remaining);
         return tag;
      }

      @Override
      public void load(CompoundTag tag) {
         this.remaining = tag.getDouble("remaining");
         this.markDirty();
      }
   }
}
