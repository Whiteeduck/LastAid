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

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.client.ClientHooks;
import com.duckerquacker.lastaid.client.HUDHandler;
import com.duckerquacker.lastaid.common.CapProvider;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import com.duckerquacker.lastaid.common.util.LoggingMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import com.duckerquacker.lastaid.shim.forge.network.NetworkEvent;

import java.util.function.Supplier;

public class MessageConfiguration {

    private final CompoundTag playerDamageModel;

    public MessageConfiguration(CompoundTag model) {
        this.playerDamageModel = model;
    }

    public MessageConfiguration(FriendlyByteBuf buffer) {
        this(buffer.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(playerDamageModel);
    }

    public static class Handler {

        public static void onMessage(MessageConfiguration message, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            CommonUtils.checkClient(ctx);

            LastAid.LOGGER.info(LoggingMarkers.NETWORK, "Received remote damage model");
            ctx.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
                if (damageModel == null) return;
                damageModel.deserializeNBT(message.playerDamageModel);
                if (damageModel.hasTutorial)
                    CapProvider.tutorialDone.add(Minecraft.getInstance().player.getName().getString());
                else
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("[Last Aid] " + I18n.get("lastaid.tutorial.hint", ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString())));
                HUDHandler.INSTANCE.ticker = 200;
                LastAid.isSynced = true;
                LastAid.LOGGER.debug(LoggingMarkers.NETWORK, "Sync complete");
            });
        }
    }
}
