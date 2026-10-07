package com.duckerquacker.lastaid.common.medical.concussion;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.DamageContext;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalContext;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.medical.MedicalAdvancements;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalIcons;
import com.duckerquacker.lastaid.common.medical.MedicalRegistry;
import com.duckerquacker.lastaid.common.medical.PlayerCommentTrigger;
import com.duckerquacker.lastaid.common.medical.PlayerMedicalContext;
import com.duckerquacker.lastaid.common.medical.compat.TemperatureCompat;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class Concussion implements MedicalCondition<Concussion.State> {
   public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("lastaid", "concussion");
   public static final Concussion INSTANCE = new Concussion();
   public static final int FLAG_SPASMS = 1;
   public static final int FLAG_CALM = 2;
   public static final int FLAG_CLARITY = 4;
   public static final int FLAG_BRACED = 8;

   private Concussion() {
   }

   public Concussion.State state(Player player) {
      return MedicalHooks.data(player).getState(this);
   }

   @Override
   public ResourceLocation getId() {
      return ID;
   }

   public Concussion.State createState() {
      return new Concussion.State();
   }

   @Override
   public int getDefaultColor() {
      return -6283024;
   }

   @Override
   public ResourceLocation getIconTexture() {
      return MedicalIcons.BRAIN;
   }

   public boolean isActive(Concussion.State state) {
      return state.severity > 0;
   }

   private static MedicalConfig.Concussion cfg() {
      return MedicalConfig.SERVER.concussion;
   }

   public void onDamage(ServerPlayer serverPlayer, Concussion.State state, DamageContext damageContext, MedicalContext medicalContext) {
      if ((Boolean)cfg().enabled.get()) {
         float f = damageContext.damageTo(EnumPlayerPart.HEAD);
         if (state.severity > 0 && damageContext.totalDamage() > 0.0F && !isFrozen(serverPlayer)) {
            state.progress = Math.max(0.0F, state.progress - ((Double)cfg().damagePenalty.get()).floatValue());
            state.markDirty();
         }

         int i = this.rollConcussion(serverPlayer, damageContext, f, serverPlayer.getRandom());
         if (i > 0) {
            this.inflict(serverPlayer, state, i, false);
         }
      }
   }

   public static boolean isBluntTrauma(DamageSource damageSource) {
      if (damageSource.is(DamageTypes.FALLING_ANVIL) || damageSource.is(DamageTypes.LIGHTNING_BOLT)) {
         return true;
      } else {
         return !damageSource.is(DamageTypeTags.IS_FIRE)
               && !damageSource.is(DamageTypeTags.IS_FREEZING)
               && !damageSource.is(DamageTypeTags.IS_DROWNING)
               && !damageSource.is(DamageTypes.MAGIC)
               && !damageSource.is(DamageTypes.INDIRECT_MAGIC)
               && !damageSource.is(DamageTypes.WITHER)
               && !damageSource.is(DamageTypes.STARVE)
               && !damageSource.is(DamageTypes.IN_WALL)
               && !damageSource.is(DamageTypes.CRAMMING)
               && !damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)
               && !damageSource.is(DamageTypes.GENERIC_KILL)
               && !damageSource.is(DamageTypes.DRY_OUT)
               && !damageSource.is(DamageTypes.SWEET_BERRY_BUSH)
               && !damageSource.is(DamageTypes.CACTUS)
               && !damageSource.is(DamageTypes.THORNS)
            ? damageSource.getDirectEntity() != null
               || damageSource.is(DamageTypeTags.IS_FALL)
               || damageSource.is(DamageTypeTags.IS_EXPLOSION)
               || damageSource.is(DamageTypeTags.IS_PROJECTILE)
               || damageSource.is(DamageTypes.FLY_INTO_WALL)
               || damageSource.is(DamageTypes.FALLING_BLOCK)
               || damageSource.is(DamageTypes.FALLING_STALACTITE)
               || damageSource.is(DamageTypes.STALAGMITE)
            : false;
      }
   }

   private int rollConcussion(ServerPlayer serverPlayer, DamageContext damageContext, float f, RandomSource randomSource) {
      MedicalConfig.Concussion concussion = cfg();
      DamageSource damageSource = damageContext.source();
      if (!isBluntTrauma(damageSource)) {
         return 0;
      } else {
         int i = Math.max(1, damageContext.maxHealth(EnumPlayerPart.HEAD));
         float g = f * 4.0F / i;
         boolean bl = f > 0.0F && damageContext.healthAfter(EnumPlayerPart.HEAD) <= 0.0F;
         boolean bl1 = serverPlayer.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
            || serverPlayer.getItemBySlot(EquipmentSlot.HEAD).is((Item)MedicalRegistry.HEAD_BANDAGE.get());
         if (bl) {
            return 3;
         } else {
            double d = 0.0;
            int j = 0;
            if (f > 0.0F) {
               Concussion.Tier tier = Concussion.Tier.of(g);
               d = tier.chance(concussion);
               j = tier.rollSeverity(concussion, randomSource);
            }

            Entity entity = damageSource.getEntity();
            ItemStack itemStack = damageSource.getWeaponItem();
            if (damageSource.is(DamageTypes.FALLING_ANVIL) && f > 0.0F) {
               d = 1.0;
               j = Math.max(j, 1);
            } else if (damageSource.is(DamageTypes.LIGHTNING_BOLT)) {
               d = Math.max(d, (Double)concussion.lightningChance.get());
               j = Math.max(j, 1);
            } else if (damageSource.is(DamageTypes.FLY_INTO_WALL)) {
               d = Math.max(d, (Double)concussion.flyIntoWallChance.get());
               j = Math.max(j, g >= 2.0F ? 2 : 1);
            } else if (entity instanceof IronGolem || entity instanceof Ravager || itemStack != null && itemStack.is(Items.MACE)) {
               double h = (Double)concussion.heavyHitChance.get() + (bl1 ? (Double)concussion.heavyHitNoHelmetBonus.get() : 0.0);
               d = Math.max(d, h);
               j = Math.max(j, 1);
            } else if (damageSource.is(DamageTypeTags.IS_FALL) && damageContext.fallDistance() > 0.0F) {
               double e = (damageContext.fallDistance() - (Double)concussion.fallStartBlocks.get()) * (Double)concussion.fallChancePerBlock.get();
               e = Mth.clamp(e, 0.0, (Double)concussion.fallMaxChance.get());
               if (MedicalHooks.data(serverPlayer).pendingFallCrouching) {
                  e *= concussion.fallCrouchMultiplier.get();
               }

               if (e > 0.0 && bl1 && serverPlayer.level().canSeeSky(serverPlayer.blockPosition().above())) {
                  e += concussion.snowNoHelmetBonus.get();
               }

               d = Math.max(d, e);
               if (f <= 0.0F) {
                  j = damageContext.fallDistance() >= ((Integer)concussion.fallSeverityThreeBlocks.get()).intValue()
                     ? 3
                     : (damageContext.fallDistance() >= ((Integer)concussion.fallSeverityTwoBlocks.get()).intValue() ? 2 : 1);
               }
            }

            if (damageSource.is(DamageTypeTags.IS_EXPLOSION) && f > 0.0F) {
               d *= concussion.explosionMultiplier.get();
               Vec3 vec3 = damageSource.getSourcePosition();
               if (vec3 != null && isLookingAt(serverPlayer, vec3, 45.0)) {
                  d *= concussion.explosionFacingMultiplier.get();
               }
            }

            if (!(d <= 0.0) && j > 0) {
               d *= helmetMultiplier(serverPlayer.getItemBySlot(EquipmentSlot.HEAD));
               d *= concussion.chanceMultiplier.get();
               return randomSource.nextDouble() < Math.min(1.0, d) ? Math.min(3, j) : 0;
            } else {
               return 0;
            }
         }
      }
   }

   private static boolean isLookingAt(Player player, Vec3 vec3, double d) {
      Vec3 vec31 = player.getViewVector(1.0F).normalize();
      Vec3 vec32 = vec3.subtract(player.getEyePosition()).normalize();
      double e = Mth.clamp(vec31.dot(vec32), -1.0, 1.0);
      return Math.toDegrees(Math.acos(e)) <= d;
   }

   public static double helmetMultiplier(ItemStack itemStack) {
      MedicalConfig.Concussion concussion = cfg();
      if (!itemStack.isEmpty() && itemStack.getItem() instanceof ArmorItem armorItem) {
         Holder holder = armorItem.getMaterial();
         double d;
         if (holder.is(ArmorMaterials.LEATHER) || holder.is(ArmorMaterials.GOLD)) {
            d = (Double)concussion.helmetLeatherGold.get();
         } else if (holder.is(ArmorMaterials.CHAIN) || holder.is(ArmorMaterials.TURTLE)) {
            d = (Double)concussion.helmetChainTurtle.get();
         } else if (holder.is(ArmorMaterials.IRON)) {
            d = (Double)concussion.helmetIron.get();
         } else if (holder.is(ArmorMaterials.DIAMOND)) {
            d = (Double)concussion.helmetDiamond.get();
         } else if (holder.is(ArmorMaterials.NETHERITE)) {
            d = (Double)concussion.helmetNetherite.get();
         } else {
            d = (Double)concussion.helmetOther.get();
         }

         if (itemStack.isDamageableItem() && itemStack.getMaxDamage() > 0) {
            float f = 1.0F - (float)itemStack.getDamageValue() / itemStack.getMaxDamage();
            if (f < 0.2F) {
               d += concussion.helmetLowDurabilityPenalty.get();
            }
         }

         return Math.min(1.0, d);
      } else {
         return 1.0;
      }
   }

   public void inflict(ServerPlayer serverPlayer, Concussion.State state, int i, boolean bl) {
      int j = state.severity;
      if (bl) {
         state.severity = Mth.clamp(i, 0, 3);
         state.progress = 0.0F;
         if (state.severity == 0) {
            state.relapseCount = 0;
         }
      } else if (j > 0) {
         if (isFrozen(serverPlayer) || serverPlayer.hasEffect(MedicalRegistry.STABILIZED)) {
            return;
         }

         state.severity = Math.min(3, Math.max(j + 1, i));
         state.progress = 0.0F;
         state.relapseCount++;
      } else {
         state.severity = Mth.clamp(i, 1, 3);
         state.progress = 0.0F;
         state.relapseCount = 0;
         state.treatedWithItems = false;
         state.strainSecondsToday = 0;
         MedicalAdvancements.award(serverPlayer, "something_wrong");
      }

      state.nauseaTimer = 3;
      state.blindTimer = 10;
      state.markDirty();
      if (state.severity > j) {
         PlayerCommentTrigger.onSeverityChanged(serverPlayer, "concussion_" + state.severity, j, state.severity);
      }

      if (state.severity > 0 && state.severity != j) {
         serverPlayer.playNotifySound((SoundEvent)RegistryObjects.TINNITUS.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
         if (state.severity >= 3 && serverPlayer.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)) {
            MedicalAdvancements.award(serverPlayer, "head_not_helmet");
         }
      }
   }

   public void clear(ServerPlayer serverPlayer, Concussion.State state) {
      state.severity = 0;
      state.progress = 0.0F;
      state.relapseCount = 0;
      state.strainSecondsToday = 0;
      state.markDirty();
   }

   public static boolean isFrozen(Player player) {
      return player.hasEffect(MedicalRegistry.NECK_BRACED);
   }

   public void tick(ServerPlayer serverPlayer, Concussion.State state, MedicalContext medicalContext) {
      if (state.severity > 0 && (Boolean)cfg().enabled.get()) {
         state.secondCounter++;
         long l = serverPlayer.level().getDayTime() / 24000L;
         if (l != state.dayIndex) {
            state.dayIndex = l;
            state.strainSecondsToday = 0;
         }

         if (!isFrozen(serverPlayer)) {
            this.updateRecovery(serverPlayer, state, medicalContext);
            if (state.severity <= 0) {
               return;
            }
         }

         this.applySymptoms(serverPlayer, state, medicalContext);
      }
   }

   private void updateRecovery(ServerPlayer serverPlayer, Concussion.State state, MedicalContext medicalContext) {
      MedicalConfig.Concussion concussion1 = cfg();
      double d = this.recoveryPerSecond(serverPlayer, state, medicalContext);
      if (medicalContext.activity() == MedicalContext.Activity.STRAINING) {
         state.strainSecondsToday++;
         if (state.strainSecondsToday > (Integer)concussion1.activityLimitSeconds.get()) {
            this.relapse(serverPlayer, state);
            return;
         }
      }

      float f = state.progress;
      state.progress = (float)Math.max(0.0, state.progress + d);
      float g = threshold(state.severity);
      if (state.progress >= g) {
         if (state.severity >= 3 && (Boolean)concussion1.hardcoreMode.get()) {
            state.progress = g;
         } else if (!headHealthAllowsImprovement(medicalContext)) {
            state.progress = g;
         } else {
            this.improve(serverPlayer, state);
         }
      }

      if ((int)f != (int)state.progress) {
         state.markDirty();
      }
   }

   public double recoveryPerSecond(ServerPlayer serverPlayer, Concussion.State state, MedicalContext medicalContext) {
      MedicalConfig.Concussion concussion = cfg();
      if (isFrozen(serverPlayer)) {
         return 0.0;
      } else {
         boolean bl = !medicalContext.hostilesNearby() && medicalContext.lightLevel() <= (Integer)concussion.bedMaxLight.get();
         boolean bl1 = medicalContext.secondsSinceDamage() >= 10 && medicalContext.lightLevel() <= (Integer)concussion.stillMaxLight.get();
         double d;
         switch (medicalContext.activity()) {
            case STRAINING:
               return (Double)concussion.strainingPerSecond.get();
            case RESTING_IN_BED:
               d = bl ? (Double)concussion.bedRestPerSecond.get() : (bl1 ? (Double)concussion.stillPerSecond.get() : (Double)concussion.walkingPerSecond.get());
               break;
            case STILL:
               d = bl1 ? (Double)concussion.stillPerSecond.get() : (Double)concussion.walkingPerSecond.get();
               break;
            default:
               d = (Double)concussion.walkingPerSecond.get();
         }

         if (medicalContext.nearHeatSource()) {
            d += concussion.heatSourceBonus.get();
         }

         if (serverPlayer.getItemBySlot(EquipmentSlot.HEAD).is((Item)MedicalRegistry.HEAD_BANDAGE.get())) {
            d += concussion.headBandageBonus.get();
         }

         double e = (Double)concussion.recoverySpeedMultiplier.get();
         if (medicalContext.lightLevel() > (Integer)concussion.brightLightAbove.get()) {
            e *= concussion.brightLightMultiplier.get();
         }

         if (medicalContext.temperatureMatters()) {
            MedicalConfig.Server server = MedicalConfig.SERVER;
            float f = medicalContext.bodyTemperatureC();
            if (f < (Double)server.hypothermiaBelowC.get()) {
               e *= concussion.hypothermiaMultiplier.get();
            } else if (f > (Double)server.feverAboveC.get()) {
               e *= concussion.feverMultiplier.get();
            }
         }

         if (medicalContext.noisyEnvironment()) {
            e *= concussion.noiseMultiplier.get();
         }

         if (serverPlayer.getFoodData().getFoodLevel() < (Integer)concussion.hungerBelow.get()) {
            e *= concussion.hungerMultiplier.get();
         }

         if (state.severity == 2 && serverPlayer.hasEffect(MedicalRegistry.CLARITY)) {
            e = 0.0;
         }

         if (state.severity >= 3 && (Boolean)concussion.hardcoreMode.get()) {
            e = 0.0;
         }

         return d * e;
      }
   }

   private static boolean headHealthAllowsImprovement(MedicalContext medicalContext) {
      AbstractDamageablePart abstractDamageablePart = medicalContext.damageModel().getFromEnum(EnumPlayerPart.HEAD);
      return abstractDamageablePart.currentHealth >= abstractDamageablePart.getMaxHealth() * 0.5F;
   }

   public static float threshold(int i) {
      MedicalConfig.Concussion concussion = cfg();

      return switch (i) {
         case 1 -> ((Double)concussion.thresholdSeverity1.get()).floatValue();
         case 2 -> ((Double)concussion.thresholdSeverity2.get()).floatValue();
         default -> ((Double)concussion.thresholdSeverity3.get()).floatValue();
      };
   }

   private void improve(ServerPlayer serverPlayer, Concussion.State state) {
      MedicalConfig.Concussion concussion = cfg();
      if (state.severity == 1) {
         state.severity = 0;
         state.progress = 0.0F;
         serverPlayer.displayClientMessage(Component.translatable("lastaid.concussion.cured").withStyle(ChatFormatting.GREEN), true);
         if (!state.treatedWithItems) {
            MedicalAdvancements.award(serverPlayer, "bed_rest");
         }

         if (state.relapseCount >= 2) {
            MedicalAdvancements.award(serverPlayer, "back_to_normal");
         }

         state.relapseCount = 0;
         removeSymptomEffects(serverPlayer);
      } else if (state.severity == 2) {
         state.severity = 1;
         state.progress = ((Double)concussion.carryOverTo1.get()).floatValue();
         serverPlayer.displayClientMessage(Component.translatable("lastaid.concussion.improved", new Object[]{roman(1)}).withStyle(ChatFormatting.GREEN), true);
      } else {
         state.severity = 2;
         state.progress = ((Double)concussion.carryOverTo2.get()).floatValue();
         serverPlayer.displayClientMessage(Component.translatable("lastaid.concussion.improved", new Object[]{roman(2)}).withStyle(ChatFormatting.GREEN), true);
      }

      state.markDirty();
   }

   private void relapse(ServerPlayer serverPlayer, Concussion.State state) {
      MedicalConfig.Concussion concussion = cfg();
      state.progress = 0.0F;
      state.relapseCount++;
      state.strainSecondsToday = 0;
      if (state.severity < 3
         && !serverPlayer.hasEffect(MedicalRegistry.STABILIZED)
         && serverPlayer.getRandom().nextDouble() < state.relapseCount * (Double)concussion.relapseChancePerCount.get()) {
         state.severity++;
      }

      serverPlayer.displayClientMessage(Component.translatable("lastaid.concussion.relapse").withStyle(ChatFormatting.RED), false);
      serverPlayer.playNotifySound((SoundEvent)RegistryObjects.TINNITUS.value(), SoundSource.PLAYERS, 0.7F, 0.9F);
      state.markDirty();
   }

   public void treat(ServerPlayer serverPlayer, Concussion.State state, float f) {
      if (state.severity > 0) {
         state.treatedWithItems = true;
         if (isFrozen(serverPlayer)) {
            state.markDirty();
         } else {
            state.progress += f;
            float g = threshold(state.severity);
            if (state.progress >= g && (state.severity < 3 || !(Boolean)cfg().hardcoreMode.get())) {
               this.improve(serverPlayer, state);
            }

            state.markDirty();
         }
      }
   }

   public void onSleepFinished(ServerPlayer serverPlayer, Concussion.State state, MedicalContext medicalContext) {
      if (state.severity > 0 && !isFrozen(serverPlayer)) {
         float f = (float)((Double)cfg().fullNightSleepBonus.get() * (Double)cfg().recoverySpeedMultiplier.get());
         if (state.severity < 3 || !(Boolean)cfg().hardcoreMode.get()) {
            state.progress += f;

            while (state.severity > 0 && state.progress >= threshold(state.severity) && headHealthAllowsImprovement(medicalContext)) {
               float g = state.progress - threshold(state.severity);
               this.improve(serverPlayer, state);
               if (state.severity > 0) {
                  state.progress += g;
               }
            }

            state.markDirty();
         }
      }
   }

   public Component sleepProblem(ServerPlayer serverPlayer) {
      Concussion.State state = this.state(serverPlayer);
      if (state.severity >= 3 && (Boolean)cfg().enabled.get()) {
         MedicalConfig.Server server = MedicalConfig.SERVER;
         TemperatureCompat temperatureCompat = TemperatureCompat.get();
         if (temperatureCompat.active()
            && !temperatureCompat.hasGrace(serverPlayer)
            && temperatureCompat.bodyCelsius(serverPlayer) < (Double)server.hypothermiaBelowC.get()) {
            return Component.translatable("lastaid.concussion.no_sleep_cold");
         } else {
            return cfg().requireSecondPlayerForSeverity3.get() && !PlayerMedicalContext.isOtherPlayerNearby(serverPlayer, 8.0)
               ? Component.translatable("lastaid.concussion.no_sleep_alone")
               : null;
         }
      } else {
         return null;
      }
   }

   private void applySymptoms(ServerPlayer serverPlayer, Concussion.State state, MedicalContext medicalContext) {
      MedicalConfig.Concussion concussion = cfg();
      RandomSource randomSource = serverPlayer.getRandom();
      boolean bl = serverPlayer.hasEffect(MedicalRegistry.CALM);
      boolean bl1 = serverPlayer.hasEffect(MedicalRegistry.CLARITY);
      boolean bl2 = serverPlayer.hasEffect(MedicalRegistry.STABILIZED);
      boolean bl3 = !bl || state.secondCounter % 2 == 0;
      int i = state.severity;
      keepEffect(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN, i >= 2 ? 1 : 0);
      if (i >= 2) {
         keepEffect(serverPlayer, MobEffects.WEAKNESS, 0);
      }

      if (bl1) {
         serverPlayer.removeEffect(MobEffects.CONFUSION);
         serverPlayer.removeEffect(MobEffects.BLINDNESS);
      }

      if (bl3) {
         if (--state.nauseaTimer <= 0) {
            byte b;
            switch (i) {
               case 1:
                  b = 3;
                  state.nauseaTimer = 25 + randomSource.nextInt(11);
                  break;
               case 2:
                  b = 5;
                  state.nauseaTimer = 20;
                  break;
               default:
                  b = 8;
                  state.nauseaTimer = 12;
            }

            if (!bl1 && !bl2) {
               serverPlayer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, (b + 3) * 20, 0, false, false, true));
            }
         }

         if (i >= 2 && --state.blindTimer <= 0) {
            int j = i >= 3 ? 2 : 1;
            state.blindTimer = i >= 3 ? 30 : 40;
            if (medicalContext.inSnowstorm()) {
               j++;
            }

            if (!bl1) {
               serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, j * 20 + 10, 0, false, false, true));
            }
         }

         if (i >= 3 && --state.faintTimer <= 0) {
            state.faintTimer = 120;
            if (!bl1 && randomSource.nextDouble() < (Double)concussion.faintChance.get()) {
               serverPlayer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 70, 0, false, false, true));
               serverPlayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, false, true));
               serverPlayer.displayClientMessage(Component.translatable("lastaid.concussion.faint").withStyle(ChatFormatting.DARK_RED), true);
            }
         }
      }

      if (i >= 2
         && medicalContext.activity() != MedicalContext.Activity.STILL
         && medicalContext.activity() != MedicalContext.Activity.RESTING_IN_BED
         && !serverPlayer.getMainHandItem().isEmpty()
         && randomSource.nextDouble() < (Double)concussion.dropItemChancePerSecond.get()) {
         serverPlayer.drop(false);
         serverPlayer.displayClientMessage(Component.translatable("lastaid.concussion.dropped").withStyle(ChatFormatting.GRAY), true);
      }

      if (randomSource.nextDouble() < (Double)concussion.tinnitusChancePerSecond.get() * i) {
         serverPlayer.playNotifySound((SoundEvent)RegistryObjects.TINNITUS.value(), SoundSource.PLAYERS, 0.25F + 0.1F * i, 1.0F);
      }

      if (state.secondCounter % 5 == 0) {
         state.markDirty();
      }
   }

   private static void keepEffect(ServerPlayer serverPlayer, Holder<MobEffect> holder, int i) {
      MobEffectInstance mobEffectInstance = serverPlayer.getEffect(holder);
      if (mobEffectInstance == null
         || mobEffectInstance.getAmplifier() <= i && (mobEffectInstance.getAmplifier() != i || mobEffectInstance.getDuration() <= 30)) {
         serverPlayer.addEffect(new MobEffectInstance(holder, 50, i, false, false, true));
      }
   }

   private static void removeSymptomEffects(ServerPlayer serverPlayer) {
      removeIfShort(serverPlayer, MobEffects.MOVEMENT_SLOWDOWN);
      removeIfShort(serverPlayer, MobEffects.WEAKNESS);
      removeIfShort(serverPlayer, MobEffects.CONFUSION);
   }

   private static void removeIfShort(ServerPlayer serverPlayer, Holder<MobEffect> holder) {
      MobEffectInstance mobEffectInstance = serverPlayer.getEffect(holder);
      if (mobEffectInstance != null && mobEffectInstance.getDuration() <= 220 && !mobEffectInstance.isVisible()) {
         serverPlayer.removeEffect(holder);
      }
   }

   public static Component roman(int i) {
      return Component.translatable("lastaid.severity." + Mth.clamp(i, 0, 3));
   }

   public ConditionSnapshot snapshot(ServerPlayer serverPlayer, Concussion.State state, MedicalContext medicalContext) {
      MedicalConfig.Concussion concussion1 = cfg();
      float f = threshold(state.severity);
      float g = Mth.clamp(state.progress / f, 0.0F, 1.0F);
      int i = Math.round(g * 100.0F);
      boolean bl = isFrozen(serverPlayer);
      boolean bl1 = serverPlayer.hasEffect(MedicalRegistry.CALM);
      boolean bl2 = serverPlayer.hasEffect(MedicalRegistry.CLARITY);
      MedicalConfig.Server server = MedicalConfig.SERVER;
      boolean bl3 = medicalContext.temperatureMatters() && medicalContext.bodyTemperatureC() < (Double)server.hypothermiaBelowC.get();
      ArrayList arrayList = new ArrayList();
      arrayList.add(Component.translatable("lastaid.concussion.tooltip.severity", new Object[]{roman(state.severity)}));
      arrayList.add(Component.translatable("lastaid.concussion.tooltip.progress", new Object[]{i}));
      if (state.relapseCount > 0) {
         arrayList.add(Component.translatable("lastaid.concussion.tooltip.relapses", new Object[]{state.relapseCount}));
      }

      if (bl) {
         arrayList.add(Component.translatable("lastaid.concussion.tooltip.braced").withStyle(ChatFormatting.AQUA));
      }

      if (bl2) {
         arrayList.add(Component.translatable("lastaid.concussion.tooltip.clarity").withStyle(ChatFormatting.AQUA));
      }

      if (bl1) {
         arrayList.add(Component.translatable("lastaid.concussion.tooltip.calm").withStyle(ChatFormatting.GREEN));
      }

      arrayList.add(
         Component.translatable("lastaid.concussion.tooltip.effects", new Object[]{symptomEffectList(state.severity)}).withStyle(ChatFormatting.GRAY)
      );
      ArrayList arrayList1 = new ArrayList();
      arrayList1.add(Component.translatable("lastaid.concussion.detail.description"));
      arrayList1.add(Component.empty());
      arrayList1.add(Component.translatable("lastaid.concussion.detail.symptoms_header").withStyle(ChatFormatting.GOLD));

      for (int j = 1; j <= 4; j++) {
         String string = "lastaid.concussion.detail.symptoms" + state.severity + "." + j;
         arrayList1.add(Component.literal(" • ").append(Component.translatable(string)));
      }

      arrayList1.add(Component.empty());
      arrayList1.add(Component.translatable("lastaid.concussion.detail.helps_header").withStyle(ChatFormatting.GREEN));

      for (int l = 1; l <= 5; l++) {
         arrayList1.add(Component.literal(" + ").append(Component.translatable("lastaid.concussion.detail.helps." + l)).withStyle(ChatFormatting.GREEN));
      }

      arrayList1.add(Component.translatable("lastaid.concussion.detail.hurts_header").withStyle(ChatFormatting.RED));

      for (int m = 1; m <= 4; m++) {
         arrayList1.add(Component.literal(" - ").append(Component.translatable("lastaid.concussion.detail.hurts." + m)).withStyle(ChatFormatting.RED));
      }

      arrayList1.add(Component.empty());
      double d = this.recoveryPerSecond(serverPlayer, state, medicalContext);
      arrayList1.add(
         Component.translatable("lastaid.concussion.detail.rate", new Object[]{String.format("%+.2f", d)})
            .withStyle(d > 0.0 ? ChatFormatting.GREEN : ChatFormatting.RED)
      );
      arrayList1.add(
         Component.translatable("lastaid.concussion.detail.progress", new Object[]{String.format("%.0f", state.progress), String.format("%.0f", f)})
      );
      if (d > 0.0) {
         int k = (int)Math.ceil(Math.max(0.0, (double)(f - state.progress)) / d);
         MutableComponent mutableComponent = Component.translatable("lastaid.concussion.detail.eta", new Object[]{formatDuration(k)});
         arrayList1.add(mutableComponent.withStyle(ChatFormatting.AQUA));
      } else {
         arrayList1.add(Component.translatable("lastaid.concussion.detail.no_progress").withStyle(ChatFormatting.GRAY));
      }

      arrayList1.add(
         Component.translatable("lastaid.concussion.detail.strain", new Object[]{state.strainSecondsToday, concussion1.activityLimitSeconds.get()})
      );
      if (!headHealthAllowsImprovement(medicalContext)) {
         arrayList1.add(Component.translatable("lastaid.concussion.detail.head_low").withStyle(ChatFormatting.RED));
      }

      if (state.severity >= 3 && (Boolean)concussion1.hardcoreMode.get()) {
         arrayList1.add(Component.translatable("lastaid.concussion.detail.hardcore").withStyle(ChatFormatting.DARK_RED));
      }

      if (bl3 && state.severity >= 3) {
         arrayList1.add(Component.translatable("lastaid.concussion.detail.spasms").withStyle(ChatFormatting.AQUA));
      }

      byte b = 0;
      if (bl3 && state.severity >= 3) {
         b |= 1;
      }

      if (bl1) {
         b |= 2;
      }

      if (bl2) {
         b |= 4;
      }

      if (bl) {
         b |= 8;
      }

      return new ConditionSnapshot(
         ID,
         Component.translatable("lastaid.condition.concussion"),
         this.getDefaultColor(),
         state.severity,
         ConditionSnapshot.mask(EnumPlayerPart.HEAD),
         g,
         state.severity >= 3,
         arrayList,
         arrayList1,
         b,
         MedicalIcons.BRAIN,
         0,
         0
      );
   }

   private static Component symptomEffectList(int i) {
      return switch (i) {
         case 1 -> Component.translatable("lastaid.concussion.effects.1");
         case 2 -> Component.translatable("lastaid.concussion.effects.2");
         default -> Component.translatable("lastaid.concussion.effects.3");
      };
   }

   private static String formatDuration(int i) {
      return i >= 3600 ? String.format("%d:%02d:%02d", i / 3600, i / 60 % 60, i % 60) : String.format("%d:%02d", i / 60, i % 60);
   }

   public static boolean isHeadPart(EnumPlayerPart enumPlayerPart) {
      return enumPlayerPart == EnumPlayerPart.HEAD;
   }

   static void syncModel(ServerPlayer serverPlayer) {
      CommonUtils.syncDamageModel(serverPlayer);
   }

   public static final class State extends ConditionState {
      public int severity;
      public float progress;
      public int relapseCount;
      public int strainSecondsToday;
      public long dayIndex = -1L;
      public boolean treatedWithItems;
      int nauseaTimer = 20;
      int blindTimer = 30;
      int faintTimer = 120;
      int secondCounter;

      @Override
      public CompoundTag save() {
         CompoundTag compoundTag = new CompoundTag();
         compoundTag.putInt("severity", this.severity);
         compoundTag.putFloat("progress", this.progress);
         compoundTag.putInt("relapses", this.relapseCount);
         compoundTag.putInt("strain", this.strainSecondsToday);
         compoundTag.putLong("day", this.dayIndex);
         compoundTag.putBoolean("treated", this.treatedWithItems);
         return compoundTag;
      }

      @Override
      public void load(CompoundTag compoundTag) {
         this.severity = Mth.clamp(compoundTag.getInt("severity"), 0, 3);
         this.progress = compoundTag.getFloat("progress");
         this.relapseCount = compoundTag.getInt("relapses");
         this.strainSecondsToday = compoundTag.getInt("strain");
         this.dayIndex = compoundTag.contains("day") ? compoundTag.getLong("day") : -1L;
         this.treatedWithItems = compoundTag.getBoolean("treated");
         this.markDirty();
      }
   }

   private static enum Tier {
      UNDER_HALF,
      T1,
      T2,
      T3,
      T4;

      static Concussion.Tier of(float f) {
         if (f < 0.5F) {
            return UNDER_HALF;
         } else if (f < 1.0F) {
            return T1;
         } else if (f < 2.0F) {
            return T2;
         } else {
            return f < 3.0F ? T3 : T4;
         }
      }

      double chance(MedicalConfig.Concussion concussion) {
         return switch (this) {
            case UNDER_HALF -> concussion.chanceUnderHalfHeart.get();
            case T1 -> concussion.chanceTier1.get();
            case T2 -> concussion.chanceTier2.get();
            case T3 -> concussion.chanceTier3.get();
            case T4 -> concussion.chanceTier4.get();
         };
      }

      int rollSeverity(MedicalConfig.Concussion concussion, RandomSource randomSource) {
         return switch (this) {
            case UNDER_HALF, T1 -> 1;
            case T2 -> randomSource.nextDouble() < concussion.tier2SeverityTwoChance.get() ? 2 : 1;
            case T3 -> 2;
            case T4 -> randomSource.nextDouble() < concussion.tier4SeverityThreeChance.get() ? 3 : 2;
         };
      }
   }
}
