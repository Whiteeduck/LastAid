package com.duckerquacker.lastaid.common.medical;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.duckerquacker.lastaid.api.medical.ConditionState;
import com.duckerquacker.lastaid.api.medical.MedicalCondition;
import com.duckerquacker.lastaid.api.medical.MedicalConditions;
import com.duckerquacker.lastaid.common.medical.cold.CommonCold;
import com.duckerquacker.lastaid.common.medical.cold.Frostbite;
import com.duckerquacker.lastaid.common.medical.cold.Hypothermia;
import com.duckerquacker.lastaid.common.medical.concussion.Concussion;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MedicalCommands {
   private MedicalCommands() {
   }

   private static final String[] RESTORE_TARGETS = new String[]{"all", "right_arm", "left_arm", "right_leg", "left_leg"};

   /** Permission check is evaluated at use time, so the [commands] requireOp option works without a restart. */
   private static boolean allowed(CommandSourceStack source) {
      boolean requireOp = !MedicalConfig.SERVER_SPEC.isLoaded() || MedicalConfig.SERVER.commands.requireOp.get();
      return !requireOp || source.hasPermission(2);
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("medical").requires(MedicalCommands::allowed);
      root.then(
         Commands.literal("concussion")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("severity", IntegerArgumentType.integer(0, 3))
                        .executes(c -> setConcussion(c.getSource(), EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "severity")))
                  )
                  .then(
                     Commands.literal("progress")
                        .then(
                           Commands.argument("amount", FloatArgumentType.floatArg(-10000.0F, 10000.0F))
                              .executes(c -> addProgress(c.getSource(), EntityArgument.getPlayers(c, "targets"), FloatArgumentType.getFloat(c, "amount")))
                        )
                  )
            )
      );
      root.then(
         Commands.literal("cold")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("stage", IntegerArgumentType.integer(0, 3))
                        .executes(c -> setCold(c.getSource(), EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "stage")))
                  )
            )
      );
      root.then(
         Commands.literal("frostbite")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("limb", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Frostbite.Limb.VALUES).map(Frostbite.Limb::key), b))
                        .then(
                           Commands.argument("stage", IntegerArgumentType.integer(0, 4))
                              .executes(
                                 c -> setFrostbite(
                                    c.getSource(),
                                    EntityArgument.getPlayers(c, "targets"),
                                    StringArgumentType.getString(c, "limb"),
                                    IntegerArgumentType.getInteger(c, "stage")
                                 )
                              )
                        )
                  )
            )
      );
      root.then(
         Commands.literal("overdose")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("drug", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"morphine", "adrenaline"}, b))
                        .then(
                           Commands.argument("doses", FloatArgumentType.floatArg(0.0F, 50.0F))
                              .executes(
                                 c -> setOverdose(
                                    c.getSource(),
                                    EntityArgument.getPlayers(c, "targets"),
                                    StringArgumentType.getString(c, "drug"),
                                    FloatArgumentType.getFloat(c, "doses")
                                 )
                              )
                        )
                  )
            )
      );
      root.then(
         Commands.literal("fracture")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("limb", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Frostbite.Limb.VALUES).map(Frostbite.Limb::key), b))
                        .then(
                           Commands.argument("type", IntegerArgumentType.integer(0, 3))
                              .executes(c -> bones(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "limb"), IntegerArgumentType.getInteger(c, "type"), false))
                        )
                  )
            )
      );
      root.then(
         Commands.literal("wound")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("zone", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(com.duckerquacker.lastaid.common.medical.wounds.Zone.VALUES).map(com.duckerquacker.lastaid.common.medical.wounds.Zone::key), b))
                        .then(
                           Commands.argument("severity", IntegerArgumentType.integer(0, 4))
                              .executes(c -> wound(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "zone"), IntegerArgumentType.getInteger(c, "severity"), false))
                              .then(Commands.literal("arrow")
                                 .executes(c -> wound(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "zone"), IntegerArgumentType.getInteger(c, "severity"), true)))
                        )
                  )
            )
      );
      root.then(
         Commands.literal("infection")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("stage", IntegerArgumentType.integer(0, 4))
                        .executes(c -> infection(c.getSource(), EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "stage"), null))
                        .then(
                           Commands.argument("zone", StringArgumentType.word())
                              .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(com.duckerquacker.lastaid.common.medical.wounds.Zone.VALUES).map(com.duckerquacker.lastaid.common.medical.wounds.Zone::key), b))
                              .executes(c -> infection(c.getSource(), EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "stage"), StringArgumentType.getString(c, "zone")))
                        )
                  )
            )
      );
      root.then(
         Commands.literal("dirt")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("zone", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(com.duckerquacker.lastaid.common.medical.wounds.Zone.VALUES).map(com.duckerquacker.lastaid.common.medical.wounds.Zone::key), b))
                        .then(
                           Commands.argument("days", FloatArgumentType.floatArg(0.0F, 30.0F))
                              .executes(c -> dirt(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "zone"), FloatArgumentType.getFloat(c, "days")))
                        )
                  )
            )
      );
      root.then(
         Commands.literal("log")
            .then(Commands.argument("target", EntityArgument.player()).executes(c -> log(c.getSource(), EntityArgument.getPlayer(c, "target"))))
      );
      root.then(
         Commands.literal("dislocate")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("limb", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Frostbite.Limb.VALUES).map(Frostbite.Limb::key), b))
                        .then(
                           Commands.argument("on", IntegerArgumentType.integer(0, 1))
                              .executes(c -> bones(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "limb"), IntegerArgumentType.getInteger(c, "on"), true))
                        )
                  )
            )
      );
      root.then(
         Commands.literal("bonetime")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("days", FloatArgumentType.floatArg(0.0F, 100.0F))
                        .executes(c -> {
                           for (ServerPlayer player : EntityArgument.getPlayers(c, "targets")) {
                              com.duckerquacker.lastaid.common.medical.bone.Bones.advance(player, FloatArgumentType.getFloat(c, "days"));
                           }
                           c.getSource().sendSuccess(() -> Component.literal("OK"), true);
                           return 1;
                        })
                  )
            )
      );
      root.then(Commands.literal("sneeze").then(Commands.argument("target", EntityArgument.player()).executes(c -> {
         ServerPlayer serverPlayer = EntityArgument.getPlayer(c, "target");
         CommonCold.sneeze(serverPlayer, Math.max(1, CommonCold.INSTANCE.state(serverPlayer).stage));
         return 1;
      })));
      root.then(
         Commands.literal("clear").then(Commands.argument("targets", EntityArgument.players()).executes(c -> clear(c.getSource(), EntityArgument.getPlayers(c, "targets"))))
      );
      root.then(
         Commands.literal("heal").then(Commands.argument("targets", EntityArgument.players()).executes(c -> heal(c.getSource(), EntityArgument.getPlayers(c, "targets"))))
      );
      root.then(
         Commands.literal("restore")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("limb", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(RESTORE_TARGETS, b))
                        .executes(c -> restore(c.getSource(), EntityArgument.getPlayers(c, "targets"), StringArgumentType.getString(c, "limb")))
                  )
            )
      );
      root.then(
         Commands.literal("amputate")
            .then(
               Commands.argument("targets", EntityArgument.players())
                  .then(
                     Commands.argument("limb", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Frostbite.Limb.VALUES).map(Frostbite.Limb::key), b))
                        .executes(c -> {
                           String name = StringArgumentType.getString(c, "limb");
                           Frostbite.Limb limb = null;
                           for (Frostbite.Limb candidate : Frostbite.Limb.VALUES) {
                              if (candidate.key().equalsIgnoreCase(name)) {
                                 limb = candidate;
                              }
                           }
                           if (limb == null) {
                              c.getSource().sendFailure(Component.translatable("lastaid.command.unknown_limb", name));
                              return 0;
                           }
                           Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
                           for (ServerPlayer serverPlayer : players) {
                              Frostbite.INSTANCE.amputate(serverPlayer, limb);
                           }
                           c.getSource().sendSuccess(() -> Component.literal("OK"), true);
                           return players.size();
                        })
                  )
            )
      );
      root.then(Commands.literal("info").then(Commands.argument("target", EntityArgument.player()).executes(c -> info(c.getSource(), EntityArgument.getPlayer(c, "target")))));
      com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> medical = dispatcher.register(root);
      // same subcommands under /lastaid (merged with the original First Aid /lastaid settings commands)
      LiteralArgumentBuilder<CommandSourceStack> alias = Commands.literal("lastaid").requires(MedicalCommands::allowed);
      for (com.mojang.brigadier.tree.CommandNode<CommandSourceStack> child : medical.getChildren()) {
         alias.then(child);
      }
      dispatcher.register(alias);
   }

   private static com.duckerquacker.lastaid.common.medical.wounds.Zone zone(CommandSourceStack source, String key) {
      for (com.duckerquacker.lastaid.common.medical.wounds.Zone zone : com.duckerquacker.lastaid.common.medical.wounds.Zone.VALUES) {
         if (zone.key().equalsIgnoreCase(key)) {
            return zone;
         }
      }
      source.sendFailure(Component.literal("Unknown zone: " + key));
      return null;
   }

   private static int wound(CommandSourceStack source, Collection<ServerPlayer> players, String zoneKey, int severity, boolean arrow) {
      com.duckerquacker.lastaid.common.medical.wounds.Zone zone = zone(source, zoneKey);
      if (zone == null) {
         return 0;
      }
      for (ServerPlayer player : players) {
         if (severity == 0) {
            com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.clear(player, com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.state(player));
         } else {
            com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.addWound(player, zone, severity, arrow);
         }
      }
      source.sendSuccess(() -> Component.literal("OK"), true);
      return players.size();
   }

   private static int infection(CommandSourceStack source, Collection<ServerPlayer> players, int stage, String zoneKey) {
      com.duckerquacker.lastaid.common.medical.wounds.Zone zone = zoneKey == null ? null : zone(source, zoneKey);
      for (ServerPlayer player : players) {
         com.duckerquacker.lastaid.common.medical.infection.Infection.INSTANCE.set(player, stage, zone);
      }
      source.sendSuccess(() -> Component.literal("OK"), true);
      return players.size();
   }

   /** Ages the dressing on this zone by some game days (testing dirt and infection). */
   private static int dirt(CommandSourceStack source, Collection<ServerPlayer> players, String zoneKey, float days) {
      com.duckerquacker.lastaid.common.medical.wounds.Zone zone = zone(source, zoneKey);
      if (zone == null) {
         return 0;
      }
      for (ServerPlayer player : players) {
         com.duckerquacker.lastaid.common.medical.wounds.Wounds.ZoneState state = com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.state(player).get(zone);
         if (state.dressing != 0) {
            state.dirt = 1.0F;
            state.dirtyTicks = (long)(days * 24000.0F);
            com.duckerquacker.lastaid.common.medical.wounds.Wounds.INSTANCE.state(player).markDirty();
         }
      }
      source.sendSuccess(() -> Component.literal("OK"), true);
      return players.size();
   }

   private static int log(CommandSourceStack source, ServerPlayer player) {
      java.util.List<MedicalLog.Entry> entries = MedicalLog.get(player);
      source.sendSuccess(() -> Component.translatable("lastaid.log.header", player.getDisplayName(), entries.size()).withStyle(net.minecraft.ChatFormatting.GOLD), false);
      long now = player.level().getGameTime();
      for (MedicalLog.Entry entry : entries) {
         long minutes = (now - entry.gameTime()) / 1200L;
         source.sendSuccess(() -> Component.translatable("lastaid.log.entry", minutes, entry.text()).withStyle(net.minecraft.ChatFormatting.GRAY), false);
      }
      return entries.size();
   }

   /** Clears every condition, full health, no pain. Amputations stay (see /medical restore). */
   private static int heal(CommandSourceStack source, Collection<ServerPlayer> players) {
      for (ServerPlayer serverPlayer : players) {
         MedicalData medicalData = MedicalHooks.data(serverPlayer);
         for (MedicalCondition medicalCondition : MedicalConditions.all()) {
            clearCondition(serverPlayer, medicalCondition, medicalData);
         }
         Frostbite.State frostbite = Frostbite.INSTANCE.state(serverPlayer);
         for (Frostbite.LimbState limbState : frostbite.limbs) {
            limbState.amputatedAt = -1L;
         }
         frostbite.markDirty();
         com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel damageModel = com.duckerquacker.lastaid.common.util.CommonUtils.getDamageModel(serverPlayer);
         if (damageModel != null) {
            for (com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart part : damageModel) {
               if (!MedicalHooks.isAmputated(serverPlayer, part.part)) {
                  part.currentHealth = part.getMaxHealth();
               }
            }
            if (damageModel instanceof com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel playerDamageModel) {
               playerDamageModel.clearStatusEffects();
               playerDamageModel.syncVanillaHealth(serverPlayer);
            }
            com.duckerquacker.lastaid.common.util.CommonUtils.syncDamageModel(serverPlayer);
         }
         for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect : java.util.List.of(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
            net.minecraft.world.effect.MobEffects.WEAKNESS,
            net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN,
            net.minecraft.world.effect.MobEffects.CONFUSION,
            net.minecraft.world.effect.MobEffects.BLINDNESS
         )) {
            serverPlayer.removeEffect(effect);
         }
         serverPlayer.setTicksFrozen(0);
         MedicalManager.context(serverPlayer).coldDeathSeconds = 0;
         MedicalManager.sync(serverPlayer);
      }
      source.sendSuccess(() -> Component.translatable("lastaid.command.healed", players.size()), true);
      return players.size();
   }

   private static int restore(CommandSourceStack source, Collection<ServerPlayer> players, String limbName) {
      java.util.List<Frostbite.Limb> limbs = new java.util.ArrayList<>();
      if (limbName.equalsIgnoreCase("all")) {
         limbs.addAll(Arrays.asList(Frostbite.Limb.VALUES));
      } else {
         for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
            if (limb.key().equalsIgnoreCase(limbName)) {
               limbs.add(limb);
            }
         }
      }
      if (limbs.isEmpty()) {
         source.sendFailure(Component.translatable("lastaid.command.unknown_limb", limbName));
         return 0;
      }
      int restored = 0;
      for (ServerPlayer serverPlayer : players) {
         for (Frostbite.Limb limb : limbs) {
            if (Frostbite.INSTANCE.restore(serverPlayer, limb)) {
               restored++;
            }
         }
         Amputation.updateMaxHealth(serverPlayer);
         Amputation.syncLimbs(serverPlayer, 0);
         MedicalManager.sync(serverPlayer);
      }
      int count = restored;
      source.sendSuccess(() -> Component.translatable("lastaid.command.restored", count, players.size()), true);
      return restored;
   }

   private static int setConcussion(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection, int i) {
      for (ServerPlayer serverPlayer : collection) {
         Concussion.INSTANCE.inflict(serverPlayer, Concussion.INSTANCE.state(serverPlayer), i, true);
      }

      commandSourceStack.sendSuccess(() -> Component.translatable("lastaid.command.concussion_set", new Object[]{collection.size(), i}), true);
      return collection.size();
   }

   private static int setCold(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection, int i) {
      for (ServerPlayer serverPlayer : collection) {
         CommonCold.State state = CommonCold.INSTANCE.state(serverPlayer);
         state.stage = i;
         state.healProgress = 0.0F;
         state.exposure = 0.0F;
         state.markDirty();
      }

      commandSourceStack.sendSuccess(() -> Component.literal("OK"), true);
      return collection.size();
   }

   private static int setFrostbite(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection, String string, int i) {
      Frostbite.Limb limb = null;

      for (Frostbite.Limb limb1 : Frostbite.Limb.VALUES) {
         if (limb1.key().equalsIgnoreCase(string)) {
            limb = limb1;
         }
      }

      if (limb == null) {
         commandSourceStack.sendFailure(Component.translatable("lastaid.command.unknown_limb", string));
         return 0;
      } else {
         for (ServerPlayer serverPlayer : collection) {
            Frostbite.INSTANCE.setStage(serverPlayer, limb, i);
         }

         commandSourceStack.sendSuccess(() -> Component.literal("OK"), true);
         return collection.size();
      }
   }

   private static int bones(CommandSourceStack source, Collection<ServerPlayer> targets, String limbName, int value, boolean dislocation) {
      Frostbite.Limb limb = null;
      for (Frostbite.Limb candidate : Frostbite.Limb.VALUES) {
         if (candidate.key().equalsIgnoreCase(limbName)) {
            limb = candidate;
         }
      }
      if (limb == null) {
         source.sendFailure(Component.translatable("lastaid.command.unknown_limb", limbName));
         return 0;
      }
      for (ServerPlayer player : targets) {
         if (dislocation) {
            com.duckerquacker.lastaid.common.medical.bone.Bones.setDislocated(player, limb, value > 0);
         } else {
            com.duckerquacker.lastaid.common.medical.bone.Bones.setFracture(player, limb, value);
         }
      }
      source.sendSuccess(() -> Component.literal("OK"), true);
      return targets.size();
   }

   private static int setOverdose(CommandSourceStack source, Collection<ServerPlayer> targets, String drugName, float doses) {
      com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug drug;
      if (drugName.equalsIgnoreCase("morphine")) {
         drug = com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.MORPHINE;
      } else if (drugName.equalsIgnoreCase("adrenaline")) {
         drug = com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.ADRENALINE;
      } else {
         source.sendFailure(Component.literal("morphine | adrenaline"));
         return 0;
      }
      for (ServerPlayer player : targets) {
         com.duckerquacker.lastaid.common.medical.overdose.Overdose.setPool(player, drug, doses);
      }
      source.sendSuccess(() -> Component.literal("OK"), true);
      return targets.size();
   }

   private static int addProgress(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection, float f) {
      for (ServerPlayer serverPlayer : collection) {
         Concussion.State state = Concussion.INSTANCE.state(serverPlayer);
         if (f >= 0.0F) {
            Concussion.INSTANCE.treat(serverPlayer, state, f);
            state.treatedWithItems = false;
         } else {
            state.progress = Math.max(0.0F, state.progress + f);
            state.markDirty();
         }
      }

      commandSourceStack.sendSuccess(() -> Component.literal("OK"), true);
      return collection.size();
   }

   private static int clear(CommandSourceStack commandSourceStack, Collection<ServerPlayer> collection) {
      for (ServerPlayer serverPlayer : collection) {
         MedicalData medicalData = MedicalHooks.data(serverPlayer);

         for (MedicalCondition medicalCondition : MedicalConditions.all()) {
            clearCondition(serverPlayer, medicalCondition, medicalData);
         }
      }

      commandSourceStack.sendSuccess(() -> Component.translatable("lastaid.command.cleared", new Object[]{collection.size()}), true);
      return collection.size();
   }

   private static <S extends ConditionState> void clearCondition(ServerPlayer serverPlayer, MedicalCondition<S> medicalCondition, MedicalData medicalData) {
      medicalCondition.clear(serverPlayer, medicalData.getState(medicalCondition));
   }

   private static int info(CommandSourceStack commandSourceStack, ServerPlayer serverPlayer) {
      Concussion.State state = Concussion.INSTANCE.state(serverPlayer);
      PlayerMedicalContext playerMedicalContext = MedicalManager.context(serverPlayer);
      commandSourceStack.sendSuccess(
         () -> Component.literal(
            String.format(
               "%s: concussion severity=%d progress=%.1f/%.0f relapses=%d strain=%ds | activity=%s light=%d hostiles=%s noise=%s heat=%s snow=%s body=%.1fC rate=%+.2f/s",
               serverPlayer.getScoreboardName(),
               state.severity,
               state.progress,
               Concussion.threshold(Math.max(1, state.severity)),
               state.relapseCount,
               state.strainSecondsToday,
               playerMedicalContext.activity,
               playerMedicalContext.light,
               playerMedicalContext.hostiles,
               playerMedicalContext.noisy,
               playerMedicalContext.heat,
               playerMedicalContext.snowstorm,
               playerMedicalContext.bodyC,
               state.severity > 0 && playerMedicalContext.damageModel != null
                  ? Concussion.INSTANCE.recoveryPerSecond(serverPlayer, state, playerMedicalContext)
                  : 0.0
            )
         ),
         false
      );
      CommonCold.State state1 = CommonCold.INSTANCE.state(serverPlayer);
      StringBuilder stringBuilder = new StringBuilder();
      Frostbite.State state2 = Frostbite.INSTANCE.state(serverPlayer);

      for (Frostbite.Limb limb : Frostbite.Limb.VALUES) {
         Frostbite.LimbState limbState = state2.get(limb);
         stringBuilder.append(limb.key())
            .append('=')
            .append(limbState.amputated ? "amputated" : String.valueOf(limbState.stage))
            .append(String.format("(exp %.0f, heal %.0f) ", limbState.exposure, limbState.heal));
      }

      commandSourceStack.sendSuccess(
         () -> Component.literal(
            String.format(
               "hypothermia=%d | cold stage=%d exposure=%.0f heal=%.0f | frostbite: %s| insulation x%.2f wet=%s grace=%s feels=%.1fC",
               Hypothermia.stage(serverPlayer),
               state1.stage,
               state1.exposure,
               state1.healProgress,
               stringBuilder,
               playerMedicalContext.insulationFactor,
               playerMedicalContext.wet,
               playerMedicalContext.grace,
               playerMedicalContext.feelsLikeC
            )
         ),
         false
      );
      return 1;
   }
}
