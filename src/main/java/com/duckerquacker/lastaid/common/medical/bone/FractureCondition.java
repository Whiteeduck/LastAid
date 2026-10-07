package com.duckerquacker.lastaid.common.medical.bone;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.DamageContext;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Fractures per arm / leg: 1 hairline, 2 closed, 3 open. Effects and triggers live in {@link Bones}.
 * A closed or open fracture that heals without a splint heals wrong (permanent penalty until a medical kit
 * re-breaks it).
 */
public final class FractureCondition implements MedicalCondition<FractureCondition.State> {
   public static final FractureCondition INSTANCE = new FractureCondition();
   public static final ResourceLocation ID = EffectUtil.id("fracture");
   public static final int HAIRLINE = 1;
   public static final int CLOSED = 2;
   public static final int OPEN = 3;

   private FractureCondition() {
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
      return 0xFFE8E0C8;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return Bones.BONE_ICON;
   }

   @Override
   public boolean isActive(State state) {
      for (Bone bone : state.limbs) {
         if (bone.type > 0 || bone.healedWrong) {
            return true;
         }
      }
      return false;
   }

   @Override
   public void tick(ServerPlayer player, State state, MedicalContext context) {
      if (Bones.enabled()) {
         Bones.tickFractures(player, state);
      }
   }

   @Override
   public void onDamage(ServerPlayer player, State state, DamageContext damage, MedicalContext context) {
      if (Bones.enabled()) {
         Bones.onDamage(player, damage);
      }
   }

   @Override
   public void clear(ServerPlayer player, State state) {
      for (Bone bone : state.limbs) {
         bone.reset();
         bone.healedWrong = false;
      }
      state.markDirty();
      Bones.applyEffects(player);
   }

   @Override
   public int movementFlags(ServerPlayer player, State state) {
      return Bones.enabled() ? Bones.movementFlags(player) : 0;
   }

