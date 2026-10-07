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

package com.duckerquacker.lastaid.common.damagesystem.debuff.builder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.duckerquacker.lastaid.api.debuff.IDebuff;
import com.duckerquacker.lastaid.api.debuff.IDebuffBuilder;
import com.duckerquacker.lastaid.api.enums.EnumDebuffSlot;
import com.duckerquacker.lastaid.common.damagesystem.debuff.ConstantDebuff;
import com.duckerquacker.lastaid.common.damagesystem.debuff.ConstantDebuffEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import java.util.List;

public class ConstantDebuffBuilder implements IDebuffBuilder {
    public static final MapCodec<ConstantDebuffBuilder> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    StringRepresentable.fromEnum(EnumDebuffSlot::values).fieldOf("debuffSlot").forGetter(o -> o.debuffSlot),
                    ResourceLocation.CODEC.fieldOf("potionName").forGetter(o -> o.effect),
                    ConstantDebuffEntry.CODEC.listOf().fieldOf("amplifierBoundaries").forGetter(o -> o.amplifierBoundaries)
            ).apply(instance, ConstantDebuffBuilder::new)
    );
    private final EnumDebuffSlot debuffSlot;
    private final ResourceLocation effect;
    private final List<ConstantDebuffEntry> amplifierBoundaries;

    public ConstantDebuffBuilder(EnumDebuffSlot debuffSlot, ResourceLocation effect, List<ConstantDebuffEntry> amplifierBoundaries) {
        this.debuffSlot = debuffSlot;
        this.effect = effect;
        this.amplifierBoundaries = amplifierBoundaries;
    }

    @Override
    public MapCodec<? extends IDebuffBuilder> codec() {
        return CODEC;
    }

    @Override
    public EnumDebuffSlot affectedSlot() {
        return this.debuffSlot;
    }

    @Override
    public IDebuff build() {
        return new ConstantDebuff(this.effect, this.amplifierBoundaries);
    }
}


