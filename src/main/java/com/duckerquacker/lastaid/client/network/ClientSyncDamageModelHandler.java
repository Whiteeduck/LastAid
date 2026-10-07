package com.duckerquacker.lastaid.client.network;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.client.HUDHandler;
import com.duckerquacker.lastaid.common.CapProvider;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import com.duckerquacker.lastaid.common.network.MessageClientRequest;
import com.duckerquacker.lastaid.common.network.MessageSyncDamageModel;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class ClientSyncDamageModelHandler {

    private ClientSyncDamageModelHandler() {
    }

    public static void handle(MessageSyncDamageModel message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            LastAid.isSynced = false;
            return;
        }

        Player targetPlayer = resolveTargetPlayer(minecraft, message.entityId());
        if (targetPlayer == null) {
            if (message.entityId() == minecraft.player.getId()) {
                LastAid.isSynced = false;
                LastAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
            }
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(targetPlayer);
        if (damageModel == null) {
            if (targetPlayer == minecraft.player) {
                LastAid.isSynced = false;
                LastAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
            }
            return;
        }

        boolean wasUnconscious = isUnconscious(damageModel);
        // Apply saved max/current health first so scale logic does not clamp against stale caps.
        damageModel.deserializeNBT(message.playerDamageModel());
        if (message.shouldScaleMaxHealth()) {
            damageModel.runScaleLogic(targetPlayer);
        }
        if (wasUnconscious != isUnconscious(damageModel)) {
            targetPlayer.refreshDimensions();
        }
        if (targetPlayer == minecraft.player) {
            if (damageModel.hasTutorial) {
                CapProvider.tutorialDone.add(minecraft.player.getName().getString());
            }
            HUDHandler.INSTANCE.ticker = 200;
            LastAid.isSynced = true;
        }
    }

    private static Player resolveTargetPlayer(Minecraft minecraft, int entityId) {
        if (minecraft.player != null && minecraft.player.getId() == entityId) {
            return minecraft.player;
        }

        return minecraft.level.getEntity(entityId) instanceof Player targetPlayer ? targetPlayer : null;
    }

    private static boolean isUnconscious(AbstractPlayerDamageModel damageModel) {
        return damageModel instanceof PlayerDamageModel playerDamageModel
                ? playerDamageModel.isUnconscious()
                : damageModel.getUnconsciousTicks() > 0;
    }
}
