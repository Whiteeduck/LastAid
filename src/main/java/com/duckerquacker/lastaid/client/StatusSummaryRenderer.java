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

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.medicine.MedicineStatusDisplay;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

public final class StatusSummaryRenderer {
    private StatusSummaryRenderer() {
    }

    public static int renderStatusSummary(
            GuiGraphics guiGraphics,
            Font font,
            Player player,
            AbstractPlayerDamageModel damageModel,
            @Nullable PlayerDamageModel playerDamageModel,
            int baseX,
            int baseY
    ) {
        int lineY = baseY;

        if (damageModel.getPainLevel() > 0) {
            boolean painSuppressed = playerDamageModel != null && playerDamageModel.isPainSuppressed(player);
            Component painText = painSuppressed
                    ? Component.translatable("lastaid.gui.status.pain_suppressed")
                    : Component.translatable("lastaid.gui.status.pain", Component.translatable(getPainSeverityKey(damageModel.getPainLevel())));
            guiGraphics.drawString(font, painText, baseX, lineY, painSuppressed ? 9425919 : 16747146);
            lineY += 10;
        }

        if (damageModel.getAdrenalineLevel() > 0) {
            int suppressionLevel = playerDamageModel != null ? playerDamageModel.getSuppressionLevel() : damageModel.getAdrenalineLevel();
            guiGraphics.drawString(
                    font,
                    Component.translatable("lastaid.gui.status.suppression", Component.translatable(getSuppressionSeverityKey(suppressionLevel))),
                    baseX,
                    lineY,
                    12637930
            );
            lineY += 10;
        }

        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            if ("lastaid:adrenaline".equals(display.getStatusId().toString())) continue;
            lineY = MedicineStatusClientHelper.drawStatusLine(guiGraphics, font, display, baseX, lineY);
        }

        return lineY;
    }

    public static int countVisibleLines(AbstractPlayerDamageModel damageModel, Player player) {
        int count = 0;
        if (damageModel.getPainLevel() > 0) count++;
        if (damageModel.getAdrenalineLevel() > 0) count++;
        for (MedicineStatusDisplay display : MedicineStatusClientHelper.collect(player)) {
            if ("lastaid:adrenaline".equals(display.getStatusId().toString())) continue;
            count++;
        }
        return count;
    }

    private static String getPainSeverityKey(int painLevel) {
        return switch (painLevel) {
            case 1 -> "lastaid.gui.pain.mild";
            case 2 -> "lastaid.gui.pain.moderate";
            case 3 -> "lastaid.gui.pain.severe";
            case 4 -> "lastaid.gui.pain.extreme";
            default -> "lastaid.gui.pain.critical";
        };
    }

    private static String getSuppressionSeverityKey(int suppressionLevel) {
        return switch (suppressionLevel) {
            case 1 -> "lastaid.gui.suppression.low";
            case 2 -> "lastaid.gui.suppression.medium";
            default -> "lastaid.gui.suppression.high";
        };
    }
}
