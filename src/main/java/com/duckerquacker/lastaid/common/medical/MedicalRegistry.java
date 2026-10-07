package com.duckerquacker.lastaid.common.medical;

import com.duckerquacker.lastaid.common.medical.items.ItemAntiviral;
import com.duckerquacker.lastaid.common.medical.items.ItemBarkBroth;
import com.duckerquacker.lastaid.common.medical.items.ItemHeadBandage;
import com.duckerquacker.lastaid.common.medical.items.ItemHerbalTea;
import com.duckerquacker.lastaid.common.medical.items.ItemIcePack;
import com.duckerquacker.lastaid.common.medical.items.ItemMedicalKit;
import com.duckerquacker.lastaid.common.medical.items.ItemNeckBrace;
import com.duckerquacker.lastaid.common.medical.items.ItemSurgicalKnife;
import com.duckerquacker.lastaid.common.medical.items.ItemTooltip;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public final class MedicalRegistry {
   private static final Items ITEMS = DeferredRegister.createItems("lastaid");
   private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "lastaid");
   private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, "lastaid");
   public static final DeferredItem<ItemIcePack> ICE_PACK = ITEMS.registerItem("ice_pack", ItemIcePack::new, new Properties().durability(3));
   public static final DeferredItem<Item> TEA_BLEND = ITEMS.registerItem(
      "herbal_tea_blend", properties -> new ItemTooltip(properties, "item.lastaid.herbal_tea_blend.desc"), new Properties().stacksTo(16)
   );
   public static final DeferredItem<ItemHerbalTea> HERBAL_TEA = ITEMS.registerItem("herbal_tea", ItemHerbalTea::new, new Properties().stacksTo(16));
   public static final DeferredItem<Item> BARK = ITEMS.registerItem(
      "bark", properties -> new ItemTooltip(properties, "item.lastaid.bark.desc"), new Properties()
   );
   public static final DeferredItem<ItemBarkBroth> BARK_BROTH = ITEMS.registerItem("bark_broth", ItemBarkBroth::new, new Properties().stacksTo(16));
   public static final DeferredItem<ItemHeadBandage> HEAD_BANDAGE = ITEMS.registerItem("head_bandage", ItemHeadBandage::new, new Properties().stacksTo(1));
   public static final DeferredItem<ItemNeckBrace> NECK_BRACE = ITEMS.registerItem("neck_brace", ItemNeckBrace::new, new Properties().stacksTo(4));
   public static final DeferredItem<ItemMedicalKit> MEDICAL_KIT = ITEMS.registerItem("medical_kit", ItemMedicalKit::new, new Properties().stacksTo(4));
   public static final DeferredItem<ItemAntiviral> ANTIVIRAL = ITEMS.registerItem("antiviral", ItemAntiviral::new, new Properties().durability(4));
   public static final DeferredItem<ItemSurgicalKnife> SURGICAL_KNIFE = ITEMS.registerItem(
      "surgical_knife", ItemSurgicalKnife::new, new Properties().durability(32)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemAntidote> NALOXONE = ITEMS.registerItem(
      "naloxone",
      properties -> new com.duckerquacker.lastaid.common.medical.items.ItemAntidote(
         properties, com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.MORPHINE, "item.lastaid.naloxone.desc"
      ),
      new Properties().stacksTo(4)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemAntidote> BETA_BLOCKER = ITEMS.registerItem(
      "beta_blocker",
      properties -> new com.duckerquacker.lastaid.common.medical.items.ItemAntidote(
         properties, com.duckerquacker.lastaid.common.medical.overdose.Overdose.Drug.ADRENALINE, "item.lastaid.beta_blocker.desc"
      ),
      new Properties().stacksTo(4)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemSplint> SPLINT = ITEMS.registerItem(
      "splint", com.duckerquacker.lastaid.common.medical.items.ItemSplint::new, new Properties().stacksTo(8)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.api.healing.ItemHealing> RAG = ITEMS.registerItem(
      "rag",
      properties -> com.duckerquacker.lastaid.api.healing.ItemHealing.create(
         properties,
         stack -> new com.duckerquacker.lastaid.common.damagesystem.PartHealer(
            () -> com.duckerquacker.lastaid.LastAid.scaleMedicalTimingTicks(MedicalConfig.SERVER.bleeding.ragSecondsPerHeal.get() * 20),
            () -> MedicalConfig.SERVER.bleeding.ragTotalHeals.get(),
            stack
         ),
         stack -> MedicalConfig.SERVER.bleeding.ragApplyTimeMs.get(),
         stack -> com.duckerquacker.lastaid.common.RegistryObjects.BANDAGE_USE.value(),
         stack -> com.duckerquacker.lastaid.api.healing.ItemHealing.ApplySoundMode.WHILE_USING
      ),
      new Properties().stacksTo(16)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemDirtyDressing> DIRTY_RAG = ITEMS.registerItem(
      "dirty_rag", properties -> new com.duckerquacker.lastaid.common.medical.items.ItemDirtyDressing(properties, RAG, "item.lastaid.dirty_rag.desc"), new Properties().stacksTo(16)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemDirtyDressing> DIRTY_BANDAGE = ITEMS.registerItem(
      "dirty_bandage",
      properties -> new com.duckerquacker.lastaid.common.medical.items.ItemDirtyDressing(properties, com.duckerquacker.lastaid.common.RegistryObjects.BANDAGE, "item.lastaid.dirty_bandage.desc"),
      new Properties().stacksTo(16)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemInfectedRag> INFECTED_RAG = ITEMS.registerItem(
      "infected_rag", properties -> new com.duckerquacker.lastaid.common.medical.items.ItemInfectedRag(properties, "item.lastaid.infected_rag.desc"), new Properties().stacksTo(16)
   );
   public static final DeferredItem<Item> TOURNIQUET = ITEMS.registerItem(
      "tourniquet", properties -> new ItemTooltip(properties, "item.lastaid.tourniquet.desc"), new Properties().stacksTo(4)
   );
   public static final DeferredItem<Item> SEWING_KIT = ITEMS.registerItem(
      "sewing_kit", properties -> new ItemTooltip(properties, "item.lastaid.sewing_kit.desc"), new Properties().durability(5)
   );
   public static final DeferredItem<Item> ALCOHOL = ITEMS.registerItem(
      "alcohol", properties -> new ItemTooltip(properties, "item.lastaid.alcohol.desc"), new Properties().durability(4)
   );
   public static final DeferredItem<com.duckerquacker.lastaid.common.medical.items.ItemAntibiotic> ANTIBIOTIC = ITEMS.registerItem(
      "antibiotic", properties -> new com.duckerquacker.lastaid.common.medical.items.ItemAntibiotic(properties, "item.lastaid.antibiotic.desc"), new Properties().stacksTo(16)
   );
   public static final Holder<MobEffect> CALM = EFFECTS.register("calm", () -> new MedicalRegistry.MedicalEffect(MobEffectCategory.BENEFICIAL, 10474394));
   public static final Holder<MobEffect> CLARITY = EFFECTS.register("clarity", () -> new MedicalRegistry.MedicalEffect(MobEffectCategory.BENEFICIAL, 12117759));
   public static final Holder<MobEffect> HEAD_REGEN = EFFECTS.register("head_regen", () -> new HeadRegenEffect(MobEffectCategory.BENEFICIAL, 13214334));
   public static final Holder<MobEffect> NECK_BRACED = EFFECTS.register(
      "neck_braced", () -> new MedicalRegistry.MedicalEffect(MobEffectCategory.NEUTRAL, 14737632)
   );
   public static final Holder<MobEffect> WARM_PROTECTION = EFFECTS.register(
      "warm_protection", () -> new MedicalRegistry.MedicalEffect(MobEffectCategory.BENEFICIAL, 15245392)
   );
   public static final Holder<MobEffect> ANTIVIRAL_EFFECT = EFFECTS.register(
      "antiviral", () -> new MedicalRegistry.MedicalEffect(MobEffectCategory.BENEFICIAL, 8446144)
   );
   public static final Holder<MobEffect> STABILIZED = EFFECTS.register(
      "stabilized", () -> new MedicalRegistry.MedicalEffect(MobEffectCategory.BENEFICIAL, 16744576)
   );
   public static final Holder<Potion> CLARITY_POTION = POTIONS.register(
      "clarity", () -> new Potion("clarity", new MobEffectInstance[]{new MobEffectInstance(CLARITY, 3600)})
   );

   private MedicalRegistry() {
   }

   public static void register(IEventBus iEventBus) {
      ITEMS.register(iEventBus);
      EFFECTS.register(iEventBus);
      POTIONS.register(iEventBus);
   }

   public static void onBrewingRecipes(RegisterBrewingRecipesEvent registerBrewingRecipesEvent) {
      registerBrewingRecipesEvent.getBuilder().addMix(Potions.AWKWARD, net.minecraft.world.item.Items.GLOW_LICHEN, CLARITY_POTION);
   }

   public static void addToCreativeTab(BuildCreativeModeTabContentsEvent buildCreativeModeTabContentsEvent) {
      buildCreativeModeTabContentsEvent.accept(ICE_PACK);
      buildCreativeModeTabContentsEvent.accept(TEA_BLEND);
      buildCreativeModeTabContentsEvent.accept(HERBAL_TEA);
      buildCreativeModeTabContentsEvent.accept(BARK);
      buildCreativeModeTabContentsEvent.accept(BARK_BROTH);
      buildCreativeModeTabContentsEvent.accept(HEAD_BANDAGE);
      buildCreativeModeTabContentsEvent.accept(NECK_BRACE);
      buildCreativeModeTabContentsEvent.accept(MEDICAL_KIT);
      buildCreativeModeTabContentsEvent.accept(ANTIVIRAL);
      buildCreativeModeTabContentsEvent.accept(SURGICAL_KNIFE);
      buildCreativeModeTabContentsEvent.accept(SPLINT);
      buildCreativeModeTabContentsEvent.accept(NALOXONE);
      buildCreativeModeTabContentsEvent.accept(BETA_BLOCKER);
      buildCreativeModeTabContentsEvent.accept(RAG);
      buildCreativeModeTabContentsEvent.accept(DIRTY_RAG);
      buildCreativeModeTabContentsEvent.accept(INFECTED_RAG);
      buildCreativeModeTabContentsEvent.accept(DIRTY_BANDAGE);
      buildCreativeModeTabContentsEvent.accept(TOURNIQUET);
      buildCreativeModeTabContentsEvent.accept(SEWING_KIT);
      buildCreativeModeTabContentsEvent.accept(ALCOHOL);
      buildCreativeModeTabContentsEvent.accept(ANTIBIOTIC);
   }

   public static class MedicalEffect extends MobEffect {
      public MedicalEffect(MobEffectCategory mobEffectCategory, int i) {
         super(mobEffectCategory, i);
      }
   }
}
