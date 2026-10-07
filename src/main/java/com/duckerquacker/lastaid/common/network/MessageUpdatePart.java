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

package com.duckerquacker.lastaid.common.network;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.duckerquacker.lastaid.shim.forge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageUpdatePart {
    private final byte id;
    private final int maxHealth;
    private final float absorption;
    private final float currentHealth;

    public MessageUpdatePart(FriendlyByteBuf buf) {
        this.id = buf.readByte();
        this.maxHealth = buf.readInt();
        this.absorption = buf.readFloat();
        this.currentHealth = buf.readFloat();
        validate();
    }

    public MessageUpdatePart(AbstractDamageablePart part) {
        this.id = (byte) part.part.ordinal();
        this.maxHealth = part.getMaxHealth();
        this.absorption = part.getAbsorption();
        this.currentHealth = part.currentHealth;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(id);
        buf.writeInt(maxHealth);
        buf.writeFloat(absorption);
        buf.writeFloat(currentHealth);
        validate();
    }

    private void validate() {
        if (currentHealth < 0)
            throw new RuntimeException("Negative currentHealth!");
        if (absorption < 0)
            throw new RuntimeException("Negative absorption!");
        if (maxHealth < 0)
            throw new RuntimeException("Negative maxHealth!");
        if (EnumPlayerPart.VALUES[id].ordinal() != this.id)
            throw new RuntimeException("Wrong player mapping!");
    }

    public static class Handler {

        public static void onMessage(MessageUpdatePart message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            CommonUtils.checkClient(ctx);
            ctx.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
                if (damageModel == null) return;
                AbstractDamageablePart damageablePart = damageModel.getFromEnum(EnumPlayerPart.VALUES[message.id]);
                damageablePart.setMaxHealth(message.maxHealth);
                damageablePart.setAbsorption(message.absorption);
                damageablePart.currentHealth = message.currentHealth;
            });
        }
    }
}