   @Override
   public void collectSnapshots(ServerPlayer player, State state, MedicalContext context, List<ConditionSnapshot> list) {
      if (!Bones.enabled()) {
         return;
      }
      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         Bone bone = state.get(limb);
         if (bone.type > 0) {
            list.add(snapshot(limb, bone));
         } else if (bone.healedWrong) {
            list.add(healedWrongSnapshot(limb));
         }
      }
   }

   private static ConditionSnapshot snapshot(Frostbite.Limb limb, Bone bone) {
      Component limbName = Frostbite.limbName(limb);
      Component typeName = Component.translatable("lastaid.fracture.type." + bone.type);
      double needed = Bones.healTicks(bone.type);
      float progress = bone.type == OPEN && !bone.bleedingStopped ? 0.0F : (float)Mth.clamp(bone.heal / needed, 0.0, 1.0);
      double daysLeft = Math.max(0.0, (needed - bone.heal) / 24000.0);
      MutableComponent line = Component.translatable("lastaid.fracture.line", limbName, typeName)
         .append(Component.literal(", "))
         .append(bone.splinted
            ? Component.translatable("lastaid.fracture.splinted").withStyle(ChatFormatting.GREEN)
            : Component.translatable("lastaid.fracture.no_splint").withStyle(bone.type >= CLOSED ? ChatFormatting.RED : ChatFormatting.GRAY));
      if (bone.type == OPEN && !bone.bleedingStopped) {
         line.append(Component.literal(", ")).append(Component.translatable("lastaid.fracture.bleeding").withStyle(ChatFormatting.RED));
      } else {
         line.append(Component.literal(", ")).append(Component.translatable("lastaid.fracture.days_left", String.format(java.util.Locale.ROOT, "%.1f", daysLeft)));
      }
      List<Component> tooltip = new ArrayList<>();
      tooltip.add(line);
      if (bone.type >= CLOSED && !bone.splinted) {
         tooltip.add(Component.translatable("lastaid.fracture.hint.splint").withStyle(ChatFormatting.YELLOW));
      }
      if (bone.type == OPEN && !bone.bleedingStopped) {
         tooltip.add(Component.translatable("lastaid.fracture.hint.bandage").withStyle(ChatFormatting.YELLOW));
      }
      if (bone.type == OPEN && !bone.treated) {
         tooltip.add(Component.translatable("lastaid.fracture.hint.medkit").withStyle(ChatFormatting.GOLD));
      }
      List<Component> detail = new ArrayList<>();
      detail.add(Component.translatable("lastaid.fracture.detail." + bone.type));
      detail.add(Component.empty());
      detail.add(line.copy());
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.fracture.effect." + (limb.arm ? "arm." : "leg.") + bone.type)));
      detail.add(Component.empty());
      detail.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));
      if (bone.type >= CLOSED) {
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.fracture.help.splint")));
      }
      if (bone.type == OPEN) {
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.fracture.help.bandage")));
         detail.add(Component.literal(" • ").append(Component.translatable("lastaid.fracture.help.medkit")));
      }
      detail.add(Component.literal(" • ").append(Component.translatable("lastaid.fracture.help.time")));
      int flags = bone.type >= CLOSED && !bone.splinted ? Bones.FLAG_BLINK : 0;
      return new ConditionSnapshot(
         EffectUtil.id("fracture_" + limb.key()),
         Component.translatable("lastaid.condition.fracture", limbName),
         0xFFE8E0C8,
         bone.type,
         limb.mask(),
         progress,
         bone.type == OPEN,
         tooltip,
         detail,
         0,
         Bones.BONE_ICON,
         bone.type == OPEN && !bone.bleedingStopped ? 0x50B01010 : 0,
         flags
      );
   }

   private static ConditionSnapshot healedWrongSnapshot(Frostbite.Limb limb) {
      Component limbName = Frostbite.limbName(limb);
      List<Component> tooltip = List.of(
         Component.translatable("lastaid.fracture.healed_wrong.line", limbName),
         Component.translatable("lastaid.fracture.hint.healed_wrong").withStyle(ChatFormatting.GOLD)
      );
      List<Component> detail = List.of(
         Component.translatable("lastaid.fracture.healed_wrong.detail"),
         Component.empty(),
         Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN),
         Component.literal(" • ").append(Component.translatable("lastaid.fracture.hint.healed_wrong"))
      );
      return new ConditionSnapshot(
         EffectUtil.id("healed_wrong_" + limb.key()),
         Component.translatable("lastaid.condition.healed_wrong", limbName),
         0xFFB0A080,
         1,
         limb.mask(),
         -1.0F,
         false,
         tooltip,
         detail,
         0,
         Bones.BONE_ICON,
         0,
         0
      );
   }

   @Override
   public ConditionSnapshot snapshot(ServerPlayer player, State state, MedicalContext context) {
      List<ConditionSnapshot> list = new ArrayList<>();
      this.collectSnapshots(player, state, context, list);
      return list.isEmpty() ? null : list.get(0);
   }

   public static final class Bone {
      public int type;
      public boolean splinted;
      /** healing progress in ticks */
      public double heal;
      public boolean healedWrong;
      /** open fracture: a bandage / plaster was put on that limb */
      public boolean bleedingStopped;
      /** open fracture: cleaned with a medical kit, no infection */
      public boolean treated;
      /** ticks since the bone broke (infection check) */
      public long age;
      public boolean infectionRolled;
      int bleedTimer;

      void reset() {
         this.type = 0;
         this.splinted = false;
         this.heal = 0.0;
         this.bleedingStopped = false;
         this.treated = false;
         this.age = 0L;
         this.infectionRolled = false;
         this.bleedTimer = 0;
      }

      CompoundTag save() {
         CompoundTag tag = new CompoundTag();
         tag.putInt("type", this.type);
         tag.putBoolean("splinted", this.splinted);
         tag.putDouble("heal", this.heal);
         tag.putBoolean("wrong", this.healedWrong);
         tag.putBoolean("bandaged", this.bleedingStopped);
         tag.putBoolean("treated", this.treated);
         tag.putLong("age", this.age);
         tag.putBoolean("infection_rolled", this.infectionRolled);
         return tag;
      }

      void load(CompoundTag tag) {
         this.type = Mth.clamp(tag.getInt("type"), 0, 3);
         this.splinted = tag.getBoolean("splinted");
         this.heal = tag.getDouble("heal");
         this.healedWrong = tag.getBoolean("wrong");
         this.bleedingStopped = tag.getBoolean("bandaged");
         this.treated = tag.getBoolean("treated");
         this.age = tag.getLong("age");
         this.infectionRolled = tag.getBoolean("infection_rolled");
      }
   }

   public static final class State extends ConditionState {
      public final Bone[] limbs = new Bone[Frostbite.Limb.VALUES.length];

      State() {
         for (int i = 0; i < this.limbs.length; i++) {
            this.limbs[i] = new Bone();
         }
      }

      public Bone get(Frostbite.Limb limb) {
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
