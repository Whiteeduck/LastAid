package com.duckerquacker.lastaid.common.medical;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class MedicalConfig {
   public static final ModConfigSpec SERVER_SPEC;
   public static final MedicalConfig.Server SERVER;
   public static final ModConfigSpec CLIENT_SPEC;
   public static final MedicalConfig.Client CLIENT;

   private MedicalConfig() {
   }

   static {
      Pair pair = new Builder().configure(MedicalConfig.Server::new);
      SERVER = (MedicalConfig.Server)pair.getLeft();
      SERVER_SPEC = (ModConfigSpec)pair.getRight();
      Pair pair1 = new Builder().configure(MedicalConfig.Client::new);
      CLIENT = (MedicalConfig.Client)pair1.getLeft();
      CLIENT_SPEC = (ModConfigSpec)pair1.getRight();
   }

   public static final class Client {
      public final BooleanValue limbVisuals;
      public final BooleanValue mouthItem;
      public final BooleanValue headBandageFirstPerson;
      public final BooleanValue cameraSway;
      public final BooleanValue vignette;
      public final BooleanValue chatGarble;
      public final BooleanValue debugOverlayLine;
      public final DoubleValue panelScale;
      public final ConfigValue<List<? extends String>> conditionColors;

      Client(Builder builder) {
         builder.push("medical");
         this.cameraSway = builder.define("cameraSway", true);
         this.vignette = builder.define("vignette", true);
         this.chatGarble = builder.define("chatGarble", true);
         this.debugOverlayLine = builder.comment("Show concussion recovery in the F3 screen").define("debugOverlayLine", true);
         this.panelScale = builder.comment("Extra scale of the body panel (H)").defineInRange("panelScale", 1.0, 0.5, 2.0);
         this.limbVisuals = builder.comment("Hide amputated limbs and color frostbitten limbs on player models").define("limbVisuals", true);
         this.mouthItem = builder.comment("Visual only: a player whose both arms are useless holds the item in the teeth").define("mouthItem", true);
         this.headBandageFirstPerson = builder.comment("Head bandage: a thin gauze strip at the top of the screen in first person").define("headBandageFirstPerson", true);
         this.conditionColors = builder.comment("Indicator dot colors, format: condition_id=#RRGGBB")
            .defineListAllowEmpty(
               "conditionColors",
               List.of(
                  "concussion=#A020F0", "bleeding=#FF2020", "infection=#20FF20", "frostbite=#20A0FF", "exhaustion=#FFA020", "stress=#A0A0A0", "pain=#FF6060"
               ),
               () -> "concussion=#A020F0",
               object -> object instanceof String string && string.contains("=")
            );
         builder.pop();
      }
   }

   public static final class CommonCold {
      public final BooleanValue enabled;
      public final IntValue exposureToCatchSeconds;
      public final DoubleValue exposureWeightStage2;
      public final DoubleValue exposureWeightStage3;
      public final DoubleValue exposureWeightStage4;
      public final DoubleValue exposureDecayPerSecond;
      public final IntValue healSecondsStage1;
      public final IntValue healSecondsStage2;
      public final IntValue healSecondsStage3;
      public final DoubleValue teaHealMultiplier;
      public final DoubleValue antiviralHealMultiplier;
      public final DoubleValue reexposureStageUpChance;
      public final IntValue sneezeMinStage1;
      public final IntValue sneezeMaxStage1;
      public final IntValue sneezeMinStage2;
      public final IntValue sneezeMaxStage2;
      public final IntValue sneezeMinStage3;
      public final IntValue sneezeMaxStage3;
      public final IntValue mobHearingStage1;
      public final IntValue mobHearingStage2;
      public final IntValue mobHearingStage3;
      public final IntValue sneezeChatRadius;
      public final DoubleValue sneezeDropChanceStage3;
      public final IntValue antiviralDoseTicks;

      CommonCold(Builder builder) {
         builder.comment("Common cold. It only heals while the body is warm (>= normalFromC). Medicine only speeds it up.").push("cold_common");
         this.enabled = builder.define("enabled", true);
         this.exposureToCatchSeconds = builder.comment("Weighted seconds in hypothermia stage 2+ until you catch a cold")
            .defineInRange("exposureToCatchSeconds", 300, 1, 100000);
         this.exposureWeightStage2 = builder.defineInRange("exposureWeightStage2", 1.0, 0.0, 100.0);
         this.exposureWeightStage3 = builder.defineInRange("exposureWeightStage3", 2.0, 0.0, 100.0);
         this.exposureWeightStage4 = builder.defineInRange("exposureWeightStage4", 3.0, 0.0, 100.0);
         this.exposureDecayPerSecond = builder.comment("Risk going away per second while not cold").defineInRange("exposureDecayPerSecond", 0.5, 0.0, 100.0);
         this.healSecondsStage1 = builder.comment("Seconds of warmth to heal (1200 = one in-game day)").defineInRange("healSecondsStage1", 1200, 1, 1000000);
         this.healSecondsStage2 = builder.defineInRange("healSecondsStage2", 2400, 1, 1000000);
         this.healSecondsStage3 = builder.defineInRange("healSecondsStage3", 3600, 1, 1000000);
         this.teaHealMultiplier = builder.defineInRange("teaHealMultiplier", 1.5, 1.0, 100.0);
         this.antiviralHealMultiplier = builder.defineInRange("antiviralHealMultiplier", 2.0, 1.0, 100.0);
         this.reexposureStageUpChance = builder.comment("Getting cold again while sick: recovery resets and this is the chance to get worse")
            .defineInRange("reexposureStageUpChance", 0.3, 0.0, 1.0);
         this.sneezeMinStage1 = builder.defineInRange("sneezeMinSecondsStage1", 60, 1, 3600);
         this.sneezeMaxStage1 = builder.defineInRange("sneezeMaxSecondsStage1", 90, 1, 3600);
         this.sneezeMinStage2 = builder.defineInRange("sneezeMinSecondsStage2", 30, 1, 3600);
         this.sneezeMaxStage2 = builder.defineInRange("sneezeMaxSecondsStage2", 45, 1, 3600);
         this.sneezeMinStage3 = builder.defineInRange("sneezeMinSecondsStage3", 15, 1, 3600);
         this.sneezeMaxStage3 = builder.defineInRange("sneezeMaxSecondsStage3", 20, 1, 3600);
         this.mobHearingStage1 = builder.comment("Radius in which monsters come to check a sneeze (0 = none)").defineInRange("mobHearingStage1", 0, 0, 64);
         this.mobHearingStage2 = builder.defineInRange("mobHearingStage2", 12, 0, 64);
         this.mobHearingStage3 = builder.defineInRange("mobHearingStage3", 20, 0, 64);
         this.sneezeChatRadius = builder.comment("Players within this radius see '<name> Achoo!' in chat").defineInRange("sneezeChatRadius", 16, 0, 256);
         this.sneezeDropChanceStage3 = builder.defineInRange("sneezeDropChanceStage3", 0.05, 0.0, 1.0);
         this.antiviralDoseTicks = builder.comment("One antiviral dose lasts this long and the next dose is only possible afterwards (24000 = 1 day)")
            .defineInRange("antiviralDoseTicks", 24000, 20, 10000000);
         builder.pop();
      }
   }

   public static final class Concussion {
      public final BooleanValue enabled;
      public final DoubleValue chanceMultiplier;
      public final BooleanValue hardcoreMode;
      public final BooleanValue requireSecondPlayerForSeverity3;
      public final DoubleValue chanceUnderHalfHeart;
      public final DoubleValue chanceTier1;
      public final DoubleValue chanceTier2;
      public final DoubleValue chanceTier3;
      public final DoubleValue chanceTier4;
      public final DoubleValue tier2SeverityTwoChance;
      public final DoubleValue tier4SeverityThreeChance;
      public final DoubleValue explosionMultiplier;
      public final DoubleValue explosionFacingMultiplier;
      public final DoubleValue fallStartBlocks;
      public final DoubleValue fallChancePerBlock;
      public final DoubleValue fallMaxChance;
      public final DoubleValue fallCrouchMultiplier;
      public final IntValue fallSeverityTwoBlocks;
      public final IntValue fallSeverityThreeBlocks;
      public final DoubleValue snowNoHelmetBonus;
      public final DoubleValue heavyHitChance;
      public final DoubleValue heavyHitNoHelmetBonus;
      public final DoubleValue lightningChance;
      public final DoubleValue flyIntoWallChance;
      public final DoubleValue helmetLeatherGold;
      public final DoubleValue helmetChainTurtle;
      public final DoubleValue helmetIron;
      public final DoubleValue helmetDiamond;
      public final DoubleValue helmetNetherite;
      public final DoubleValue helmetOther;
      public final DoubleValue helmetLowDurabilityPenalty;
      public final DoubleValue recoverySpeedMultiplier;
      public final DoubleValue bedRestPerSecond;
      public final DoubleValue stillPerSecond;
      public final DoubleValue walkingPerSecond;
      public final DoubleValue strainingPerSecond;
      public final DoubleValue damagePenalty;
      public final DoubleValue fullNightSleepBonus;
      public final IntValue bedMaxLight;
      public final IntValue stillMaxLight;
      public final IntValue brightLightAbove;
      public final DoubleValue brightLightMultiplier;
      public final DoubleValue hypothermiaMultiplier;
      public final DoubleValue feverMultiplier;
      public final DoubleValue noiseMultiplier;
      public final DoubleValue hungerMultiplier;
      public final IntValue hungerBelow;
      public final DoubleValue heatSourceBonus;
      public final DoubleValue headBandageBonus;
      public final DoubleValue thresholdSeverity1;
      public final DoubleValue thresholdSeverity2;
      public final DoubleValue thresholdSeverity3;
      public final DoubleValue carryOverTo1;
      public final DoubleValue carryOverTo2;
      public final IntValue activityLimitSeconds;
      public final DoubleValue relapseChancePerCount;
      public final DoubleValue dropItemChancePerSecond;
      public final DoubleValue faintChance;
      public final DoubleValue tinnitusChancePerSecond;
      public final DoubleValue sprintBlockChanceSeverity1;
      public final DoubleValue jumpFailChanceSeverity2;
      public final DoubleValue chatGarbleChance;

      Concussion(Builder builder) {
         builder.comment("Concussion. Damage values are scaled to a 4 HP head, so they work with any head max health.").push("concussion");
         this.enabled = builder.define("enabled", true);
         this.chanceMultiplier = builder.comment("Global multiplier for every concussion chance").defineInRange("chanceMultiplier", 1.0, 0.0, 10.0);
         this.hardcoreMode = builder.comment("If true, severity III never heals by itself (only /medical concussion ... set)").define("hardcoreMode", false);
         this.requireSecondPlayerForSeverity3 = builder.comment("Severity III: sleeping and self treatment need another player nearby")
            .define("requireSecondPlayerForSeverity3", true);
         builder.push("chances");
         this.chanceUnderHalfHeart = builder.comment("Head damage < 0.5").defineInRange("underHalfHeart", 0.0, 0.0, 1.0);
         this.chanceTier1 = builder.comment("Head damage 0.5 - 1.0 -> severity I").defineInRange("tier1", 0.25, 0.0, 1.0);
         this.chanceTier2 = builder.comment("Head damage 1.0 - 2.0 -> severity I or II").defineInRange("tier2", 0.45, 0.0, 1.0);
         this.chanceTier3 = builder.comment("Head damage 2.0 - 3.0 -> severity II").defineInRange("tier3", 0.7, 0.0, 1.0);
         this.chanceTier4 = builder.comment("Head damage 3.0 - 4.0 -> severity II or III").defineInRange("tier4", 0.9, 0.0, 1.0);
         this.tier2SeverityTwoChance = builder.defineInRange("tier2SeverityTwoChance", 0.5, 0.0, 1.0);
         this.tier4SeverityThreeChance = builder.defineInRange("tier4SeverityThreeChance", 0.3, 0.0, 1.0);
         this.explosionMultiplier = builder.defineInRange("explosionMultiplier", 1.5, 0.0, 10.0);
         this.explosionFacingMultiplier = builder.comment("Extra multiplier if the player looked at the explosion (+-45 degrees)")
            .defineInRange("explosionFacingMultiplier", 1.5, 0.0, 10.0);
         this.fallStartBlocks = builder.defineInRange("fallStartBlocks", 4.0, 0.0, 256.0);
         this.fallChancePerBlock = builder.defineInRange("fallChancePerBlock", 0.06, 0.0, 1.0);
         this.fallMaxChance = builder.defineInRange("fallMaxChance", 0.9, 0.0, 1.0);
         this.fallCrouchMultiplier = builder.comment("Multiplier if the player was sneaking when landing").defineInRange("fallCrouchMultiplier", 0.5, 0.0, 1.0);
         this.fallSeverityTwoBlocks = builder.comment("If the head itself took no damage, severity comes from the fall height")
            .defineInRange("fallSeverityTwoBlocks", 12, 1, 512);
         this.fallSeverityThreeBlocks = builder.defineInRange("fallSeverityThreeBlocks", 20, 1, 512);
         this.snowNoHelmetBonus = builder.comment("Added to fall chance under open sky without a helmet (Primal Winter)")
            .defineInRange("snowNoHelmetBonus", 0.05, 0.0, 1.0);
         this.heavyHitChance = builder.comment("Mace, Iron Golem, Ravager").defineInRange("heavyHitChance", 0.25, 0.0, 1.0);
         this.heavyHitNoHelmetBonus = builder.defineInRange("heavyHitNoHelmetBonus", 0.1, 0.0, 1.0);
         this.lightningChance = builder.defineInRange("lightningChance", 0.4, 0.0, 1.0);
         this.flyIntoWallChance = builder.comment("Elytra crash").defineInRange("flyIntoWallChance", 0.2, 0.0, 1.0);
         builder.pop();
         builder.comment("Helmet multipliers for the concussion chance").push("helmets");
         this.helmetLeatherGold = builder.defineInRange("leatherGold", 0.85, 0.0, 1.0);
         this.helmetChainTurtle = builder.defineInRange("chainmailTurtle", 0.7, 0.0, 1.0);
         this.helmetIron = builder.defineInRange("iron", 0.6, 0.0, 1.0);
         this.helmetDiamond = builder.defineInRange("diamond", 0.5, 0.0, 1.0);
         this.helmetNetherite = builder.defineInRange("netherite", 0.35, 0.0, 1.0);
         this.helmetOther = builder.comment("Any other helmet (modded)").defineInRange("other", 0.7, 0.0, 1.0);
         this.helmetLowDurabilityPenalty = builder.comment("Added to the multiplier if the helmet has < 20% durability")
            .defineInRange("lowDurabilityPenalty", 0.2, 0.0, 1.0);
         builder.pop();
         builder.comment("Recovery progress per second. Golden apples / regeneration never add progress.").push("recovery");
         this.recoverySpeedMultiplier = builder.comment("Multiplies all positive recovery").defineInRange("recoverySpeedMultiplier", 1.0, 0.0, 100.0);
         this.bedRestPerSecond = builder.defineInRange("bedRestPerSecond", 2.0, 0.0, 100.0);
         this.stillPerSecond = builder.defineInRange("stillPerSecond", 0.5, 0.0, 100.0);
         this.walkingPerSecond = builder.defineInRange("walkingPerSecond", 0.1, 0.0, 100.0);
         this.strainingPerSecond = builder.comment("Sprinting / jumping / fighting (negative = progress lost)")
            .defineInRange("strainingPerSecond", -1.5, -100.0, 100.0);
         this.damagePenalty = builder.comment("Progress lost when taking any damage").defineInRange("damagePenalty", 10.0, 0.0, 1000.0);
         this.fullNightSleepBonus = builder.defineInRange("fullNightSleepBonus", 50.0, 0.0, 1000.0);
         this.bedMaxLight = builder.defineInRange("bedMaxLight", 4, 0, 15);
         this.stillMaxLight = builder.defineInRange("stillMaxLight", 7, 0, 15);
         this.brightLightAbove = builder.defineInRange("brightLightAbove", 8, 0, 15);
         this.brightLightMultiplier = builder.defineInRange("brightLightMultiplier", 0.5, 0.0, 10.0);
         this.hypothermiaMultiplier = builder.defineInRange("hypothermiaMultiplier", 0.3, 0.0, 10.0);
         this.feverMultiplier = builder.defineInRange("feverMultiplier", 0.4, 0.0, 10.0);
         this.noiseMultiplier = builder.comment("Thunder, explosions, note blocks, jukeboxes within 24 blocks")
            .defineInRange("noiseMultiplier", 0.5, 0.0, 10.0);
         this.hungerMultiplier = builder.defineInRange("hungerMultiplier", 0.7, 0.0, 10.0);
         this.hungerBelow = builder.defineInRange("hungerBelow", 6, 0, 20);
         this.heatSourceBonus = builder.comment("Added per second near campfire/fire/lava/lit furnace (4 blocks)")
            .defineInRange("heatSourceBonus", 0.5, 0.0, 100.0);
         this.headBandageBonus = builder.comment("Added per second while wearing the head bandage, even while walking")
            .defineInRange("headBandageBonus", 0.3, 0.0, 100.0);
         this.thresholdSeverity1 = builder.defineInRange("thresholdSeverity1", 30.0, 1.0, 100000.0);
         this.thresholdSeverity2 = builder.defineInRange("thresholdSeverity2", 80.0, 1.0, 100000.0);
         this.thresholdSeverity3 = builder.defineInRange("thresholdSeverity3", 150.0, 1.0, 100000.0);
         this.carryOverTo1 = builder.comment("Progress after severity II improves to I").defineInRange("carryOverTo1", 0.0, 0.0, 100000.0);
         this.carryOverTo2 = builder.comment("Progress after severity III improves to II").defineInRange("carryOverTo2", 40.0, 0.0, 100000.0);
         this.activityLimitSeconds = builder.comment("Seconds of sprinting/fighting per in-game day before a relapse")
            .defineInRange("activityLimitSeconds", 300, 1, 24000);
         this.relapseChancePerCount = builder.comment("Chance per relapse count that severity increases on relapse")
            .defineInRange("relapseChancePerCount", 0.15, 0.0, 1.0);
         builder.pop();
         builder.push("symptoms");
         this.dropItemChancePerSecond = builder.comment("Severity II+: chance per second while moving to drop the held item")
            .defineInRange("dropItemChancePerSecond", 0.01, 0.0, 1.0);
         this.faintChance = builder.comment("Severity III: chance every 2 minutes to briefly black out").defineInRange("faintChance", 0.05, 0.0, 1.0);
         this.tinnitusChancePerSecond = builder.defineInRange("tinnitusChancePerSecond", 0.03, 0.0, 1.0);
         this.sprintBlockChanceSeverity1 = builder.defineInRange("sprintBlockChanceSeverity1", 0.5, 0.0, 1.0);
         this.jumpFailChanceSeverity2 = builder.defineInRange("jumpFailChanceSeverity2", 0.4, 0.0, 1.0);
         this.chatGarbleChance = builder.comment("Severity III: chance that a chat message is shown garbled").defineInRange("chatGarbleChance", 0.3, 0.0, 1.0);
         builder.pop();
         builder.pop();
      }
   }

   public static final class Frostbite {
      public final BooleanValue enabled;
      public final BooleanValue permanentDeath;
      public final DoubleValue exposureStage1;
      public final DoubleValue exposureStage2;
      public final DoubleValue exposureStage3;
      public final DoubleValue exposureStage4;
      public final DoubleValue weightHypothermia2;
      public final DoubleValue weightHypothermia3;
      public final DoubleValue weightHypothermia4;
      public final DoubleValue leatherProtection;
      public final DoubleValue furProtection;
      public final DoubleValue otherArmorProtection;
      public final ConfigValue<List<? extends String>> furItems;
      public final IntValue healSecondsStage1;
      public final IntValue healSecondsStage2;
      public final IntValue healSecondsStage3;
      public final IntValue healSecondsStage4;
      public final DoubleValue limbDamageStage2;
      public final DoubleValue limbDamageStage3;
      public final DoubleValue attackPenaltyStage1;
      public final DoubleValue attackPenaltyStage2;
      public final DoubleValue attackPenaltyStage3;
      public final DoubleValue miningPenaltyStage2;
      public final DoubleValue miningPenaltyStage3;
      public final DoubleValue speedPenaltyStage1;
      public final DoubleValue speedPenaltyStage2;
      public final DoubleValue speedPenaltyStage3;
      public final DoubleValue jumpPenaltyStage2;
      public final DoubleValue jumpPenaltyStage3;
      public final DoubleValue bowInaccuracyStage2;
      public final DoubleValue bowInaccuracyStage3;
      public final DoubleValue deadLegSpeedPenalty;
      public final DoubleValue crawlSpeedPenalty;
      public final DoubleValue damageMultiplier;
      public final IntValue damageMultiplierFromStage;
      public final IntValue infectionCheckSeconds;
      public final DoubleValue infectionChance;
      public final IntValue amputationSeconds;
      public final IntValue amputationBandages;

      Frostbite(Builder builder) {
         builder.comment("Frostbite of arms and legs (each limb separately)").push("frostbite");
         this.enabled = builder.define("enabled", true);
         this.permanentDeath = builder.comment(
               new String[]{
                  "true: gangrene (stage 4) is permanent and only amputation stops it.",
                  "false: gangrene heals like stage 3 (warmth + medical kit, longer) and amputation is disabled."
               }
            )
            .define("permanentDeath", true);
         this.exposureStage1 = builder.comment("Weighted cold seconds per limb to reach each stage").defineInRange("exposureStage1", 120.0, 1.0, 1000000.0);
         this.exposureStage2 = builder.defineInRange("exposureStage2", 300.0, 1.0, 1000000.0);
         this.exposureStage3 = builder.defineInRange("exposureStage3", 600.0, 1.0, 1000000.0);
         this.exposureStage4 = builder.defineInRange("exposureStage4", 1000.0, 1.0, 1000000.0);
         this.weightHypothermia2 = builder.comment("Exposure gained per second in each hypothermia stage").defineInRange("weightHypothermia2", 0.5, 0.0, 100.0);
         this.weightHypothermia3 = builder.defineInRange("weightHypothermia3", 1.5, 0.0, 100.0);
         this.weightHypothermia4 = builder.defineInRange("weightHypothermia4", 6.0, 0.0, 100.0);
         this.leatherProtection = builder.comment("Exposure is divided by this for limbs covered with leather (arms: chestplate, legs: leggings/boots)")
            .defineInRange("leatherProtection", 2.0, 1.0, 100.0);
         this.furProtection = builder.defineInRange("furProtection", 3.0, 1.0, 100.0);
         this.otherArmorProtection = builder.comment("Metal etc. armor").defineInRange("otherArmorProtection", 1.25, 1.0, 100.0);
         this.furItems = builder.comment("Items counting as fur clothing (supports * at the end)")
            .defineListAllowEmpty("furItems", List.of("cold_sweat:goat_fur_*", "cold_sweat:hoglin_*"), () -> "modid:item", object -> object instanceof String);
         this.healSecondsStage1 = builder.comment(
               new String[]{
                  "Seconds of warmth to go down one stage. Stage 2 also needs a bandage/plaster on the limb,",
                  "stage 3 (and 4 if permanentDeath=false) needs a medical kit"
               }
            )
            .defineInRange("healSecondsStage1", 900, 1, 1000000);
         this.healSecondsStage2 = builder.defineInRange("healSecondsStage2", 1800, 1, 1000000);
         this.healSecondsStage3 = builder.defineInRange("healSecondsStage3", 2400, 1, 1000000);
         this.healSecondsStage4 = builder.defineInRange("healSecondsStage4", 3600, 1, 1000000);
         this.limbDamageStage2 = builder.comment("Limb damage every 10 seconds").defineInRange("limbDamageStage2", 0.2, 0.0, 100.0);
         this.limbDamageStage3 = builder.defineInRange("limbDamageStage3", 0.5, 0.0, 100.0);
         this.attackPenaltyStage1 = builder.comment("Attack damage penalty of the weapon arm").defineInRange("attackPenaltyStage1", 0.1, 0.0, 1.0);
         this.attackPenaltyStage2 = builder.defineInRange("attackPenaltyStage2", 0.25, 0.0, 1.0);
         this.attackPenaltyStage3 = builder.defineInRange("attackPenaltyStage3", 0.5, 0.0, 1.0);
         this.miningPenaltyStage2 = builder.defineInRange("miningPenaltyStage2", 0.2, 0.0, 1.0);
         this.miningPenaltyStage3 = builder.defineInRange("miningPenaltyStage3", 0.5, 0.0, 1.0);
         this.speedPenaltyStage1 = builder.comment("Movement penalty of the worst leg").defineInRange("speedPenaltyStage1", 0.05, 0.0, 1.0);
         this.speedPenaltyStage2 = builder.defineInRange("speedPenaltyStage2", 0.1, 0.0, 1.0);
         this.speedPenaltyStage3 = builder.defineInRange("speedPenaltyStage3", 0.2, 0.0, 1.0);
         this.jumpPenaltyStage2 = builder.defineInRange("jumpPenaltyStage2", 0.2, 0.0, 1.0);
         this.jumpPenaltyStage3 = builder.defineInRange("jumpPenaltyStage3", 0.4, 0.0, 1.0);
         this.bowInaccuracyStage2 = builder.comment("Extra arrow inaccuracy (vanilla bow = 1.0)").defineInRange("bowInaccuracyStage2", 0.3, 0.0, 20.0);
         this.bowInaccuracyStage3 = builder.defineInRange("bowInaccuracyStage3", 1.0, 0.0, 20.0);
         this.deadLegSpeedPenalty = builder.comment("One dead/amputated leg: movement penalty, no sprinting, no jumping")
            .defineInRange("deadLegSpeedPenalty", 0.6, 0.0, 1.0);
         this.crawlSpeedPenalty = builder.comment("Both legs dead: crawling pose (vanilla crawl is already x0.3), 0.62 ~ 0.5 blocks/s")
            .defineInRange("crawlSpeedPenalty", 0.62, 0.0, 1.0);
         this.damageMultiplier = builder.comment("Damage multiplier for frostbitten / dead / amputated limbs")
            .defineInRange("damageMultiplier", 2.0, 1.0, 100.0);
         this.damageMultiplierFromStage = builder.defineInRange("damageMultiplierFromStage", 3, 1, 4);
         this.infectionCheckSeconds = builder.comment("Untreated gangrene: infection roll interval (real seconds). Infection itself is not implemented yet.")
            .defineInRange("infectionCheckSeconds", 300, 1, 100000);
         this.infectionChance = builder.defineInRange("infectionChance", 0.15, 0.0, 1.0);
         this.amputationSeconds = builder.defineInRange("amputationSeconds", 15, 1, 600);
         this.amputationBandages = builder.defineInRange("amputationBandages", 2, 0, 64);
         builder.pop();
      }
   }

   public static final class Hypothermia {
      public final BooleanValue enabled;
      public final DoubleValue stage1BelowC;
      public final DoubleValue stage2BelowC;
      public final DoubleValue stage3BelowC;
      public final DoubleValue stage4BelowC;
      public final DoubleValue hysteresisC;
      public final IntValue stageUpDelaySeconds;
      public final DoubleValue stage1SlownessChance;
      public final DoubleValue stage2SlownessChance;
      public final DoubleValue stage3MiningPenalty;
      public final DoubleValue stage4MiningPenalty;
      public final DoubleValue stage4FaintChancePerMinute;

      Hypothermia(Builder builder) {
         builder.comment("Hypothermia stages (body temperature in Celsius, see [temperature])").push("cold");
         this.enabled = builder.define("enabled", true);
         this.stage1BelowC = builder.comment("Stage 1 'chilly'").defineInRange("stage1BelowC", 36.5, 20.0, 45.0);
         this.stage2BelowC = builder.comment("Stage 2 'cold': common cold risk starts").defineInRange("stage2BelowC", 35.0, 20.0, 45.0);
         this.stage3BelowC = builder.comment("Stage 3 'very cold': frostbite risk").defineInRange("stage3BelowC", 34.0, 20.0, 45.0);
         this.stage4BelowC = builder.comment("Stage 4 'hypothermia'").defineInRange("stage4BelowC", 33.0, 20.0, 45.0);
         this.hysteresisC = builder.comment("The body has to be this much warmer than a border to go back a stage")
            .defineInRange("hysteresisC", 0.15, 0.0, 5.0);
         this.stageUpDelaySeconds = builder.comment("Seconds the body must stay in a colder band before the stage rises")
            .defineInRange("stageUpDelaySeconds", 10, 0, 3600);
         this.stage1SlownessChance = builder.comment("Chance per second for 3 s of Slowness I").defineInRange("stage1SlownessChance", 0.02, 0.0, 1.0);
         this.stage2SlownessChance = builder.defineInRange("stage2SlownessChance", 0.08, 0.0, 1.0);
         this.stage3MiningPenalty = builder.comment("Mining speed penalty (0.1 = -10%)").defineInRange("stage3MiningPenalty", 0.1, 0.0, 1.0);
         this.stage4MiningPenalty = builder.defineInRange("stage4MiningPenalty", 0.25, 0.0, 1.0);
         this.stage4FaintChancePerMinute = builder.defineInRange("stage4FaintChancePerMinute", 0.05, 0.0, 1.0);
         builder.pop();
      }
   }

   public static final class Items {
      public final DoubleValue icePackProgress;
      public final IntValue icePackCooldownSeconds;
      public final DoubleValue icePackColdUnits;
      public final DoubleValue teaProgress;
      public final DoubleValue teaHeadHeal;
      public final IntValue calmSeconds;
      public final DoubleValue teaWarmthC;
      public final IntValue warmProtectionSeconds;
      public final DoubleValue brothHeadHealTotal;
      public final IntValue brothSeconds;
      public final DoubleValue medkitProgress;
      public final IntValue medkitSeconds;
      public final IntValue medkitNauseaImmunitySeconds;
      public final IntValue neckBraceMinutes;
      public final DoubleValue barkFromStrippingChance;

      Items(Builder builder) {
         builder.push("items");
         this.icePackProgress = builder.defineInRange("icePackProgress", 8.0, 0.0, 1000.0);
         this.icePackCooldownSeconds = builder.defineInRange("icePackCooldownSeconds", 300, 0, 72000);
         this.icePackColdUnits = builder.comment("ColdSweat units removed from core temperature if the target has no helmet")
            .defineInRange("icePackColdUnits", 10.0, 0.0, 150.0);
         this.teaProgress = builder.defineInRange("herbalTeaProgress", 10.0, 0.0, 1000.0);
         this.teaHeadHeal = builder.comment("HP restored on the head (head usually has 4 HP)").defineInRange("herbalTeaHeadHeal", 1.0, 0.0, 100.0);
         this.calmSeconds = builder.defineInRange("calmSeconds", 180, 1, 72000);
         this.teaWarmthC = builder.comment("Instant body warming from herbal tea in Celsius").defineInRange("herbalTeaWarmthC", 1.5, 0.0, 10.0);
         this.warmProtectionSeconds = builder.comment("Herbal tea: cooling is slowed down for this long (see temperature.warmProtectionCoolingMultiplier)")
            .defineInRange("warmProtectionSeconds", 180, 0, 72000);
         this.brothHeadHealTotal = builder.defineInRange("barkBrothHeadHealTotal", 1.5, 0.0, 100.0);
         this.brothSeconds = builder.defineInRange("barkBrothSeconds", 60, 1, 72000);
         this.medkitProgress = builder.defineInRange("medicalKitProgress", 20.0, 0.0, 1000.0);
         this.medkitSeconds = builder.defineInRange("medicalKitSeconds", 8, 1, 600);
         this.medkitNauseaImmunitySeconds = builder.defineInRange("medicalKitNauseaImmunitySeconds", 300, 0, 72000);
         this.neckBraceMinutes = builder.defineInRange("neckBraceMinutes", 10, 1, 1440);
         this.barkFromStrippingChance = builder.comment("Chance to get bark when stripping a log with an axe")
            .defineInRange("barkFromStrippingChance", 0.5, 0.0, 1.0);
         builder.pop();
      }
   }

   public static final class Server {
      public final BooleanValue enabled;
      public final BooleanValue coldSweatIntegration;
      public final BooleanValue primalWinterIntegration;
      public final BooleanValue allowMagicHealing;
      public final DoubleValue celsiusPerColdSweatUnit;
      public final DoubleValue hypothermiaBelowC;
      public final DoubleValue normalFromC;
      public final DoubleValue normalToC;
      public final DoubleValue feverAboveC;
      public final BooleanValue freezeDuringGrace;
      public final DoubleValue warmProtectionCoolingMultiplier;
      public final DoubleValue hungryCoolingMultiplier;
      public final DoubleValue tiredCoolingMultiplier;
      public final IntValue hungryBelowFood;
      public final IntValue tiredAfterTicksAwake;
      public final IntValue commentCooldownSeconds;
      public final MedicalConfig.Concussion concussion;
      public final MedicalConfig.Items items;
      public final MedicalConfig.Hypothermia cold;
      public final MedicalConfig.CommonCold commonCold;
      public final MedicalConfig.Frostbite frostbite;
      public final MedicalConfig.Pain pain;
      public final MedicalConfig.Amputation amputation;
      public final MedicalConfig.ColdDeath coldDeath;
      public final MedicalConfig.Sleep sleep;
      public final MedicalConfig.Overdose overdose;
      public final MedicalConfig.Fracture fracture;
      public final MedicalConfig.Splint splint;
      public final MedicalConfig.BandageHead bandageHead;
      public final MedicalConfig.Carry carry;
      public final MedicalConfig.Bleeding bleeding;
      public final MedicalConfig.Wounds wounds;
      public final MedicalConfig.Infection infection;
      public final MedicalConfig.Anemia anemia;
      public final MedicalConfig.General general;
      public final MedicalConfig.Commands commands;

      Server(Builder builder) {
         builder.comment("Medical conditions (concussion, ...) built on top of First Aid").push("general");
         this.enabled = builder.comment("Master switch for all medical conditions").define("enabled", true);
         this.coldSweatIntegration = builder.comment("Use ColdSweat body temperature for recovery and items (ignored if ColdSweat is missing)")
            .define("coldSweatIntegration", true);
         this.primalWinterIntegration = builder.comment("Treat open sky as a snowstorm (Primal Winter: it always snows)")
            .define("primalWinterIntegration", true);
         this.allowMagicHealing = builder.comment("If false, the Potion of Clarity only removes symptoms and never adds recovery")
            .define("allowMagicHealing", true);
         builder.pop();
         builder.comment(
               new String[]{
                  "ColdSweat stores body temperature on an abstract scale (0 = normal, +-100 = damage starts, +-150 = max).",
                  "It is converted to Celsius as 36.6 + value * celsiusPerColdSweatUnit"
               }
            )
            .push("temperature");
         this.celsiusPerColdSweatUnit = builder.comment("Default 0.036: ColdSweat starts freezing damage at -100 = 33.0 C = hypothermia stage 4")
            .defineInRange("celsiusPerColdSweatUnit", 0.036, 0.001, 1.0);
         this.hypothermiaBelowC = builder.defineInRange("hypothermiaBelowC", 35.0, 20.0, 40.0);
         this.normalFromC = builder.defineInRange("normalFromC", 36.5, 20.0, 45.0);
         this.normalToC = builder.defineInRange("normalToC", 37.5, 20.0, 45.0);
         this.feverAboveC = builder.defineInRange("feverAboveC", 38.5, 30.0, 45.0);
         this.freezeDuringGrace = builder.comment(
               "While the ColdSweat 'grace' effect is active the body temperature is frozen and temperature penalties are ignored"
            )
            .define("freezeDuringGrace", true);
         this.warmProtectionCoolingMultiplier = builder.comment(
               "Herbal tea 'warm protection': cooling speed multiplier (clothing is handled by ColdSweat itself)"
            )
            .defineInRange("warmProtectionCoolingMultiplier", 0.5, 0.0, 10.0);
         this.hungryCoolingMultiplier = builder.defineInRange("hungryCoolingMultiplier", 1.2, 0.0, 10.0);
         this.hungryBelowFood = builder.defineInRange("hungryBelowFood", 6, 0, 20);
         this.tiredCoolingMultiplier = builder.defineInRange("tiredCoolingMultiplier", 1.2, 0.0, 10.0);
         this.tiredAfterTicksAwake = builder.comment("Ticks since last sleep after which the player counts as tired (24000 = 1 day)")
            .defineInRange("tiredAfterTicksAwake", 24000, 0, Integer.MAX_VALUE);
         this.commentCooldownSeconds = builder.comment("Character comments (italic chat lines): minimum seconds between two comments of the same category")
            .defineInRange("commentCooldownSeconds", 60, 0, 3600);
         builder.pop();
         this.concussion = new MedicalConfig.Concussion(builder);
         this.items = new MedicalConfig.Items(builder);
         this.cold = new MedicalConfig.Hypothermia(builder);
         this.commonCold = new MedicalConfig.CommonCold(builder);
         this.frostbite = new MedicalConfig.Frostbite(builder);
         this.pain = new MedicalConfig.Pain(builder);
         this.amputation = new MedicalConfig.Amputation(builder);
         this.coldDeath = new MedicalConfig.ColdDeath(builder);
         this.sleep = new MedicalConfig.Sleep(builder);
         this.overdose = new MedicalConfig.Overdose(builder);
         this.fracture = new MedicalConfig.Fracture(builder);
         this.splint = new MedicalConfig.Splint(builder);
         this.bandageHead = new MedicalConfig.BandageHead(builder);
         this.carry = new MedicalConfig.Carry(builder);
         this.bleeding = new MedicalConfig.Bleeding(builder);
         this.wounds = new MedicalConfig.Wounds(builder);
         this.infection = new MedicalConfig.Infection(builder);
         this.anemia = new MedicalConfig.Anemia(builder);
         this.general = new MedicalConfig.General(builder);
         this.commands = new MedicalConfig.Commands(builder);
      }
   }

   /** Pain levels 0-5 (0 none, 1 minor, 2 pain, 3 severe, 4 excruciating, 5 agony). */
   public static final class Pain {
      public final DoubleValue painkillerReduction;
      public final IntValue amputationPeakPainDuration;
      public final IntValue amputationPainDecayDuration;
      public final IntValue deadLimbPainLevel;
      public final IntValue gangrenePainLevel;
      public final IntValue frostbiteStage3PainLevel;

      Pain(Builder builder) {
         builder.comment("Pain from medical conditions. Pain levels: 0 none, 1 minor, 2 pain, 3 severe, 4 excruciating, 5 agony").push("pain");
         this.painkillerReduction = builder.comment("While painkillers work the pain level is multiplied by (1 - this). 0.6 = -60%")
            .defineInRange("painkillerReduction", 0.6, 0.0, 1.0);
         this.amputationPeakPainDuration = builder.comment("Seconds of agony (5/5) right after an amputation")
            .defineInRange("amputationPeakPainDuration", 30, 0, 3600);
         this.amputationPainDecayDuration = builder.comment("Seconds in which the amputation pain then fades linearly to 0")
            .defineInRange("amputationPainDecayDuration", 60, 0, 3600);
         this.deadLimbPainLevel = builder.comment("Pain of an already amputated (healed) limb. 0 = the stump does not hurt")
            .defineInRange("deadLimbPainLevel", 0, 0, 5);
         this.gangrenePainLevel = builder.comment("Constant pain of a gangrenous limb (frostbite stage 4, not amputated)")
            .defineInRange("gangrenePainLevel", 4, 0, 5);
         this.frostbiteStage3PainLevel = builder.comment("Pain of a limb with frostbite stage 3 (+1 if two or more limbs are at stage 3)")
            .defineInRange("frostbiteStage3PainLevel", 2, 0, 5);
         builder.pop();
      }
   }

   public static final class Amputation {
      public final DoubleValue maxHpLossPerLimb;
      public final DoubleValue minMaxHp;
      public final BooleanValue painkillerBlocksAmputationFeedback;
      public final BooleanValue scaryText;

      Amputation(Builder builder) {
         builder.comment("Permanent consequences of an amputation").push("amputation");
         this.maxHpLossPerLimb = builder.comment("Max health removed per amputated limb (2 = one heart)").defineInRange("maxHpLossPerLimb", 2.0, 0.0, 20.0);
         this.minMaxHp = builder.comment("Max health never goes below this").defineInRange("minMaxHp", 2.0, 1.0, 1024.0);
         this.painkillerBlocksAmputationFeedback = builder.comment("An active painkiller or morphine removes the camera shake and the red text of an amputation (only a short 'you feel nothing' message, quieter sounds). A painkiller that has not kicked in yet halves them")
            .define("painkillerBlocksAmputationFeedback", true);
         this.scaryText = builder.comment("Show the patient red text fragments on screen while a limb is being amputated").define("scaryText", true);
         builder.pop();
      }
   }

   public static final class ColdDeath {
      public final BooleanValue blockDirectColdDamage;
      public final BooleanValue allowColdDeath;
      public final BooleanValue coldDeathRequiresMaxCold;
      public final DoubleValue coldDeathDelayMinutes;
      public final IntValue minHypothermiaStage;
      public final IntValue damageIntervalSeconds;
      public final DoubleValue damage;

      ColdDeath(Builder builder) {
         builder.comment(
               new String[]{
                  "Cold never kills directly (ColdSweat freezing damage and vanilla powder snow freezing are blocked).",
                  "The only way to die from cold: common cold stage 3 AND hypothermia >= minHypothermiaStage for coldDeathDelayMinutes,",
                  "then 'damage' HP every damageIntervalSeconds until warmed up."
               }
            )
            .push("cold_death");
         this.blockDirectColdDamage = builder.comment("Block ColdSweat cold damage and vanilla freeze damage").define("blockDirectColdDamage", true);
         this.allowColdDeath = builder.define("allowColdDeath", true);
         this.coldDeathRequiresMaxCold = builder.comment("If false, hypothermia alone (without a stage 3 common cold) is enough")
            .define("coldDeathRequiresMaxCold", true);
         this.coldDeathDelayMinutes = builder.comment("Real-time minutes (1 minute = 1200 ticks)").defineInRange("coldDeathDelayMinutes", 3.0, 0.0, 1440.0);
         this.minHypothermiaStage = builder.defineInRange("minHypothermiaStage", 3, 1, 4);
         this.damageIntervalSeconds = builder.defineInRange("damageIntervalSeconds", 10, 1, 3600);
         this.damage = builder.defineInRange("damage", 1.0, 0.0, 100.0);
         builder.pop();
      }
   }

   public static final class Sleep {
      public final DoubleValue coldRecoveryPerSleepTick;
      public final DoubleValue sleepFullNightBonus;
      public final DoubleValue coldBodyMultiplier;
      public final DoubleValue coldBodyBelowC;
      public final IntValue healCooldownMinutes;

      Sleep(Builder builder) {
         builder.comment("Sleeping cures the common cold").push("sleep");
         this.coldRecoveryPerSleepTick = builder.comment("Common cold recovery per second of sleep, relative to one warm second")
            .defineInRange("coldRecoveryPerSleepTick", 1.0, 0.0, 100.0);
         this.sleepFullNightBonus = builder.comment("A full night of sleep adds this part of the needed recovery (0.3 = +30%)")
            .defineInRange("sleepFullNightBonus", 0.3, 0.0, 1.0);
         this.coldBodyMultiplier = builder.comment("Sleeping with a cold body (below coldBodyBelowC) heals this much slower")
            .defineInRange("coldBodyMultiplier", 0.5, 0.0, 1.0);
         this.coldBodyBelowC = builder.defineInRange("coldBodyBelowC", 36.0, 20.0, 45.0);
         this.healCooldownMinutes = builder.comment("After a sleep that treated the cold, sleeping does not treat it again for this many real minutes (20 = one game day). The bed itself stays usable. 0 = off")
            .defineInRange("healCooldownMinutes", 20, 0, 1440);
         builder.pop();
      }
   }

   public static final class Overdose {
      public final BooleanValue enabled;
      public final IntValue overdoseThreshold;
      public final IntValue maxDose;
      public final DoubleValue overdoseDecayHours;
      public final IntValue morphinePillDoses;
      public final IntValue morphineInjectorDoses;
      public final IntValue adrenalineDoses;
      public final DoubleValue morphineStage2Damage;
      public final IntValue morphineStage2Interval;
      public final DoubleValue morphineStage3Damage;
      public final IntValue morphineStage3Interval;
      public final IntValue morphineDeathSeconds;
      public final DoubleValue adrenalineStage2Damage;
      public final IntValue adrenalineStage2Interval;
      public final DoubleValue adrenalineArrestDamage;
      public final DoubleValue adrenalineStage3Damage;
      public final IntValue adrenalineStage3Interval;
      public final DoubleValue mixedSpeedPenalty;

      Overdose(Builder builder) {
         builder.comment(
               "Morphine and adrenaline build up in the body (separate pools, 1 intake = doses below).",
               "The pool slowly breaks down. From overdoseThreshold doses on: stage I, +1 dose: stage II, maxDose: stage III.",
               "Naloxone clears morphine, the beta blocker clears adrenaline."
            )
            .push("overdose");
         this.enabled = builder.define("enabled", true);
         this.overdoseThreshold = builder.comment("Doses for overdose stage I").defineInRange("overdoseThreshold", 4, 1, 50);
         this.maxDose = builder.comment("The pool never grows above this (= stage III)").defineInRange("maxDose", 6, 2, 50);
         this.overdoseDecayHours = builder.comment("Game hours (1000 ticks each) to break down one dose").defineInRange("overdoseDecayHours", 6.0, 0.05, 240.0);
         this.morphinePillDoses = builder.defineInRange("morphinePillDoses", 1, 0, 10);
         this.morphineInjectorDoses = builder.defineInRange("morphineInjectorDoses", 2, 0, 10);
         this.adrenalineDoses = builder.comment("Per adrenaline injection").defineInRange("adrenalineDoses", 1, 0, 10);
         this.morphineStage2Damage = builder.defineInRange("morphineStage2Damage", 0.5, 0.0, 100.0);
         this.morphineStage2Interval = builder.comment("Seconds").defineInRange("morphineStage2Interval", 5, 1, 600);
         this.morphineStage3Damage = builder.defineInRange("morphineStage3Damage", 1.0, 0.0, 100.0);
         this.morphineStage3Interval = builder.defineInRange("morphineStage3Interval", 3, 1, 600);
         this.morphineDeathSeconds = builder.comment("Stage III morphine kills after this many seconds without naloxone (0 = only the damage above)")
            .defineInRange("morphineDeathSeconds", 60, 0, 3600);
         this.adrenalineStage2Damage = builder.defineInRange("adrenalineStage2Damage", 0.3, 0.0, 100.0);
         this.adrenalineStage2Interval = builder.defineInRange("adrenalineStage2Interval", 5, 1, 600);
         this.adrenalineArrestDamage = builder.comment("Cardiac arrest: one-time damage when adrenaline reaches stage III").defineInRange("adrenalineArrestDamage", 6.0, 0.0, 100.0);
         this.adrenalineStage3Damage = builder.defineInRange("adrenalineStage3Damage", 1.0, 0.0, 100.0);
         this.adrenalineStage3Interval = builder.defineInRange("adrenalineStage3Interval", 2, 1, 600);
         this.mixedSpeedPenalty = builder.comment("Both drugs in the body at once: movement and attack speed penalty").defineInRange("mixedSpeedPenalty", 0.2, 0.0, 0.95);
         builder.pop();
      }
   }

   public static final class Fracture {
      public final BooleanValue enabled;
      public final DoubleValue fallHairlineMinBlocks;
      public final DoubleValue fallClosedMinBlocks;
      public final DoubleValue fallSevereMinBlocks;
      public final DoubleValue fallHairlineChance;
      public final DoubleValue fallClosedChance;
      public final DoubleValue fallSevereLegChance;
      public final DoubleValue fallSevereArmChance;
      public final DoubleValue fallSevereOpenChance;
      public final DoubleValue landingDislocationChance;
      public final DoubleValue explosionChance;
      public final DoubleValue explosionMinDamage;
      public final DoubleValue maceChance;
      public final DoubleValue maceOpenChance;
      public final DoubleValue heavyMobChance;
      public final DoubleValue axeCritChance;
      public final DoubleValue elytraWallChance;
      public final DoubleValue elytraWallOpenChance;
      public final DoubleValue frostbittenBreakMultiplier;
      public final DoubleValue fracturedFrostbiteTimeMultiplier;
      public final DoubleValue hairlineHealDays;
      public final DoubleValue closedHealDays;
      public final DoubleValue openHealDays;
      public final DoubleValue dislocationHealDays;
      public final DoubleValue hungryHealMultiplier;
      public final DoubleValue sleepHealMultiplier;
      public final DoubleValue legHairlineSpeedPenalty;
      public final DoubleValue legClosedSpeedPenalty;
      public final DoubleValue legOpenSpeedPenalty;
      public final DoubleValue legDislocationSpeedPenalty;
      public final DoubleValue dislocationJumpFallChance;
      public final DoubleValue armHairlineDamagePenalty;
      public final DoubleValue armClosedDamagePenalty;
      public final DoubleValue armDislocationDamagePenalty;
      public final DoubleValue dislocationDropChance;
      public final IntValue hairlinePain;
      public final IntValue closedPain;
      public final IntValue openPain;
      public final IntValue dislocationPain;
      public final IntValue reductionPain;
      public final IntValue reductionPainSeconds;
      public final IntValue afterReductionPain;
      public final DoubleValue afterReductionPainDays;
      public final BooleanValue allowWalkOnSplintedBrokenLegs;
      public final DoubleValue walkSpeedWithSplints;
      public final DoubleValue strainDamageChance;
      public final DoubleValue strainDamage;
      public final IntValue strainIntervalSeconds;
      public final IntValue splintWalkMaxPain;
      public final DoubleValue strongPainkillerMorphineDoses;

      Fracture(Builder builder) {
         builder.comment(
               "Fractures (1 hairline, 2 closed, 3 open) and dislocations, per arm and leg.",
               "Healing times are in game days (24000 ticks) of play time; skipping the night in bed counts too."
            )
            .push("fracture");
         this.enabled = builder.define("enabled", true);
         this.fallHairlineMinBlocks = builder.comment("Fall height (blocks) for a hairline fracture chance").defineInRange("fallHairlineMinBlocks", 6.0, 1.0, 256.0);
         this.fallClosedMinBlocks = builder.defineInRange("fallClosedMinBlocks", 10.0, 1.0, 256.0);
         this.fallSevereMinBlocks = builder.comment("From here: 1-2 legs, closed or open, sometimes an arm").defineInRange("fallSevereMinBlocks", 15.0, 1.0, 256.0);
         this.fallHairlineChance = builder.defineInRange("fallHairlineChance", 0.2, 0.0, 1.0);
         this.fallClosedChance = builder.defineInRange("fallClosedChance", 0.4, 0.0, 1.0);
         this.fallSevereLegChance = builder.defineInRange("fallSevereLegChance", 0.7, 0.0, 1.0);
         this.fallSevereArmChance = builder.defineInRange("fallSevereArmChance", 0.2, 0.0, 1.0);
         this.fallSevereOpenChance = builder.comment("Severe fall: chance that a fracture is open (else closed)").defineInRange("fallSevereOpenChance", 0.7, 0.0, 1.0);
         this.landingDislocationChance = builder.comment("Landing from ~3 blocks without fall damage: leg dislocation chance").defineInRange("landingDislocationChance", 0.1, 0.0, 1.0);
         this.explosionChance = builder.defineInRange("explosionChance", 0.3, 0.0, 1.0);
         this.explosionMinDamage = builder.comment("Explosion damage (HP) needed for the fracture chance").defineInRange("explosionMinDamage", 4.0, 0.0, 100.0);
         this.maceChance = builder.defineInRange("maceChance", 0.25, 0.0, 1.0);
         this.maceOpenChance = builder.defineInRange("maceOpenChance", 0.5, 0.0, 1.0);
         this.heavyMobChance = builder.comment("Iron golem / ravager hit: closed arm fracture").defineInRange("heavyMobChance", 0.4, 0.0, 1.0);
         this.axeCritChance = builder.comment("Critical axe hit by a player: hairline arm fracture").defineInRange("axeCritChance", 0.15, 0.0, 1.0);
         this.elytraWallChance = builder.defineInRange("elytraWallChance", 0.5, 0.0, 1.0);
         this.elytraWallOpenChance = builder.defineInRange("elytraWallOpenChance", 0.5, 0.0, 1.0);
         this.frostbittenBreakMultiplier = builder.comment("A frostbitten limb breaks this much more easily").defineInRange("frostbittenBreakMultiplier", 1.5, 1.0, 10.0);
         this.fracturedFrostbiteTimeMultiplier = builder.comment("A broken limb gets frostbite in this part of the usual time").defineInRange("fracturedFrostbiteTimeMultiplier", 0.7, 0.1, 1.0);
         this.hairlineHealDays = builder.defineInRange("hairlineHealDays", 1.0, 0.01, 100.0);
         this.closedHealDays = builder.defineInRange("closedHealDays", 3.0, 0.01, 100.0);
         this.openHealDays = builder.comment("Counts only after the bleeding of its wound is stopped").defineInRange("openHealDays", 5.0, 0.01, 100.0);
         this.dislocationHealDays = builder.comment("Heals by itself in this time, or at once when reduced").defineInRange("dislocationHealDays", 2.0, 0.01, 100.0);
         this.hungryHealMultiplier = builder.comment("Food below 6").defineInRange("hungryHealMultiplier", 0.5, 0.0, 10.0);
         this.sleepHealMultiplier = builder.defineInRange("sleepHealMultiplier", 1.5, 0.0, 10.0);
         this.legHairlineSpeedPenalty = builder.defineInRange("legHairlineSpeedPenalty", 0.15, 0.0, 0.95);
         this.legClosedSpeedPenalty = builder.comment("Also: no sprinting").defineInRange("legClosedSpeedPenalty", 0.35, 0.0, 0.95);
         this.legOpenSpeedPenalty = builder.comment("Also: no sprinting, no jumping").defineInRange("legOpenSpeedPenalty", 0.5, 0.0, 0.95);
         this.legDislocationSpeedPenalty = builder.defineInRange("legDislocationSpeedPenalty", 0.25, 0.0, 0.95);
         this.dislocationJumpFallChance = builder.comment("Dislocated leg: chance to fall over (2 s) on every jump").defineInRange("dislocationJumpFallChance", 0.3, 0.0, 1.0);
         this.armHairlineDamagePenalty = builder.defineInRange("armHairlineDamagePenalty", 0.15, 0.0, 0.95);
         this.armClosedDamagePenalty = builder.comment("Also: no two-handed items (bow, crossbow, shield, trident, pickaxe, axe)").defineInRange("armClosedDamagePenalty", 0.3, 0.0, 0.95);
         this.armDislocationDamagePenalty = builder.defineInRange("armDislocationDamagePenalty", 0.2, 0.0, 0.95);
         this.dislocationDropChance = builder.comment("Dislocated arm: chance to drop the held item when attacking").defineInRange("dislocationDropChance", 0.25, 0.0, 1.0);
         this.hairlinePain = builder.comment("Pain levels 0-5").defineInRange("hairlinePain", 1, 0, 5);
         this.closedPain = builder.defineInRange("closedPain", 3, 0, 5);
         this.openPain = builder.defineInRange("openPain", 4, 0, 5);
         this.dislocationPain = builder.defineInRange("dislocationPain", 3, 0, 5);
         this.reductionPain = builder.comment("Right after a dislocation is reduced (no painkiller)").defineInRange("reductionPain", 5, 0, 5);
         this.reductionPainSeconds = builder.defineInRange("reductionPainSeconds", 10, 0, 600);
         this.afterReductionPain = builder.defineInRange("afterReductionPain", 2, 0, 5);
         this.afterReductionPainDays = builder.defineInRange("afterReductionPainDays", 1.0, 0.0, 30.0);
         this.allowWalkOnSplintedBrokenLegs = builder.comment(
               "Both legs broken but both splinted, under strong pain relief (morphine): walk slowly instead of crawling.",
               "No sprint, jump, melee, carrying/dragging; boats and minecarts only with another player nearby.")
            .define("allowWalkOnSplintedBrokenLegs", true);
         this.walkSpeedWithSplints = builder.comment("Walking speed multiplier while walking on splinted broken legs (0.2 ~ 0.6 blocks/s with the usual leg debuffs)")
            .defineInRange("walkSpeedWithSplints", 0.2, 0.01, 1.0);
         this.strainDamageChance = builder.comment("Every strainIntervalSeconds of walking: chance of strain damage to a leg").defineInRange("strainDamageChance", 0.05, 0.0, 1.0);
         this.strainDamage = builder.defineInRange("strainDamage", 0.3, 0.0, 20.0);
         this.strainIntervalSeconds = builder.defineInRange("strainIntervalSeconds", 10, 1, 600);
         this.splintWalkMaxPain = builder.comment("Pain grows by 1 level per minute of walking, up to this").defineInRange("splintWalkMaxPain", 4, 0, 5);
         this.strongPainkillerMorphineDoses = builder.comment("Morphine pool (doses) that counts as strong pain relief, besides an active morphine effect")
            .defineInRange("strongPainkillerMorphineDoses", 2.0, 0.0, 6.0);
         builder.pop();
      }
   }

   public static final class Splint {
      public final IntValue splintSeconds;
      public final IntValue reduceSeconds;
      public final BooleanValue healWithoutSplintPenalty;
      public final DoubleValue healedWrongLegSpeedPenalty;
      public final DoubleValue healedWrongArmPenalty;

      Splint(Builder builder) {
         builder.comment("Splint (2 sticks + leather + string) and reducing a dislocation").push("splint");
         this.splintSeconds = builder.comment("Standing still while the splint is put on").defineInRange("splintSeconds", 5, 1, 120);
         this.reduceSeconds = builder.comment("Standing still while a dislocation is reduced").defineInRange("reduceSeconds", 3, 1, 120);
         this.healWithoutSplintPenalty = builder.comment("A closed/open fracture that heals without a splint heals wrong (permanent penalty until fixed with a medical kit)")
            .define("healWithoutSplintPenalty", true);
         this.healedWrongLegSpeedPenalty = builder.defineInRange("healedWrongLegSpeedPenalty", 0.2, 0.0, 0.95);
         this.healedWrongArmPenalty = builder.comment("Attack damage and mining speed").defineInRange("healedWrongArmPenalty", 0.25, 0.0, 0.95);
         builder.pop();
      }
   }

   public static final class BandageHead {
      public final DoubleValue bloodDecayDays;
      public final BooleanValue bandageTierFromSeverity;

      BandageHead(Builder builder) {
         builder.comment("Head bandage look (it still uses the helmet slot)").push("bandage_head");
         this.bloodDecayDays = builder.comment("Blood stains fade over this many game days (never fully)").defineInRange("bloodDecayDays", 1.0, 0.0, 100.0);
         this.bandageTierFromSeverity = builder.comment("Concussion II-III when put on: full bandage (cross over the top), else a single strip")
            .define("bandageTierFromSeverity", true);
         builder.pop();
      }
   }

   public static final class Carry {
      public final BooleanValue enabled;
      public final DoubleValue carrySpeedMultiplier;
      public final DoubleValue dragSpeedMultiplier;
      public final DoubleValue carryDropChanceOnDamage;
      public final IntValue dragDamageInterval;
      public final DoubleValue dragDamageHardBlock;
      public final DoubleValue dragDamageSoftBlock;
      public final BooleanValue allowDragWithOneArm;
      public final BooleanValue allowCarryOnlyBothArmsAlive;

      Carry(Builder builder) {
         builder.comment(
               "Carrying (empty hand, right click) and dragging (sneak + right click) a player whose legs don't work",
               "(both legs amputated / gangrene / closed+ fracture / dislocated) or who is unconscious."
            )
            .push("carry");
         this.enabled = builder.define("enabled", true);
         this.carrySpeedMultiplier = builder.defineInRange("carrySpeedMultiplier", 0.6, 0.05, 1.0);
         this.dragSpeedMultiplier = builder.defineInRange("dragSpeedMultiplier", 0.4, 0.05, 1.0);
         this.carryDropChanceOnDamage = builder.comment("Chance to drop the carried player when the carrier gets hurt").defineInRange("carryDropChanceOnDamage", 0.15, 0.0, 1.0);
         this.dragDamageInterval = builder.comment("Seconds").defineInRange("dragDamageInterval", 3, 1, 60);
         this.dragDamageHardBlock = builder.comment("Damage to the dragged player on stone, wood, metal...").defineInRange("dragDamageHardBlock", 0.4, 0.0, 20.0);
         this.dragDamageSoftBlock = builder.comment("... on grass, dirt, snow, sand, carpet, wool").defineInRange("dragDamageSoftBlock", 0.2, 0.0, 20.0);
         this.allowDragWithOneArm = builder.define("allowDragWithOneArm", true);
         this.allowCarryOnlyBothArmsAlive = builder.define("allowCarryOnlyBothArmsAlive", true);
         builder.pop();
      }
   }

   public static final class Commands {
      public final BooleanValue requireOp;

      Commands(Builder builder) {
         builder.push("commands");
         this.requireOp = builder.comment("/medical needs permission level 2 (and is hidden from players without it)").define("requireOp", true);
         builder.pop();
      }
   }

   public static final class Bleeding {
      public final BooleanValue enabled;
      public final DoubleValue hpPerSecondLight;
      public final DoubleValue hpPerSecondMedium;
      public final DoubleValue hpPerSecondHeavy;
      public final DoubleValue hpPerSecondArterial;
      public final DoubleValue arrowHpPerSecond;
      public final IntValue lightStopsAfterSeconds;
      public final DoubleValue unsuturedDressingMultiplier;
      public final DoubleValue healDaysLight;
      public final DoubleValue healDaysMedium;
      public final DoubleValue healDaysHeavy;
      public final DoubleValue healDaysArterial;
      public final DoubleValue dirtyHoursLight;
      public final DoubleValue dirtyHoursMedium;
      public final DoubleValue dirtyHoursHeavy;
      public final DoubleValue dirtyHoursArterial;
      public final DoubleValue dirtyHoursNoWound;
      public final IntValue tourniquetWarnMinutes;
      public final IntValue tourniquetNecrosisMinutes;
      public final IntValue maxBloodDecals;
      public final DoubleValue decalFadeMinutes;
      public final DoubleValue decalFadeSnowMultiplier;
      public final IntValue ragTotalHeals;
      public final IntValue ragSecondsPerHeal;
      public final IntValue ragApplyTimeMs;

      Bleeding(Builder builder) {
         builder.comment(
               "Bleeding wounds per body zone (head, torso, arms, legs). Stages: 1 light (scratch), 2 medium (laceration),",
               "3 heavy (deep wound), 4 arterial. Blood loss is spread over the whole body, ~32 HP total kills.",
               "Game hours: 1000 ticks; game days: 24000 ticks."
            )
            .push("bleeding");
         this.enabled = builder.define("enabled", true);
         this.hpPerSecondLight = builder.defineInRange("hpPerSecondLight", 0.02, 0.0, 20.0);
         this.hpPerSecondMedium = builder.comment("~half the health in 4 minutes").defineInRange("hpPerSecondMedium", 0.07, 0.0, 20.0);
         this.hpPerSecondHeavy = builder.comment("~2 minutes to death").defineInRange("hpPerSecondHeavy", 0.25, 0.0, 20.0);
         this.hpPerSecondArterial = builder.comment("~45 seconds to death").defineInRange("hpPerSecondArterial", 0.7, 0.0, 20.0);
         this.arrowHpPerSecond = builder.comment("A stuck arrow plugs the wound but keeps hurting").defineInRange("arrowHpPerSecond", 0.03, 0.0, 20.0);
         this.lightStopsAfterSeconds = builder.comment("A light bleeding stops by itself").defineInRange("lightStopsAfterSeconds", 90, 1, 72000);
         this.unsuturedDressingMultiplier = builder.comment("Heavy wound under a rag / bandage but not sutured: bleeding multiplied by this")
            .defineInRange("unsuturedDressingMultiplier", 0.5, 0.0, 1.0);
         this.healDaysLight = builder.comment("Healing time after the bleeding stopped (game days)").defineInRange("healDaysLight", 0.25, 0.01, 100.0);
         this.healDaysMedium = builder.defineInRange("healDaysMedium", 0.75, 0.01, 100.0);
         this.healDaysHeavy = builder.defineInRange("healDaysHeavy", 1.5, 0.01, 100.0);
         this.healDaysArterial = builder.defineInRange("healDaysArterial", 2.0, 0.01, 100.0);
         this.dirtyHoursLight = builder.comment("A dressing gets dirty in this many game hours, by the wound under it").defineInRange("dirtyHoursLight", 24.0, 0.01, 1000.0);
         this.dirtyHoursMedium = builder.defineInRange("dirtyHoursMedium", 4.0, 0.01, 1000.0);
         this.dirtyHoursHeavy = builder.defineInRange("dirtyHoursHeavy", 1.0, 0.01, 1000.0);
         this.dirtyHoursArterial = builder.defineInRange("dirtyHoursArterial", 0.25, 0.01, 1000.0);
         this.dirtyHoursNoWound = builder.comment("Dressing left on healed skin").defineInRange("dirtyHoursNoWound", 24.0, 0.01, 1000.0);
         this.tourniquetWarnMinutes = builder.comment("Real minutes").defineInRange("tourniquetWarnMinutes", 10, 1, 600);
         this.tourniquetNecrosisMinutes = builder.comment("Tourniquet left on this long (real minutes): the limb dies (gangrene)").defineInRange("tourniquetNecrosisMinutes", 15, 1, 600);
         this.maxBloodDecals = builder.comment("Client: blood stains on blocks, oldest go first").defineInRange("maxBloodDecals", 200, 0, 2000);
         this.decalFadeMinutes = builder.comment("Client: real minutes until a stain is gone").defineInRange("decalFadeMinutes", 5.0, 0.1, 120.0);
         this.decalFadeSnowMultiplier = builder.comment("Stains on snow fade this much faster (snow covers them)").defineInRange("decalFadeSnowMultiplier", 2.0, 1.0, 20.0);
         this.ragTotalHeals = builder.comment("Rag: heals like a weak bandage (1 heal = half a heart)").defineInRange("ragTotalHeals", 1, 1, 100);
         this.ragSecondsPerHeal = builder.defineInRange("ragSecondsPerHeal", 25, 1, 3600);
         this.ragApplyTimeMs = builder.defineInRange("ragApplyTimeMs", 2500, 0, 16000);
         builder.pop();
      }
   }

   public static final class Wounds {
      public final IntValue arrowExtractSeconds;
      public final IntValue arrowAutoEjectMinutes;
      public final IntValue sutureSeconds;
      public final DoubleValue sharpLacerationChance;
      public final DoubleValue bluntScratchChance;
      public final DoubleValue biteInfectionChance;
      public final DoubleValue explosionMinDamage;
      public final IntValue extractPain;
      public final IntValue extractPainSeconds;

      Wounds(Builder builder) {
         builder.comment("Where wounds come from and how they are treated").push("wounds");
         this.arrowExtractSeconds = builder.defineInRange("arrowExtractSeconds", 3, 1, 60);
         this.arrowAutoEjectMinutes = builder.comment("A stuck arrow falls out by itself after this many real minutes, leaving a worse wound").defineInRange("arrowAutoEjectMinutes", 5, 1, 600);
         this.sutureSeconds = builder.defineInRange("sutureSeconds", 5, 1, 60);
         this.sharpLacerationChance = builder.comment("Sword / axe / claws / teeth: chance of a laceration (else a scratch)").defineInRange("sharpLacerationChance", 0.7, 0.0, 1.0);
         this.bluntScratchChance = builder.comment("Fists and other blunt hits: chance of a scratch").defineInRange("bluntScratchChance", 0.35, 0.0, 1.0);
         this.biteInfectionChance = builder.comment("Zombie / wolf / spider bite: the wound is contaminated").defineInRange("biteInfectionChance", 0.2, 0.0, 1.0);
         this.explosionMinDamage = builder.comment("Explosion damage (HP) for an arterial wound").defineInRange("explosionMinDamage", 4.0, 0.0, 100.0);
         this.extractPain = builder.comment("Pain level while pulling an arrow out (no painkiller)").defineInRange("extractPain", 4, 0, 5);
         this.extractPainSeconds = builder.defineInRange("extractPainSeconds", 5, 0, 600);
         builder.pop();
      }
   }

   public static final class Infection {
      public final BooleanValue enabled;
      public final DoubleValue stageHours;
      public final DoubleValue dirtyDressingInfectionDays;
      public final DoubleValue contaminationHours;
      public final DoubleValue alcoholPreventionChance;
      public final DoubleValue openFractureInfectionHours;
      public final DoubleValue openFractureInfectionChance;
      public final DoubleValue openWoundSleepChance;
      public final DoubleValue coldSleepMultiplier;
      public final DoubleValue antibioticCooldownDays;
      public final IntValue antibioticStages;
      public final DoubleValue stage1HealHours;
      public final DoubleValue sleepHealMultiplier;
      public final DoubleValue feverStage2;
      public final DoubleValue feverStage3;
      public final DoubleValue feverStage4;
      public final DoubleValue stage2Damage;
      public final IntValue stage2IntervalSeconds;
      public final DoubleValue stage3Damage;
      public final IntValue stage3IntervalSeconds;
      public final DoubleValue stage4Damage;
      public final IntValue stage4IntervalSeconds;
      public final DoubleValue necrosisHours;

      Infection(Builder builder) {
         builder.comment(
               "Infection 1 mild, 2 moderate, 3 severe, 4 sepsis; a limb infected at stage 4 dies (necrosis, gangrene).",
               "Antibiotic: -1 stage and no worsening for a day. Stage 1 heals by itself with a clean dressing on a disinfected wound."
            )
            .push("infection");
         this.enabled = builder.define("enabled", true);
         this.stageHours = builder.comment("Game hours until the next stage without treatment").defineInRange("stageHours", 12.0, 0.1, 1000.0);
         this.dirtyDressingInfectionDays = builder.comment("A dirty dressing left on a wound this long starts an infection").defineInRange("dirtyDressingInfectionDays", 1.0, 0.01, 100.0);
         this.contaminationHours = builder.comment("A contaminated wound (bite) not disinfected within this many game hours gets infected").defineInRange("contaminationHours", 1.0, 0.01, 100.0);
         this.alcoholPreventionChance = builder.comment("Alcohol on a wound: chance that it prevents an infection").defineInRange("alcoholPreventionChance", 0.8, 0.0, 1.0);
         this.openFractureInfectionHours = builder.comment("Open fracture without a medical kit / alcohol for this long: infection roll").defineInRange("openFractureInfectionHours", 6.0, 0.1, 1000.0);
         this.openFractureInfectionChance = builder.defineInRange("openFractureInfectionChance", 0.5, 0.0, 1.0);
         this.openWoundSleepChance = builder.comment("Sleeping with an undressed wound").defineInRange("openWoundSleepChance", 0.1, 0.0, 1.0);
         this.coldSleepMultiplier = builder.comment("... while cold (hypothermia)").defineInRange("coldSleepMultiplier", 2.0, 1.0, 10.0);
         this.antibioticCooldownDays = builder.comment("One antibiotic per this many game days counts").defineInRange("antibioticCooldownDays", 1.0, 0.0, 10.0);
         this.antibioticStages = builder.comment("Stages removed per antibiotic").defineInRange("antibioticStages", 1, 1, 4);
         this.stage1HealHours = builder.defineInRange("stage1HealHours", 12.0, 0.1, 1000.0);
         this.sleepHealMultiplier = builder.defineInRange("sleepHealMultiplier", 1.5, 1.0, 10.0);
         this.feverStage2 = builder.comment("Fever, degrees C over the body temperature").defineInRange("feverStage2", 0.3, 0.0, 5.0);
         this.feverStage3 = builder.defineInRange("feverStage3", 0.8, 0.0, 5.0);
         this.feverStage4 = builder.defineInRange("feverStage4", 1.5, 0.0, 5.0);
         this.stage2Damage = builder.defineInRange("stage2Damage", 0.3, 0.0, 20.0);
         this.stage2IntervalSeconds = builder.defineInRange("stage2IntervalSeconds", 30, 1, 3600);
         this.stage3Damage = builder.defineInRange("stage3Damage", 0.5, 0.0, 20.0);
         this.stage3IntervalSeconds = builder.defineInRange("stage3IntervalSeconds", 10, 1, 3600);
         this.stage4Damage = builder.defineInRange("stage4Damage", 1.0, 0.0, 20.0);
         this.stage4IntervalSeconds = builder.comment("Sepsis kills in ~5 minutes without an antibiotic").defineInRange("stage4IntervalSeconds", 10, 1, 3600);
         this.necrosisHours = builder.comment("Sepsis from a limb: the limb dies after this many game hours").defineInRange("necrosisHours", 2.0, 0.01, 1000.0);
         builder.pop();
      }
   }

   public static final class Anemia {
      public final BooleanValue enabled;
      public final DoubleValue hpThreshold;
      public final IntValue windowSeconds;
      public final DoubleValue durationDays;
      public final DoubleValue foodRecoveryBonus;
      public final DoubleValue attackPenalty;

      Anemia(Builder builder) {
         builder.comment("Anemia after losing a lot of blood").push("anemia");
         this.enabled = builder.define("enabled", true);
         this.hpThreshold = builder.comment("HP lost to bleeding within windowSeconds").defineInRange("hpThreshold", 8.0, 0.5, 100.0);
         this.windowSeconds = builder.comment("Real seconds").defineInRange("windowSeconds", 600, 10, 72000);
         this.durationDays = builder.defineInRange("durationDays", 1.0, 0.01, 100.0);
         this.foodRecoveryBonus = builder.comment("Meat / bread: this part of the remaining time is gone").defineInRange("foodRecoveryBonus", 0.15, 0.0, 1.0);
         this.attackPenalty = builder.defineInRange("attackPenalty", 0.1, 0.0, 0.95);
         builder.pop();
      }
   }

   public static final class General {
      public final BooleanValue debugLogging;
      public final IntValue medicalLogSize;

      General(Builder builder) {
         builder.push("general");
         this.debugLogging = builder.comment("Extra medical events in the log file (never in chat)").define("debugLogging", false);
         this.medicalLogSize = builder.comment("/lastaid log keeps this many events per player").defineInRange("medicalLogSize", 20, 1, 200);
         builder.pop();
      }
   }
}
