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

package com.duckerquacker.lastaid.common.registries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.debuff.IDebuffBuilder;
import com.duckerquacker.lastaid.api.distribution.IDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.api.distribution.IDamageDistributionTarget;
import com.duckerquacker.lastaid.common.apiimpl.StaticDamageDistributionTarget;
import com.duckerquacker.lastaid.common.apiimpl.TagDamageDistributionTarget;
import com.duckerquacker.lastaid.common.damagesystem.debuff.builder.ConstantDebuffBuilder;
import com.duckerquacker.lastaid.common.damagesystem.debuff.builder.OnHitDebuffBuilder;
import com.duckerquacker.lastaid.common.damagesystem.distribution.DirectDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.common.damagesystem.distribution.EqualDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.common.damagesystem.distribution.RandomDamageDistributionAlgorithm;
import com.duckerquacker.lastaid.common.damagesystem.distribution.StandardDamageDistributionAlgorithm;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FirstAidRegistries {

    public static class Keys {
        static final ResourceKey<Registry<MapCodec<? extends IDamageDistributionAlgorithm>>> DAMAGE_DISTRIBUTION_ALGORITHMS = key("damage_distribution_algorithms");
        static final ResourceKey<Registry<MapCodec<? extends IDamageDistributionTarget>>> DAMAGE_DISTRIBUTION_TARGETS = key("damage_distribution_targets");
        static final ResourceKey<Registry<IDamageDistributionTarget>> DAMAGE_DISTRIBUTIONS = key("damage_distributions");

        static final ResourceKey<Registry<MapCodec<? extends IDebuffBuilder>>> DEBUFF_BUILDERS = key("debuff_builders");
        static final ResourceKey<Registry<IDebuffBuilder>> DEBUFFS = key("debuffs");


        private static <T> ResourceKey<Registry<T>> key(String name) {
            return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(LastAid.MODID, name));
        }
    }

    // --- REGULAR REGISTRIES ---
    static final DeferredRegister<MapCodec<? extends IDamageDistributionAlgorithm>> DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS = DeferredRegister.create(Keys.DAMAGE_DISTRIBUTION_ALGORITHMS, LastAid.MODID);
    public static final Registry<MapCodec<? extends IDamageDistributionAlgorithm>> DAMAGE_DISTRIBUTION_ALGORITHMS = DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.makeRegistry(builder -> {});

    static final DeferredRegister<MapCodec<? extends IDamageDistributionTarget>> DEFERRED_DAMAGE_DISTRIBUTION_TARGETS = DeferredRegister.create(Keys.DAMAGE_DISTRIBUTION_TARGETS, LastAid.MODID);
    public static final Registry<MapCodec<? extends IDamageDistributionTarget>> DAMAGE_DISTRIBUTION_TARGETS = DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.makeRegistry(builder -> {});

    static final DeferredRegister<MapCodec<? extends IDebuffBuilder>> DEFERRED_DEBUFF_BUILDERS = DeferredRegister.create(Keys.DEBUFF_BUILDERS, LastAid.MODID);
    public static final Registry<MapCodec<? extends IDebuffBuilder>> DEBUFF_BUILDERS = DEFERRED_DEBUFF_BUILDERS.makeRegistry(builder -> {});

    static {
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("direct", () -> DirectDamageDistributionAlgorithm.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("equal", () -> EqualDamageDistributionAlgorithm.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("random", () -> RandomDamageDistributionAlgorithm.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register("standard", () -> StandardDamageDistributionAlgorithm.CODEC);

        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.register("static", () -> StaticDamageDistributionTarget.CODEC);
        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.register("tag", () -> TagDamageDistributionTarget.CODEC);

        DEFERRED_DEBUFF_BUILDERS.register("constant", () -> ConstantDebuffBuilder.CODEC);
        DEFERRED_DEBUFF_BUILDERS.register("on_hit", () -> OnHitDebuffBuilder.CODEC);
    }

    // --- DATA DRIVEN REGISTRIES
    public static void createDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(Keys.DAMAGE_DISTRIBUTIONS, FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC, FirstAidBaseCodecs.DAMAGE_DISTRIBUTION_TARGETS_DIRECT_CODEC);
        event.dataPackRegistry(Keys.DEBUFFS, FirstAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC, FirstAidBaseCodecs.DEBUFF_BUILDERS_DIRECT_CODEC);
    }

    public static void setup(IEventBus modEventBus) {
        DEFERRED_DAMAGE_DISTRIBUTION_ALGORITHMS.register(modEventBus);
        DEFERRED_DAMAGE_DISTRIBUTION_TARGETS.register(modEventBus);
        DEFERRED_DEBUFF_BUILDERS.register(modEventBus);
        modEventBus.addListener(FirstAidRegistries::createDataPackRegistries);
    }
}



