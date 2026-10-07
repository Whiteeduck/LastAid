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

package com.duckerquacker.lastaid.common;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.FirstAidConfig;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import com.duckerquacker.lastaid.common.network.MessageSyncCommandSettings;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class FirstAidCommand {

    private FirstAidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        MedicalSettingCommands.register(dispatcher);
        dispatcher.register(Commands.literal("lastaid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pain")
                        .then(Commands.literal("dynamic")
                                .executes(context -> setDynamicPain(context.getSource(), true)))
                        .then(Commands.literal("mild")
                                .executes(context -> setDynamicPain(context.getSource(), false)))
                        .then(Commands.literal("display")
                                .then(Commands.literal("vignette")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainVignette(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainVignette(context.getSource(), false))))
                                .then(Commands.literal("hitpulse")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainVignette(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainVignette(context.getSource(), false))))
                                .then(Commands.literal("blur")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainBlur(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainBlur(context.getSource(), false))))
                                .then(Commands.literal("fov")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainFovCompression(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainFovCompression(context.getSource(), false))))
                                .then(Commands.literal("audio")
                                        .then(Commands.literal("on")
                                                .executes(context -> setPainAudioEffects(context.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(context -> setPainAudioEffects(context.getSource(), false))))))
                .then(buildAdrenalineBranch("suppression"))
            .then(buildAdrenalineBranch("adrenaline"))
                .then(Commands.literal("revivewakeup")
                        .then(Commands.literal("on")
                                .executes(context -> setRescueWakeUp(context.getSource(), true))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                        .executes(context -> setRescueWakeUpDelay(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("off")
                                .executes(context -> setRescueWakeUp(context.getSource(), false))))
                .then(Commands.literal("naturalregen")
                        .then(Commands.literal("off")
                                .executes(context -> setNaturalRegenMode(context.getSource(), LastAid.NaturalRegenMode.OFF)))
                        .then(buildNaturalRegenBranch("limited", LastAid.NaturalRegenMode.LIMITED))
                        .then(buildNaturalRegenBranch("limited2", LastAid.NaturalRegenMode.LIMITED2))
                        .then(buildNaturalRegenBranch("full", LastAid.NaturalRegenMode.FULL)))
                .then(Commands.literal("randomdamage")
                        .then(Commands.literal("friendly")
                                .executes(context -> setFriendlyRandomDistribution(context.getSource(), true))
                                .then(Commands.literal("chance")
                                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                                                .executes(context -> setFriendlyRandomDistributionChance(context.getSource(), DoubleArgumentType.getDouble(context, "percent"))))))
                        .then(Commands.literal("normal")
                                .executes(context -> setFriendlyRandomDistribution(context.getSource(), false))))
                .then(Commands.literal("medicineeffect")
                        .then(Commands.literal("realistic")
                                .executes(context -> setMedicineEffectMode(context.getSource(), LastAid.MedicineEffectMode.REALISTIC)))
                        .then(Commands.literal("assisted")
                                .executes(context -> setMedicineEffectMode(context.getSource(), LastAid.MedicineEffectMode.ASSISTED)))
                        .then(Commands.literal("casual")
                                .executes(context -> setMedicineEffectMode(context.getSource(), LastAid.MedicineEffectMode.CASUAL))))
                .then(Commands.literal("injurydebuff")
                        .then(Commands.literal("normal")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), LastAid.InjuryDebuffMode.NORMAL)))
                        .then(Commands.literal("low")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), LastAid.InjuryDebuffMode.LOW)))
                        .then(Commands.literal("off")
                                .executes(context -> setInjuryDebuffMode(context.getSource(), LastAid.InjuryDebuffMode.OFF)))
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .then(Commands.literal("normal")
                                        .executes(context -> setInjuryDebuffModeForEffect(context.getSource(), StringArgumentType.getString(context, "effect"), LastAid.InjuryDebuffMode.NORMAL)))
                                .then(Commands.literal("low")
                                        .executes(context -> setInjuryDebuffModeForEffect(context.getSource(), StringArgumentType.getString(context, "effect"), LastAid.InjuryDebuffMode.LOW)))
                                .then(Commands.literal("off")
                                        .executes(context -> setInjuryDebuffModeForEffect(context.getSource(), StringArgumentType.getString(context, "effect"), LastAid.InjuryDebuffMode.OFF))))));
        dispatcher.register(Commands.literal("lastaid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("commandtips")
                        .then(Commands.literal("on")
                                .executes(context -> setCommandTips(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setCommandTips(context.getSource(), false)))));
        dispatcher.register(Commands.literal("lastaid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("addiction")
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> queryAddiction(context.getSource(), EntityArgument.getPlayer(context, "player"))))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 100.0F))
                                                .executes(context -> setAddiction(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        FloatArgumentType.getFloat(context, "value"))))))));
    }

    private static int queryAddiction(CommandSourceStack source, ServerPlayer target) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(target);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel)) {
            source.sendFailure(Component.translatable("lastaid.command.addiction.unavailable"));
            return 0;
        }
        float value = playerDamageModel.getAddictionValue();
        source.sendSuccess(() -> Component.translatable(
                "lastaid.command.addiction.query",
                target.getDisplayName(),
                String.format(java.util.Locale.ROOT, "%.1f", value)
        ), false);
        return 1;
    }

    private static int setAddiction(CommandSourceStack source, ServerPlayer target, float value) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(target);
        if (!(damageModel instanceof PlayerDamageModel playerDamageModel)) {
            source.sendFailure(Component.translatable("lastaid.command.addiction.unavailable"));
            return 0;
        }
        playerDamageModel.setAddictionValue(value);
        CommonUtils.syncDamageModel(target);
        source.sendSuccess(() -> Component.translatable(
                "lastaid.command.addiction.set",
                target.getDisplayName(),
                String.format(java.util.Locale.ROOT, "%.1f", playerDamageModel.getAddictionValue())
        ), true);
        return 1;
    }

    private static int setCommandTips(CommandSourceStack source, boolean enabled) {
        FirstAidConfig.SERVER.commandTipsEnabled.set(enabled);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.commandtips.on"
                : "lastaid.command.commandtips.off"), true);
        return 1;
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdrenalineFatigueBranch() {
        return Commands.literal("fatigue")
            .then(Commands.literal("on").executes(context -> setAdrenalineFatigueEnabled(context.getSource(), true)))
            .then(Commands.literal("off").executes(context -> setAdrenalineFatigueEnabled(context.getSource(), false)))
            .then(Commands.literal("threshold")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                    .executes(context -> setAdrenalineFatigueThreshold(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
            .then(Commands.literal("ratio")
                .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0D, 100.0D))
                    .executes(context -> setAdrenalineFatigueRatio(context.getSource(), DoubleArgumentType.getDouble(context, "percent")))));
    }

    private static int setAdrenalineFatigueEnabled(CommandSourceStack source, boolean enabled) {
        FirstAidConfig.SERVER.adrenalineFatigueEnabled.set(enabled);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable(enabled ? "lastaid.command.adrenaline.fatigue.on" : "lastaid.command.adrenaline.fatigue.off"), true);
        return 1;
    }

    private static int setAdrenalineFatigueThreshold(CommandSourceStack source, int seconds) {
        FirstAidConfig.SERVER.adrenalineFatigueThresholdSeconds.set(seconds);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("lastaid.command.adrenaline.fatigue.threshold", seconds), true);
        return 1;
    }

    private static int setAdrenalineFatigueRatio(CommandSourceStack source, double percent) {
        FirstAidConfig.SERVER.adrenalineFatigueDurationRatio.set(percent / 100.0D);
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("lastaid.command.adrenaline.fatigue.ratio", percent), true);
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildAdrenalineBranch(String literalName) {
        return Commands.literal(literalName)
                  .then(buildAdrenalineFatigueBranch())
                  .then(Commands.literal("gain")
                     .then(Commands.argument("coefficient", DoubleArgumentType.doubleArg(0.01D, 1.0D))
                        .executes(context -> setSuppressionGain(context.getSource(), DoubleArgumentType.getDouble(context, "coefficient")))))
                        .then(Commands.literal("dynamic")
                                .executes(context -> setLowSuppression(context.getSource(), false)))
                        .then(Commands.literal("mild")
                                .executes(context -> setLowSuppression(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setProjectileSuppression(context.getSource(), false)))
                        .then(Commands.literal("blacklist")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("entity", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                                .executes(context -> addSuppressionBlacklistEntry(context.getSource(), StringArgumentType.getString(context, "entity")))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("entity", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(LastAid.suppressionEntityBlacklist, builder))
                                                .executes(context -> removeSuppressionBlacklistEntry(context.getSource(), StringArgumentType.getString(context, "entity"))))));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> buildNaturalRegenBranch(String literal, LastAid.NaturalRegenMode mode) {
        return Commands.literal(literal)
                .executes(context -> setNaturalRegenMode(context.getSource(), mode))
                .then(Commands.literal("critical")
                        .executes(context -> setNaturalRegenModeAndStrategy(context.getSource(), mode, LastAid.NaturalRegenStrategy.CRITICAL)))
                .then(Commands.literal("random")
                        .executes(context -> setNaturalRegenModeAndStrategy(context.getSource(), mode, LastAid.NaturalRegenStrategy.RANDOM)));
    }

    private static int setDynamicPain(CommandSourceStack source, boolean enabled) {
        LastAid.dynamicPainEnabled = enabled;
        if (source.getServer() != null) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel instanceof PlayerDamageModel playerDamageModel) {
                    playerDamageModel.refreshPainState(player);
                    CommonUtils.syncDamageModel(player);
                }
            }
        }
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.pain.dynamic"
                : "lastaid.command.pain.mild"), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setPainVignette(CommandSourceStack source, boolean enabled) {
        LastAid.enablePainVignette = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.pain.display.vignette.on"
                : "lastaid.command.pain.display.vignette.off"), true);
        return 1;
    }
    private static int setPainBlur(CommandSourceStack source, boolean enabled) {
        LastAid.enablePainBlur = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("lastaid.command.pain.display.blur." + (enabled ? "on" : "off")), true);
        return 1;
    }


    private static int setPainFovCompression(CommandSourceStack source, boolean enabled) {
        LastAid.enablePainFovCompression = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.pain.display.fov.on"
                : "lastaid.command.pain.display.fov.off"), true);
        return 1;
    }

    private static int setPainAudioEffects(CommandSourceStack source, boolean enabled) {
        LastAid.enablePainAudioEffects = enabled;
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.pain.display.audio.on"
                : "lastaid.command.pain.display.audio.off"), true);
        return 1;
    }

    private static int setSuppressionGain(CommandSourceStack source, double coefficient) {
        LastAid.suppressionGainMultiplier = (float) coefficient;
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.literal("Adrenaline gain coefficient set to " + coefficient), true);
        return 1;
    }

    private static int setLowSuppression(CommandSourceStack source, boolean enabled) {
        LastAid.projectileSuppressionEnabled = true;
        LastAid.lowSuppressionEnabled = enabled;
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.suppression.mild"
                : "lastaid.command.suppression.dynamic"), true);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        return 1;
    }

    private static int setProjectileSuppression(CommandSourceStack source, boolean enabled) {
        LastAid.projectileSuppressionEnabled = enabled;
        source.sendSuccess(() -> Component.translatable("lastaid.command.suppression.off"), true);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        return 1;
    }

    private static int addSuppressionBlacklistEntry(CommandSourceStack source, String entityInput) {
        ResourceLocation entityId = parseEntityId(source, entityInput);
        if (entityId == null) {
            return 0;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
            source.sendFailure(Component.translatable("lastaid.command.suppression.blacklist.unknown", entityId.toString()));
            return 0;
        }
        LastAid.suppressionEntityBlacklist.add(entityId);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("lastaid.command.suppression.blacklist.add", entityId.toString()), true);
        return 1;
    }

    private static int removeSuppressionBlacklistEntry(CommandSourceStack source, String entityInput) {
        ResourceLocation entityId = parseEntityId(source, entityInput);
        if (entityId == null) {
            return 0;
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
            source.sendFailure(Component.translatable("lastaid.command.suppression.blacklist.unknown", entityId.toString()));
            return 0;
        }
        LastAid.suppressionEntityBlacklist.remove(entityId);
        FirstAidConfig.persistCommandSettings();
        syncCommandSettings(source);
        source.sendSuccess(() -> Component.translatable("lastaid.command.suppression.blacklist.remove", entityId.toString()), true);
        return 1;
    }

    private static void syncCommandSettings(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            LastAid.NETWORKING.sendCommandSettingsSync(player, MessageSyncCommandSettings.current());
        }
    }

    private static int setRescueWakeUp(CommandSourceStack source, boolean enabled) {
        LastAid.rescueWakeUpEnabled = enabled;
        FirstAidConfig.persistCommandSettings();
        refreshRescueWakeUpState(source);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.revivewakeup.on"
                : "lastaid.command.revivewakeup.off"), true);
        return 1;
    }

    private static int setRescueWakeUpDelay(CommandSourceStack source, int seconds) {
        LastAid.rescueWakeUpEnabled = true;
        LastAid.rescueWakeUpDelaySeconds = seconds;
        FirstAidConfig.persistCommandSettings();
        refreshRescueWakeUpState(source);
        source.sendSuccess(() -> Component.translatable("lastaid.command.revivewakeup.time", seconds), true);
        return 1;
    }

    private static int setNaturalRegenMode(CommandSourceStack source, LastAid.NaturalRegenMode mode) {
        LastAid.naturalRegenMode = mode;
        FirstAidConfig.persistCommandSettings();
        refreshNaturalRegenState(source);
        source.sendSuccess(() -> Component.translatable("lastaid.command.naturalregen.mode", Component.translatable(getNaturalRegenModeKey(mode))), true);
        return 1;
    }

    private static int setNaturalRegenModeAndStrategy(CommandSourceStack source, LastAid.NaturalRegenMode mode, LastAid.NaturalRegenStrategy strategy) {
        LastAid.naturalRegenMode = mode;
        LastAid.naturalRegenStrategy = strategy;
        FirstAidConfig.persistCommandSettings();
        refreshNaturalRegenState(source);
        source.sendSuccess(() -> Component.translatable(
                "lastaid.command.naturalregen.mode_strategy",
                Component.translatable(getNaturalRegenModeKey(mode)),
                Component.translatable(getNaturalRegenStrategyKey(strategy))), true);
        return 1;
    }

    private static void refreshRescueWakeUpState(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (CommonUtils.getDamageModel(player) instanceof PlayerDamageModel playerDamageModel && playerDamageModel.refreshRescueWakeUpState(player)) {
                CommonUtils.syncDamageModel(player);
            }
        }
    }

    private static void refreshNaturalRegenState(CommandSourceStack source) {
        if (source.getServer() == null) {
            return;
        }
        boolean enabled = LastAid.naturalRegenMode != LastAid.NaturalRegenMode.OFF;
        source.getServer().getAllLevels().forEach(level ->
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(enabled, source.getServer()));
    }

    private static String getNaturalRegenModeKey(LastAid.NaturalRegenMode mode) {
        return switch (mode) {
            case OFF -> "lastaid.command.naturalregen.mode_value.off";
            case FULL -> "lastaid.command.naturalregen.mode_value.full";
            case LIMITED -> "lastaid.command.naturalregen.mode_value.limited";
            case LIMITED2 -> "lastaid.command.naturalregen.mode_value.limited2";
        };
    }

    private static String getNaturalRegenStrategyKey(LastAid.NaturalRegenStrategy strategy) {
        return switch (strategy) {
            case RANDOM -> "lastaid.command.naturalregen.strategy_value.random";
            case CRITICAL -> "lastaid.command.naturalregen.strategy_value.critical";
        };
    }

    private static int setFriendlyRandomDistribution(CommandSourceStack source, boolean enabled) {
        LastAid.useFriendlyRandomDistribution = enabled;
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable(enabled
                ? "lastaid.command.randomdamage.friendly"
                : "lastaid.command.randomdamage.normal"), true);
        return 1;
    }

    private static int setFriendlyRandomDistributionChance(CommandSourceStack source, double percent) {
        LastAid.friendlyRandomDistributionChance = LastAid.clampFriendlyRandomDistributionChance((float) (percent / 100.0D));
        FirstAidConfig.persistCommandSettings();
        source.sendSuccess(() -> Component.translatable("lastaid.command.randomdamage.chance", Math.round(LastAid.friendlyRandomDistributionChance * 1000.0F) / 10.0F), true);
        return 1;
    }

    private static int setMedicineEffectMode(CommandSourceStack source, LastAid.MedicineEffectMode mode) {
        LastAid.medicineEffectMode = mode;
        LastAid.medicineTimingMultiplier = mode.getTimingMultiplier();
        String key = switch (mode) {
            case ASSISTED -> "lastaid.command.medicineeffect.assisted";
            case CASUAL -> "lastaid.command.medicineeffect.casual";
            default -> "lastaid.command.medicineeffect.realistic";
        };
        source.sendSuccess(() -> Component.translatable(key), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setInjuryDebuffMode(CommandSourceStack source, LastAid.InjuryDebuffMode mode) {
        LastAid.injuryDebuffMode = mode;
        String key = switch (mode) {
            case LOW -> "lastaid.command.injurydebuff.low";
            case OFF -> "lastaid.command.injurydebuff.off";
            default -> "lastaid.command.injurydebuff.normal";
        };
        source.sendSuccess(() -> Component.translatable(key), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static int setInjuryDebuffModeForEffect(CommandSourceStack source, String effectInput, LastAid.InjuryDebuffMode mode) {
        ResourceLocation effectId = parseEffectId(source, effectInput);
        if (effectId == null) {
            return 0;
        }
        if (BuiltInRegistries.MOB_EFFECT.get(effectId) == null) {
            source.sendFailure(Component.translatable("lastaid.command.injurydebuff.effect.unknown", effectId.toString()));
            return 0;
        }
        LastAid.setInjuryDebuffOverride(effectId, mode);
        String key = switch (mode) {
            case LOW -> "lastaid.command.injurydebuff.effect.low";
            case OFF -> "lastaid.command.injurydebuff.effect.off";
            default -> "lastaid.command.injurydebuff.effect.normal";
        };
        source.sendSuccess(() -> Component.translatable(key, effectId.toString()), true);
        FirstAidConfig.persistCommandSettings();
        return 1;
    }

    private static ResourceLocation parseEffectId(CommandSourceStack source, String input) {
        ResourceLocation effectId = ResourceLocation.tryParse(input);
        if (effectId == null) {
            effectId = ResourceLocation.tryParse("minecraft:" + input);
        }
        if (effectId == null) {
            source.sendFailure(Component.translatable("lastaid.command.injurydebuff.effect.invalid", input));
        }
        return effectId;
    }

    private static ResourceLocation parseEntityId(CommandSourceStack source, String input) {
        ResourceLocation entityId = ResourceLocation.tryParse(input);
        if (entityId == null) {
            entityId = ResourceLocation.tryParse("minecraft:" + input);
        }
        if (entityId == null) {
            source.sendFailure(Component.translatable("lastaid.command.suppression.blacklist.invalid", input));
        }
        return entityId;
    }
}
