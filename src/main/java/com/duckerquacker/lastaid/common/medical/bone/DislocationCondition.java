package com.duckerquacker.lastaid.common.medical.bone;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.medical.EffectUtil;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Dislocations per arm / leg. Heals by itself in {@code dislocationHealDays}, or at once when reduced
 * (body panel: condition details, button "Reduce"; another player: sneak + right click with an empty hand).
 * Reducing hurts: pain 5 for a few seconds, then 2 for a game day.
 */
public final class DislocationCondition implements MedicalCondition<DislocationCondition.State> {
   public static final DislocationCondition INSTANCE = new DislocationCondition();
   public static final ResourceLocation ID = EffectUtil.id("dislocation");
   public static final String SNAPSHOT_PREFIX = "dislocation_";

   private DislocationCondition() {
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
      return 0xFFE0A060;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return Bones.JOINT_ICON;
   }

   @Override
   public boolean isActive(State state) {
      for (Joint joint : state.limbs) {
         if (joint.dislocated) {
            return true;
         }
      }
      return false;
   }

   @Override
   public void tick(ServerPlayer player, State state, MedicalContext context) {
      if (Bones.enabled()) {
         Bones.tickDislocations(player, state);
      }
      // last bone condition of the second: apply the combined bone effects
      Bones.applyEffects(player);
   }

   @Override
   public void clear(ServerPlayer player, State state) {
      for (Joint joint : state.limbs) {
         joint.dislocated = false;
         joint.heal = 0.0;
         joint.reducedAt = -1L;
      }
      state.markDirty();
      Bones.applyEffects(player);
   }

   @Override
   public void collectSnapshots(ServerPlayer player, State state, MedicalContext context, List<ConditionSnapshot> list) {
      if (!Bones.enabled()) {
         return;
      }
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         Joint joint = state.get(limb);
         if (!joint.dislocated) {
            continue;
         }
         Component limbName = Frostbite.limbName(limb);
         double needed = Bones.dislocationHealTicks();
         double daysLeft = Math.max(0.0, (needed - joint.heal) / 24000.0);
         List<Component> tooltip = new ArrayList<>();
         tooltip.add(Component.translatable("lastaid.dislocation.line", limbName, String.format(java.util.Locale.ROOT, "%.1f", daysLeft)));
         tooltip.add(Component.translatable("lastaid.dislocation.hint").withStyle(ChatFormatting.YELLOW));
         List<Component> detail = new ArrayList<>();
         detail.add(Component.translatable("lastaid.dislocation.detail"));
         detail.add(Component.empty());
         detail.add(tooltip.get(0).copy());
         detail.add(Component.empty());
         detail.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.dislocation.effect." + (limb.arm ? "arm" : "leg"))));
         detail.add(Component.empty());
         detail.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.dislocation.help.reduce")));
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.dislocation.help.other")));
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.dislocation.help.time")));
         list.add(
            new ConditionSnapshot(
               EffectUtil.id(SNAPSHOT_PREFIX + limb.key()),
               Component.translatable("lastaid.condition.dislocation", limbName),
               0xFFE0A060,
               1,
               limb.mask(),
               (float)Mth.clamp(joint.heal / needed, 0.0, 1.0),
               false,
               tooltip,
               detail,
               limb.ordinal(),
               Bones.JOINT_ICON,
               0,
               0
            )
         );
      }
   }

   @Override
   public ConditionSnapshot snapshot(ServerPlayer player, State state, MedicalContext context) {
      List<ConditionSnapshot> list = new ArrayList<>();
      this.collectSnapshots(player, state, context, list);
      return list.isEmpty() ? null : list.get(0);
   }

   public static final class Joint {
      public boolean dislocated;
      public double heal;
      /** game time of the last reduction (pain afterwards), -1 = none */
      public long reducedAt = -1L;

      CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putBoolean("dislocated", this.dislocated);
         tag.putDouble("heal", this.heal);
         tag.putLong("reduced_at", this.reducedAt);
         return tag;
      }

      void load(CompoundTag tag) {
         this.dislocated = tag.getBoolean("dislocated");
         this.heal = tag.getDouble("heal");
         this.reducedAt = tag.contains("reduced_at") ? tag.getLong("reduced_at") : -1L;
      }
   }

   public static final class State extends ConditionState {
      public final Joint[] limbs = new Joint[Frostbite.Limb.VALUES.length];

      State() {
         for (int i = 0; i < this.limbs.length; i++) {
            this.limbs[i] = new Joint();
         }
      }

      public Joint get(Frostbite.Limb limb) {
         return this.limbs[limb.ordinal()];
      }

      @Override
      public CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
            tag.put(limb.key(), this.get(limb).save());
         }
         return tag;
      }

      @Override
      public void load(CompoundTag tag) {
         for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
            this.get(limb).load(tag.getCompound(limb.key()));
         }
         this.markDirty();
      }
   }
}
