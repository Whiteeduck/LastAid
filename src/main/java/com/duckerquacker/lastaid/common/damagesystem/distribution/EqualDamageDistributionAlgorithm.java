/*
 * LastAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.duckerquacker.lastaid.common.damagesystem.distribution;

import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.distribution.IDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.init.FirstAidDataAttachments;
import com.duckerquacker.lastaid.common.util.ArmorUtils;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import com.duckerquacker.lastaid.common.util.LoggingMarkers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

public class EqualDamageDistributionAlgorithm implements IDamageDistributionAlgorithm {
    public static final MapCodec<EqualDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("tryNoKill").forGetter(o -> o.tryNoKill),
                    Codec.FLOAT.fieldOf("reductionMultiplier").forGetter(o -> o.reductionMultiplier)
            ).apply(instance, EqualDamageDistributionAlgorithm::new));
    private static final Method GET_DAMAGE_AFTER_MAGIC_ABSORB_METHOD = findDamageAfterMagicAbsorbMethod();
    private final boolean tryNoKill;
    private final float reductionMultiplier;

    private static final class DistributionResult {
        private final float damageLeft;
        private final float effectiveDamageDone;

        private DistributionResult(float damageLeft, float effectiveDamageDone) {
            this.damageLeft = damageLeft;
            this.effectiveDamageDone = effectiveDamageDone;
        }
    }

    public EqualDamageDistributionAlgorithm(boolean tryNoKill, float reductionMultiplier) {
        this.tryNoKill = tryNoKill;
        this.reductionMultiplier = reductionMultiplier;
    }

    private float reduceDamage(float originalDamage, Player player, DamageSource source) {
        float damage = originalDamage;
        for (EquipmentSlot slot : CommonUtils.ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            damage = ArmorUtils.applyArmor(player, armor, source, damage, slot);
            if (damage <= 0F) return 0F;
        }

        if (GET_DAMAGE_AFTER_MAGIC_ABSORB_METHOD != null) {
            try {
                damage = (Float) GET_DAMAGE_AFTER_MAGIC_ABSORB_METHOD.invoke(player, source, damage);
            } catch (ReflectiveOperationException e) {
                LastAid.LOGGER.error(LoggingMarkers.DAMAGE_DISTRIBUTION, "Could not invoke getDamageAfterMagicAbsorb!", e);
                damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
            }
        } else {
            damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
        }

        if (damage <= 0F) return 0F;
        float reduction = originalDamage - damage;
        if (reduction > 0F) reduction *= reductionMultiplier;
        damage = originalDamage - reduction;
        if (damage <= 0F) return 0F;
        return damage;
    }

    private DistributionResult distributeOnParts(float damage, AbstractPlayerDamageModel damageModel, Player player, boolean tryNoKillThisRound) {
        int iterationCounter = 0;
        int divCount = EnumPlayerPart.VALUES.length;
        float prevDamageLeft;
        float damageLeft = damage;
        float effectiveDamageDone = 0.0F;
        do {
            //Setup values for next round
            prevDamageLeft = damageLeft;
            float toDamage = damageLeft / divCount;
            //Reset last counters
            divCount = 0;
            damageLeft = 0;

            for (AbstractDamageablePart part : damageModel) {
                if (part.currentHealth > 0F) {
                    float damageMultiplier = DamageDistribution.getIncomingPartDamageMultiplier(damageModel, part, player);
                    float scaledDamage = toDamage * damageMultiplier;
                    float scaledLeft = part.damage(scaledDamage, player, !(CommonUtils.getDamageModel(player) instanceof PlayerDamageModel model && model.isPainSuppressed(player)), tryNoKillThisRound ? 1F : 0F);
                    effectiveDamageDone += scaledDamage - scaledLeft;
                    damageLeft += Math.min(toDamage, DamageDistribution.restoreOriginalDamageScale(scaledLeft, damageMultiplier));
                    divCount++;
                }
            }

            //For safety
            if (iterationCounter >= 50) {
                LastAid.LOGGER.warn(LoggingMarkers.DAMAGE_DISTRIBUTION, "Not done distribution equally after 50 rounds, diff {}. Dropping!", Math.abs(prevDamageLeft - damageLeft));
                break;
            }
            iterationCounter++;
        } while (prevDamageLeft != damageLeft);
        return new DistributionResult(damageLeft, effectiveDamageDone);
    }

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        damage = reduceDamage(damage, player, source);
        if (damage <= 0F) return 0F;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return 0F;
        damage = DamageDistribution.consumeGlobalAbsorption(player, damage);
        if (damage <= 0.0F) {
            CommonUtils.syncDamageModel((ServerPlayer) player);
            return 0.0F;
        }

        DistributionResult result = distributeOnParts(damage, damageModel, player, tryNoKill);
        if (result.damageLeft > 0F && tryNoKill)
            result = distributeOnParts(damage, damageModel, player, false);

        CommonUtils.syncDamageModel((ServerPlayer) player);
        float effectiveDmg = result.effectiveDamageDone;
        if (effectiveDmg < 3.4028235E37F) {
            player.awardStat(Stats.DAMAGE_TAKEN, Math.round(effectiveDmg * 10.0F));
        }
        return result.damageLeft;
    }

    @Override
    public boolean skipGlobalPotionModifiers() {
        return true; //We apply all potions ourself
    }

    @Override
    public MapCodec<EqualDamageDistributionAlgorithm> codec() {
        return CODEC;
    }

    private static Method findDamageAfterMagicAbsorbMethod() {
        try {
            Method method = LivingEntity.class.getDeclaredMethod("getDamageAfterMagicAbsorb", DamageSource.class, float.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            LastAid.LOGGER.error(LoggingMarkers.DAMAGE_DISTRIBUTION, "Could not find getDamageAfterMagicAbsorb!", e);
            return null;
        }
    }
}

