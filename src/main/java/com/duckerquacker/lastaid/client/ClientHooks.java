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

package com.duckerquacker.lastaid.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.client.gui.GuiHealthScreen;
import com.duckerquacker.lastaid.client.util.EventCalendar;
import com.duckerquacker.lastaid.common.network.MessageClientRequest;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import org.lwjgl.glfw.GLFW;


public class ClientHooks {
    private static final String CATEGORY = "key.categories.lastaid";
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping GIVE_UP = new KeyMapping("keybinds.give_up", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public static void setup(IEventBus modEventBus) {
        LastAid.LOGGER.debug("Loading ClientHooks");
        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.ConcussionClientEffects.class);
        com.duckerquacker.lastaid.client.medical.ConcussionClientEffects.init();
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.LimbRenderHelper.class);
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.AmputationFeedback.class);
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.OverdoseClientEffects.class);
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.CarryClient.class);
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.BloodDecals.class);
        NeoForge.EVENT_BUS.register(com.duckerquacker.lastaid.client.medical.ItemTooltips.class);
        modEventBus.addListener(ClientHooks::registerKeybindEvent);
        modEventBus.addListener(ClientHooks::addPlayerLayers);
        modEventBus.addListener(ClientHooks::registerOverlayEvent);
        modEventBus.addListener(ClientHooks::registerReloadListenerEvent);
        EventCalendar.checkDate();
    }

    public static boolean showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientEventHandler.hasValidPendingHealingSelection(activeHand)) {
            return false;
        }

        if (!ClientEventHandler.canOpenHealingScreen(activeHand)) {
            return false;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) {
            return false;
        }

        LastAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.setScreen(GuiHealthScreen.INSTANCE);
        return true;
    }

    public static boolean beginApplyHealthUse(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientEventHandler.hasValidPendingHealingSelection(hand)) {
            return false;
        }

        mc.player.startUsingItem(hand);
        return true;
    }

    public static void registerKeybindEvent(RegisterKeyMappingsEvent event) {
        event.register(ClientHooks.SHOW_WOUNDS);
        event.register(ClientHooks.GIVE_UP);
    }

    public static void registerOverlayEvent(RegisterGuiLayersEvent event) {
        com.duckerquacker.lastaid.client.medical.ConcussionClientEffects.registerLayers(event);
        com.duckerquacker.lastaid.client.medical.AmputationFeedback.registerLayers(event);
        com.duckerquacker.lastaid.client.medical.HeadBandageLayer.registerLayers(event);
        com.duckerquacker.lastaid.client.medical.CarryClient.registerLayers(event);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(LastAid.MODID, "status_overlay"), StatusEffectLayer.INSTANCE);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(LastAid.MODID, "hud_overlay"), HUDHandler.INSTANCE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addPlayerLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new com.duckerquacker.lastaid.client.medical.FrostbiteTintLayer(playerRenderer));
                playerRenderer.addLayer(new com.duckerquacker.lastaid.client.medical.HeadBandageLayer(playerRenderer));
                playerRenderer.addLayer(new com.duckerquacker.lastaid.client.medical.MouthItemLayer(playerRenderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    public static void registerReloadListenerEvent(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(HUDHandler.INSTANCE);
    }
}


