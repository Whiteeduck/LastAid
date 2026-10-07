package com.duckerquacker.lastaid.client.gui.medical;

import com.duckerquacker.lastaid.LastAid;
import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.healing.ItemHealing;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.api.medicine.MedicineStatusDisplay;
import com.duckerquacker.lastaid.client.ClientEventHandler;
import com.duckerquacker.lastaid.client.ClientHooks;
import com.duckerquacker.lastaid.client.MedicineStatusClientHelper;
import com.duckerquacker.lastaid.common.RegistryObjects;
import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import com.duckerquacker.lastaid.common.medical.ClientMedicalState;
import com.duckerquacker.lastaid.common.medical.MedicalConfig;
import com.duckerquacker.lastaid.common.medical.MedicalHooks;
import com.duckerquacker.lastaid.common.medical.MedicalIcons;
import com.duckerquacker.lastaid.common.network.BodyStatusSyncPacket;
import com.duckerquacker.lastaid.common.network.MessageClientRequest;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class BodyStatusScreen extends Screen {
   public static final int PANEL_WIDTH = 340;
   public static final int PANEL_HEIGHT = 206;
   private static final float FIGURE_SCALE = 2.35F;
   private static final ResourceLocation HEART_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
   private static final ResourceLocation FOOD_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_full");
   protected final AbstractPlayerDamageModel damageModel;
   protected final InteractionHand activeHand;
   private final Map<EnumPlayerPart, BodyPartWidget> parts = new EnumMap<>(EnumPlayerPart.class);
   private final List<StatusRowWidget> rows = new ArrayList<>();
   private float scale = 1.0F;
   private int left;
   private int top;
   private int listX;
   private int listY;
   private int listWidth;
   private int listHeight;
   private double scroll;
   private double lastWorldC = Double.NaN;
   private int figureX;
   private int figureY;

   public BodyStatusScreen(AbstractPlayerDamageModel abstractPlayerDamageModel, InteractionHand interactionHand) {
      super(Component.translatable("lastaid.gui.healthscreen"));
      this.damageModel = abstractPlayerDamageModel;
      this.activeHand = interactionHand;
   }

   protected void init() {
      float f = MedicalConfig.CLIENT_SPEC.isLoaded() ? ((Double)MedicalConfig.CLIENT.panelScale.get()).floatValue() : 1.0F;
      this.scale = Math.min(f, Math.min((this.width - 8) / 340.0F, (this.height - 8) / 206.0F));
      this.scale = Math.max(0.4F, this.scale);
      int i = Math.round(this.width / this.scale);
      int j = Math.round(this.height / this.scale);
      this.left = (i - 340) / 2;
      this.top = (j - 206) / 2;
      int k = Math.round(BodyPartLayout.WIDTH * FIGURE_SCALE);
      int l = this.left + 12 + (100 - k) / 2;
      int m = this.top + 28;
      this.figureX = l;
      this.figureY = m;
      this.parts.clear();

      for (EnumPlayerPart enumPlayerPart : EnumPlayerPart.VALUES) {
         this.parts.put(enumPlayerPart, new BodyPartWidget(enumPlayerPart, l, m, FIGURE_SCALE));
      }

      this.listX = this.left + 132;
      this.listY = this.top + 24;
      this.listWidth = 196;
      this.listHeight = 164;
      this.rebuildRows();
   }

   public void tick() {
      this.rebuildRows();
   }

   protected AbstractPlayerDamageModel liveModel() {
      if (this.minecraft != null && this.minecraft.player != null) {
         AbstractPlayerDamageModel abstractPlayerDamageModel = CommonUtils.getDamageModel(this.minecraft.player);
         if (abstractPlayerDamageModel != null) {
            return abstractPlayerDamageModel;
         }
      }

      return this.damageModel;
   }

   private void rebuildRows() {
      this.rows.clear();
      if (this.minecraft != null && this.minecraft.player != null) {
         LocalPlayer localPlayer = this.minecraft.player;
         BodyStatusSyncPacket bodyStatusSyncPacket = ClientMedicalState.get();
         AbstractPlayerDamageModel abstractPlayerDamageModel = this.liveModel();
         float f = localPlayer.getHealth();
         float g = localPlayer.getMaxHealth();
         this.rows
            .add(
               StatusRowWidget.builder(Component.translatable("lastaid.gui.row.health"))
                  .sprite(HEART_SPRITE)
                  .value(Component.literal(String.format(Locale.ROOT, "%.1f / %.0f", f, g)), 16742263)
                  .bar(f / Math.max(1.0F, g), 14168112)
                  .build()
            );
         FoodData foodData = localPlayer.getFoodData();
         this.rows
            .add(
               StatusRowWidget.builder(Component.translatable("lastaid.gui.row.hunger"))
                  .sprite(FOOD_SPRITE)
                  .value(Component.literal(foodData.getFoodLevel() + " / 20"), 14725216)
                  .bar(foodData.getFoodLevel() / 20.0F, 12615744)
                  .tooltip(
                     List.of(
                        Component.translatable("lastaid.gui.row.saturation", new Object[]{String.format(Locale.ROOT, "%.1f", foodData.getSaturationLevel())})
                     )
                  )
                  .build()
            );
         int i = bodyStatusSyncPacket.ticksSinceRest();
         float h = i / 24000.0F;
         MutableComponent mutableComponent = h < 1.0F
            ? Component.translatable("lastaid.gui.row.fatigue.rested")
            : Component.translatable("lastaid.gui.row.fatigue.days", new Object[]{String.format(Locale.ROOT, "%.1f", h)});
         this.rows
            .add(
               StatusRowWidget.builder(Component.translatable("lastaid.gui.row.fatigue"))
                  .icon(new ItemStack(Items.RED_BED))
                  .value(mutableComponent, h >= 3.0F ? 16733525 : (h >= 1.0F ? 16777045 : 5635925))
                  .bar(Math.min(1.0F, i / 72000.0F), 9461952)
                  .tooltip(List.of(Component.translatable("lastaid.gui.row.fatigue.tooltip")))
                  .build()
            );
         BodyStatusSyncPacket.Temperature temperature = bodyStatusSyncPacket.temperature();
         if (temperature.available()) {
            float j = temperature.bodyC();
            ArrayList arrayList = new ArrayList();
            if (temperature.grace()) {
               arrayList.add(MedicalIcons.SHIELD);
            }

            if (temperature.warmProtection()) {
               arrayList.add(MedicalIcons.CUP);
            }

            ArrayList arrayList1 = new ArrayList();
            if (temperature.grace()) {
               arrayList1.add(Component.translatable("lastaid.gui.row.body_temp.grace").withStyle(ChatFormatting.WHITE));
            }

            if (temperature.warmProtection()) {
               arrayList1.add(Component.translatable("lastaid.gui.row.body_temp.tea").withStyle(ChatFormatting.GOLD));
            }

            this.rows
               .add(
                  StatusRowWidget.builder(Component.translatable("lastaid.gui.row.body_temp"))
                     .texture(MedicalIcons.THERMOMETER, false)
                     .value(Component.literal(String.format(Locale.ROOT, "%.1f °C", j)), temperature.grace() ? 16053503 : MedicalGuiUtil.bodyTempColor(j))
                     .badges(arrayList)
                     .highlight(temperature.grace())
                     .bar(Mth.clamp((j - 32.0F) / 10.0F, 0.0F, 1.0F), temperature.grace() ? 14737648 : MedicalGuiUtil.bodyTempColor(j))
                     .tooltip(arrayList1)
                     .build()
               );
            if (!Float.isNaN(temperature.worldC())) {
               String string = temperature.worldTrend() > 0 ? " ↑" : (temperature.worldTrend() < 0 ? " ↓" : "");
               this.rows
                  .add(
                     StatusRowWidget.builder(Component.translatable("lastaid.gui.row.world_temp"))
                        .icon(new ItemStack(temperature.worldC() < 5.0F ? Items.SNOWBALL : Items.CAMPFIRE))
                        .value(Component.literal(String.format(Locale.ROOT, "%.1f °C%s", temperature.worldC(), string)), worldColor(temperature.worldC()))
                        .build()
                  );
            }

            if (!Float.isNaN(temperature.feelsLikeC())) {
               List list = temperature.wet() ? List.of(MedicalIcons.DROP) : List.of();
               ArrayList arrayList2 = new ArrayList();
               arrayList2.add(Component.translatable("lastaid.gui.row.feels_like.tooltip"));
               if (temperature.wet()) {
                  arrayList2.add(Component.translatable("lastaid.gui.row.feels_like.wet").withStyle(ChatFormatting.BLUE));
               }

               this.rows
                  .add(
                     StatusRowWidget.builder(Component.translatable("lastaid.gui.row.feels_like"))
                        .icon(new ItemStack(Items.WIND_CHARGE))
                        .value(Component.literal(String.format(Locale.ROOT, "%.1f °C", temperature.feelsLikeC())), worldColor(temperature.feelsLikeC()))
                        .badges(list)
                        .tooltip(arrayList2)
                        .build()
                  );
            }

            if (temperature.insulationPercent() >= 0.0F) {
               this.rows
                  .add(
                     StatusRowWidget.builder(Component.translatable("lastaid.gui.row.clothing"))
                        .texture(MedicalIcons.SWEATER, false)
                        .value(Component.translatable("lastaid.gui.row.clothing.value", new Object[]{Math.round(temperature.insulationPercent())}), 14729360)
                        .bar(temperature.insulationPercent() / 100.0F, 12619872)
                        .tooltip(List.of(Component.translatable("lastaid.gui.row.clothing.tooltip")))
                        .build()
                  );
            }
            int n = switch (temperature.comfort()) {
               case 0 -> 5635925;
               case 1 -> 8377599;
               case 2 -> 16777045;
               default -> 16733525;
            };
            this.rows
               .add(
                  StatusRowWidget.builder(Component.translatable("lastaid.gui.row.comfort"))
                     .icon(new ItemStack(Items.LEATHER_CHESTPLATE))
                     .value(Component.translatable("lastaid.gui.comfort." + temperature.comfort()), n)
                     .build()
               );
         }

         if (abstractPlayerDamageModel instanceof PlayerDamageModel playerDamageModel) {
            int k = Mth.clamp(playerDamageModel.getPainLevel(), 0, 5);
            boolean painkillerActive = localPlayer.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
            if (k > 0) {
               List<Component> painTooltip = new ArrayList<>();
               painTooltip.add(Component.translatable("lastaid.pain.desc." + k).withStyle(ChatFormatting.GRAY));
               if (painkillerActive) {
                  painTooltip.add(Component.translatable("lastaid.pain.reduced").withStyle(ChatFormatting.GREEN));
               }
               this.rows
                  .add(
                     StatusRowWidget.builder(Component.translatable("lastaid.gui.row.pain"))
                        .icon(new ItemStack(Items.REDSTONE))
                        .value(
                           Component.translatable("lastaid.pain.level." + k).append(Component.literal(" " + k + "/5")),
                           k >= 4 ? 16733525 : (k >= 2 ? 16755200 : 16777045)
                        )
                        .bar(k / 5.0F, 16736352)
                        .tooltip(painTooltip)
                        .build()
                  );
            }

            // Painkillers: First Aid applies them hidden and with a delay, so show both states here
            net.minecraft.world.effect.MobEffectInstance painkiller = localPlayer.getEffect(RegistryObjects.PAINKILLER_EFFECT);
            int pending = playerDamageModel.getPendingPainkillerTicks();
            if (painkiller != null || pending > 0) {
               int reduction = MedicalConfig.SERVER_SPEC.isLoaded() ? (int)Math.round(MedicalConfig.SERVER.pain.painkillerReduction.get() * 100.0) : 60;
               StatusRowWidget.Builder builder = StatusRowWidget.builder(Component.translatable("lastaid.gui.row.painkiller"))
                  .icon(new ItemStack((ItemLike)RegistryObjects.PAINKILLERS.get()));
               if (painkiller != null) {
                  int total = PlayerDamageModel.getPainkillerDuration();
                  builder.value(
                        Component.translatable("lastaid.gui.painkiller.active", StringUtil.formatTickDuration(painkiller.getDuration(), 20.0F)), 5635925
                     )
                     .bar(Mth.clamp(painkiller.getDuration() / (float)Math.max(1, total), 0.0F, 1.0F), 5635925)
                     .tooltip(List.of(Component.translatable("lastaid.gui.painkiller.tooltip", reduction).withStyle(ChatFormatting.GREEN)));
               } else {
                  builder.value(Component.translatable("lastaid.gui.painkiller.pending", StringUtil.formatTickDuration(pending, 20.0F)), 16777045)
                     .tooltip(List.of(Component.translatable("lastaid.gui.painkiller.pending_tooltip").withStyle(ChatFormatting.GRAY)));
               }
               this.rows.add(builder.build());
            }

            int l = playerDamageModel.getAdrenalineLevel();
            if (l > 0) {
               this.rows
                  .add(
                     StatusRowWidget.builder(Component.translatable("lastaid.gui.row.adrenaline"))
                        .icon(new ItemStack((ItemLike)RegistryObjects.ADRENALINE_INJECTOR.get()))
                        .value(Component.literal(l + ""), 16755285)
                        .bar(playerDamageModel.getSuppressionIntensity(), 16755285)
                        .build()
                  );
            }
         }

         // Drugs in the body (overdose pools): "Morphine ███░░░ 3/6", hidden while empty
         ConditionSnapshot drugs = ClientMedicalState.find(com.duckerquacker.lastaid.common.medical.overdose.Overdose.DRUGS_ID);
         if (drugs != null) {
            int max = Math.max(1, drugs.severity());
            int packed = drugs.clientFlags();
            this.addDrugRow("morphine", packed & 63, max, new ItemStack((ItemLike)RegistryObjects.MORPHINE.get()));
            this.addDrugRow("adrenaline", packed >> 6 & 63, max, new ItemStack((ItemLike)RegistryObjects.ADRENALINE_INJECTOR.get()));
         }

         for (MedicineStatusDisplay medicineStatusDisplay : MedicineStatusClientHelper.collect(localPlayer)) {
            if (medicineStatusDisplay.getStatusId() != null && medicineStatusDisplay.getStatusId().getPath().equals("painkiller")) {
               continue; // shown above with a timer
            }
            this.rows.add(StatusRowWidget.builder(medicineStatusDisplay.getText().copy()).dot(medicineStatusDisplay.getColor() & 16777215).build());
         }

         for (ConditionSnapshot conditionSnapshot : bodyStatusSyncPacket.conditions()) {
            if (conditionSnapshot.hasFlag(com.duckerquacker.lastaid.common.medical.overdose.Overdose.FLAG_HIDDEN)) {
               continue;
            }
            int m = MedicalGuiUtil.conditionColor(conditionSnapshot);
            MutableComponent mutableComponent1 = conditionSnapshot.isRisk()
               ? Component.literal(Math.round(Math.max(0.0F, conditionSnapshot.progress()) * 100.0F) + "%")
               : (
                  conditionSnapshot.hasFlag(4)
                     ? Component.translatable("lastaid.frostbite.short.amputated")
                     : (
                        conditionSnapshot.id().getPath().startsWith("frostbite")
                           ? Component.translatable("lastaid.frostbite.short." + Mth.clamp(conditionSnapshot.severity(), 0, 4))
                           : (
                              conditionSnapshot.id().getPath().startsWith("fracture_")
                                 ? Component.translatable("lastaid.fracture.short." + Mth.clamp(conditionSnapshot.severity(), 1, 3))
                                 : (
                                    conditionSnapshot.id().getPath().startsWith("dislocation_")
                                       ? Component.translatable("lastaid.dislocation.short")
                                       : (
                                          conditionSnapshot.id().getPath().startsWith("healed_wrong_")
                                             ? Component.translatable("lastaid.fracture.short.wrong")
                                             : Component.translatable("lastaid.severity." + Mth.clamp(conditionSnapshot.severity(), 0, 4))
                                       )
                                 )
                           )
                     )
               );
            ArrayList arrayList3 = new ArrayList();
            arrayList3.add(conditionSnapshot.name().copy().withStyle(ChatFormatting.BOLD));
            arrayList3.addAll(conditionSnapshot.tooltipLines());
            arrayList3.add(
               Component.translatable("lastaid.gui.click_details").withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC})
            );
            StatusRowWidget.Builder builder = StatusRowWidget.builder(conditionSnapshot.name().copy());
            if (conditionSnapshot.icon() != null) {
               builder.texture(conditionSnapshot.icon(), conditionSnapshot.isRisk());
            } else {
               builder.dot(m);
            }

            this.rows
               .add(
                  builder.value(mutableComponent1, conditionSnapshot.severe() ? 16733525 : 16768392)
                     .bar(conditionSnapshot.progress(), 5635925)
                     .tooltip(arrayList3)
                     .onClick(statusRowWidget -> this.openDetail(conditionSnapshot))
                     .build()
               );
         }
      }
   }

   private void addDrugRow(String drug, int doses, int max, ItemStack icon) {
      if (doses <= 0) {
         return;
      }
      int threshold = MedicalConfig.SERVER_SPEC.isLoaded() ? MedicalConfig.SERVER.overdose.overdoseThreshold.get() : 4;
      // green below the overdose threshold, yellow at it, red above
      int color = doses > threshold ? 0xFF5555 : (doses == threshold ? 0xFFFF55 : 0x55FF55);
      StringBuilder blocks = new StringBuilder();
      for (int i = 0; i < Math.min(max, 12); i++) {
         blocks.append(i < doses ? '█' : '░');
      }
      this.rows.add(
         StatusRowWidget.builder(Component.translatable("lastaid.gui.row.drug." + drug))
            .icon(icon)
            .value(Component.literal(doses + "/" + max), color)
            .bar(Math.min(1.0F, doses / (float)max), color)
            .tooltip(List.of(
               Component.literal(blocks.toString()).withStyle(doses > threshold ? ChatFormatting.RED : (doses == threshold ? ChatFormatting.YELLOW : ChatFormatting.GREEN)),
               Component.translatable("lastaid.gui.row.drug.tooltip", threshold).withStyle(ChatFormatting.GRAY)
            ))
            .build()
      );
   }

   private static ItemStack thermometer() {
      Item item = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cold_sweat", "thermometer"));
      return new ItemStack(item == Items.AIR ? Items.COMPASS : item);
   }

   private double vx(double d) {
      return d / this.scale;
   }

   private double vy(double d) {
      return d / this.scale;
   }

   public void render(GuiGraphics guiGraphics, int i, int j, float f) {
      AbstractPlayerDamageModel abstractPlayerDamageModel = this.liveModel();
      BodyStatusSyncPacket bodyStatusSyncPacket = ClientMedicalState.get();
      double d = this.vx(i);
      double e = this.vy(j);
      guiGraphics.fill(0, 0, this.width, this.height, -2013265920);
      guiGraphics.pose().pushPose();
      guiGraphics.pose().scale(this.scale, this.scale, 1.0F);
      guiGraphics.fill(this.left, this.top, this.left + 340, this.top + 206, -535817964);
      MedicalGuiUtil.outline(guiGraphics, this.left, this.top, this.left + 340, this.top + 206, -13355460);
      guiGraphics.fill(this.left + 124, this.top + 20, this.left + 125, this.top + 206 - 8, -14013391);
      guiGraphics.drawString(this.font, this.title, this.left + 8, this.top + 7, -1, false);
      boolean bl = this.isOverClose(d, e);
      guiGraphics.fill(this.left + 340 - 16, this.top + 4, this.left + 340 - 4, this.top + 16, bl ? 1627346261 : 822083583);
      guiGraphics.drawString(this.font, "×", this.left + 340 - 13, this.top + 6, bl ? -34953 : -3355444, false);
      EnumPlayerPart enumPlayerPart = null;

      for (BodyPartWidget bodyPartWidget : this.parts.values()) {
         if (bodyPartWidget.contains(d, e)) {
            enumPlayerPart = bodyPartWidget.part;
         }
      }

      for (BodyPartWidget bodyPartWidget2 : this.parts.values()) {
         AbstractDamageablePart abstractDamageablePart = abstractPlayerDamageModel.getFromEnum(bodyPartWidget2.part);
         int k = this.activeHand == null ? 0 : (this.canTreat(abstractDamageablePart) ? 1 : 2);
         bodyPartWidget2.render(
            guiGraphics,
            abstractDamageablePart,
            BodyPartWidget.conditionsOn(bodyPartWidget2.part, bodyStatusSyncPacket.conditions()),
            bodyPartWidget2.part == enumPlayerPart,
            k
         );
      }

      // head bandage: white strip over the forehead (+ cross for the full bandage, pink with blood)
      if (this.minecraft != null && this.minecraft.player != null) {
         ItemStack headStack = this.minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
         if (com.duckerquacker.lastaid.common.medical.HeadBandage.isBandage(headStack)) {
            BodyPartWidget head = this.parts.get(EnumPlayerPart.HEAD);
            float blood = com.duckerquacker.lastaid.common.medical.HeadBandage.visibleBlood(headStack, this.minecraft.player.level().getGameTime());
            int r = 0xF4, g = (int)(0xF2 - 0x50 * Math.min(1.0F, blood)), b = (int)(0xEA - 0x40 * Math.min(1.0F, blood));
            int gauze = 0xF0000000 | r << 16 | g << 8 | b;
            int bandY = head.y0 + (head.y1 - head.y0) * 3 / 10;
            guiGraphics.fill(head.x0 - 1, bandY, head.x1 + 1, bandY + 4, gauze);
            guiGraphics.fill(head.x0 - 1, bandY + 4, head.x1 + 1, bandY + 5, 0x80000000);
            if (com.duckerquacker.lastaid.common.medical.HeadBandage.tier(headStack) >= 2) {
               int cx = (head.x0 + head.x1) / 2;
               guiGraphics.fill(cx - 2, head.y0 - 1, cx + 2, bandY, gauze);
            }
         }
      }

      // Seen from behind (like the HUD figure and the 3rd person camera): left is left
      int labelY = this.figureY + Math.round(BodyPartLayout.HEIGHT * FIGURE_SCALE) + 3;
      Component leftLabel = Component.translatable("lastaid.gui.side.left");
      Component rightLabel = Component.translatable("lastaid.gui.side.right");
      guiGraphics.drawString(this.font, leftLabel, this.figureX, labelY, -10066330, false);
      guiGraphics.drawString(
         this.font, rightLabel, this.figureX + Math.round(BodyPartLayout.WIDTH * FIGURE_SCALE) - this.font.width(rightLabel), labelY, -10066330, false
      );
      MutableComponent mutableComponent = null;
      if (this.activeHand != null) {
         mutableComponent = Component.translatable(
            "lastaid.gui.apply_hint_pz", new Object[]{this.minecraft.player.getItemInHand(this.activeHand).getHoverName()}
         );
      } else if (abstractPlayerDamageModel.getMorphineTicks() > 0) {
         mutableComponent = Component.translatable(
            "lastaid.gui.morphine_left", new Object[]{StringUtil.formatTickDuration(abstractPlayerDamageModel.getMorphineTicks(), 20.0F)}
         );
      }

      if (mutableComponent != null) {
         guiGraphics.drawString(this.font, mutableComponent, this.left + 8, this.top + 206 - 12, -5592406, false);
      }

      int m = this.rows.size() * 20;
      this.scroll = Mth.clamp(this.scroll, 0.0, Math.max(0, m - this.listHeight));
      guiGraphics.enableScissor(
         Math.round(this.listX * this.scale) - 2,
         Math.round(this.listY * this.scale),
         Math.round((this.listX + this.listWidth + 4) * this.scale),
         Math.round((this.listY + this.listHeight) * this.scale)
      );
      int l = this.listY - (int)this.scroll;
      StatusRowWidget statusRowWidget = null;

      for (StatusRowWidget statusRowWidget1 : this.rows) {
         boolean bl1 = d >= this.listX && d < this.listX + this.listWidth && e >= Math.max(l, this.listY) && e < Math.min(l + 20, this.listY + this.listHeight);
         if (bl1) {
            statusRowWidget = statusRowWidget1;
         }

         if (l + 20 >= this.listY && l <= this.listY + this.listHeight) {
            statusRowWidget1.render(guiGraphics, this.font, this.listX, l, this.listWidth, bl1);
         }

         l += 20;
      }

      guiGraphics.disableScissor();
      if (m > this.listHeight) {
         int n = Math.max(10, this.listHeight * this.listHeight / m);
         int o = this.listY + (int)((this.listHeight - n) * (this.scroll / (m - this.listHeight)));
         guiGraphics.fill(this.listX + this.listWidth + 3, o, this.listX + this.listWidth + 5, o + n, -10066330);
      }

      guiGraphics.pose().popPose();
      super.render(guiGraphics, i, j, f);
      if (enumPlayerPart != null) {
         AbstractDamageablePart abstractDamageablePart1 = abstractPlayerDamageModel.getFromEnum(enumPlayerPart);
         MutableComponent mutableComponent1 = null;
         if (this.activeHand != null) {
            mutableComponent1 = this.canTreat(abstractDamageablePart1)
               ? Component.translatable("lastaid.gui.click_treat").withStyle(ChatFormatting.GREEN)
               : Component.translatable("lastaid.gui.cannot_treat").withStyle(ChatFormatting.DARK_GRAY);
         }

         guiGraphics.renderComponentTooltip(
            this.font,
            ConditionTooltip.build(
               enumPlayerPart, abstractDamageablePart1, BodyPartWidget.conditionsOn(enumPlayerPart, bodyStatusSyncPacket.conditions()), mutableComponent1
            ),
            i,
            j
         );
      } else if (statusRowWidget != null && !statusRowWidget.tooltip().isEmpty()) {
         guiGraphics.renderComponentTooltip(this.font, statusRowWidget.tooltip(), i, j);
      }
   }

   public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
   }

   private boolean isOverClose(double d, double e) {
      return d >= this.left + 340 - 16 && d < this.left + 340 - 4 && e >= this.top + 4 && e < this.top + 16;
   }

   public boolean mouseClicked(double d, double e, int i) {
      double f = this.vx(d);
      double g = this.vy(e);
      if (i == 0 && this.isOverClose(f, g)) {
         this.onClose();
         return true;
      } else {
         for (BodyPartWidget bodyPartWidget : this.parts.values()) {
            if (bodyPartWidget.contains(f, g)) {
               if (i == 0) {
                  this.onPartClicked(bodyPartWidget.part);
               }

               return true;
            }
         }

         if (i == 0 && f >= this.listX && f < this.listX + this.listWidth && g >= this.listY && g < this.listY + this.listHeight) {
            int j = (int)((g - this.listY + this.scroll) / 20.0);
            if (j >= 0 && j < this.rows.size() && this.rows.get(j).clickable()) {
               this.rows.get(j).click();
               return true;
            }
         }

         return super.mouseClicked(d, e, i);
      }
   }

   private boolean holdingSplint() {
      return this.activeHand != null
         && this.minecraft != null
         && this.minecraft.player != null
         && this.minecraft.player.getItemInHand(this.activeHand).is(com.duckerquacker.lastaid.common.medical.MedicalRegistry.SPLINT.get());
   }

   private void onPartClicked(EnumPlayerPart enumPlayerPart) {
      AbstractDamageablePart abstractDamageablePart = this.liveModel().getFromEnum(enumPlayerPart);
      if (this.holdingSplint()) {
         com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb limb = com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.of(enumPlayerPart);
         if (limb != null && this.canTreat(abstractDamageablePart)) {
            com.duckerquacker.lastaid.client.medical.ClientBones.sendSplint(limb, this.activeHand);
            this.onClose();
         }
         return;
      }
      if (this.activeHand != null && this.canTreat(abstractDamageablePart)) {
         this.applyHealing(enumPlayerPart);
      } else {
         List<ConditionSnapshot> list = BodyPartWidget.conditionsOn(enumPlayerPart, ClientMedicalState.get().conditions());
         if (!list.isEmpty()) {
            list.sort((conditionSnapshot, conditionSnapshot1) -> Integer.compare(conditionSnapshot1.severity(), conditionSnapshot.severity()));
            this.openDetail((ConditionSnapshot)list.get(0));
         }
      }
   }

   private void openDetail(ConditionSnapshot conditionSnapshot) {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ConditionDetailScreen(this, conditionSnapshot));
      }
   }

   public boolean mouseScrolled(double e, double f, double g, double d) {
      this.scroll -= d * 12.0;
      return true;
   }

   public boolean keyPressed(int i, int j, int k) {
      if (ClientHooks.SHOW_WOUNDS.matches(i, j)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(i, j, k);
      }
   }

   protected boolean canTreat(AbstractDamageablePart abstractDamageablePart) {
      if (this.holdingSplint()) {
         com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb limb = com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.of(abstractDamageablePart.part);
         return limb != null
            && com.duckerquacker.lastaid.client.medical.ClientBones.fractureType(this.minecraft.player, limb) > 0
            && !com.duckerquacker.lastaid.client.medical.ClientBones.splinted(this.minecraft.player, limb);
      }
      if (this.activeHand != null && this.minecraft != null && this.minecraft.player != null) {
         ItemStack itemStack = this.minecraft.player.getItemInHand(this.activeHand);
         return !(itemStack.getItem() instanceof ItemHealing)
            ? false
            : abstractDamageablePart.activeHealer == null
               && (!CommonUtils.isPartVisuallyFull(abstractDamageablePart)
                  || com.duckerquacker.lastaid.common.medical.bone.Bones.isBleeding(this.minecraft.player, abstractDamageablePart.part))
               && !MedicalHooks.blocksHealing(this.minecraft.player, abstractDamageablePart.part);
      } else {
         return false;
      }
   }

   private static int worldColor(float f) {
      return f < 0.0F ? 8377599 : (f > 30.0F ? 16750933 : 14540253);
   }

   private void applyHealing(EnumPlayerPart enumPlayerPart) {
      if (this.activeHand != null && this.minecraft != null && this.minecraft.player != null) {
         if (ClientEventHandler.selectPendingHealing(enumPlayerPart, this.activeHand)) {
            this.onClose();
         }
      }
   }

   protected void requestRefresh() {
      LastAid.NETWORKING.sendToServer(new MessageClientRequest(MessageClientRequest.RequestType.REQUEST_REFRESH));
   }

   public boolean isPauseScreen() {
      return false;
   }
}
