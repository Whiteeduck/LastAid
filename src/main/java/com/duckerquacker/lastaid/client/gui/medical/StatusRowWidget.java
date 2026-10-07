package com.duckerquacker.lastaid.client.gui.medical;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class StatusRowWidget {
   public static final int HEIGHT = 20;
   private final Component label;
   private final Component value;
   private final int valueColor;
   private final float bar;
   private final int barColor;
   private final ItemStack icon;
   private final ResourceLocation sprite;
   private final int dotColor;
   private final ResourceLocation texture;
   private final List<ResourceLocation> badges;
   private final boolean highlight;
   private final List<Component> tooltip;
   private final Consumer<StatusRowWidget> onClick;

   private StatusRowWidget(StatusRowWidget.Builder builder) {
      this.label = builder.label;
      this.value = builder.value;
      this.valueColor = builder.valueColor;
      this.bar = builder.bar;
      this.barColor = builder.barColor;
      this.icon = builder.icon;
      this.sprite = builder.sprite;
      this.dotColor = builder.dotColor;
      this.texture = builder.texture;
      this.badges = builder.badges;
      this.highlight = builder.highlight;
      this.tooltip = builder.tooltip;
      this.onClick = builder.onClick;
   }

   public static StatusRowWidget.Builder builder(Component component) {
      return new StatusRowWidget.Builder(component);
   }

   public List<Component> tooltip() {
      return this.tooltip;
   }

   public void click() {
      if (this.onClick != null) {
         this.onClick.accept(this);
      }
   }

   public boolean clickable() {
      return this.onClick != null;
   }

   public void render(GuiGraphics guiGraphics, Font font, int i, int j, int k, boolean bl) {
      if (this.highlight) {
         guiGraphics.fill(i - 2, j - 1, i + k + 2, j + 20 - 1, 587202559);
      }

      if (bl) {
         guiGraphics.fill(i - 2, j - 1, i + k + 2, j + 20 - 1, 822083583);
      }

      int l = i + 14;
      if (this.icon != null && !this.icon.isEmpty()) {
         guiGraphics.pose().pushPose();
         guiGraphics.pose().translate(i, j + 1, 0.0F);
         guiGraphics.pose().scale(0.75F, 0.75F, 1.0F);
         guiGraphics.renderItem(this.icon, 0, 0);
         guiGraphics.pose().popPose();
      } else if (this.texture != null) {
         float f = this.dotColor == -2 ? 0.4F + 0.6F * MedicalGuiUtil.pulse(700L) : 1.0F;
         MedicalGuiUtil.drawIcon(guiGraphics, this.texture, i, j + 1, 11, f);
      } else if (this.sprite != null) {
         guiGraphics.blitSprite(this.sprite, i + 1, j + 2, 9, 9);
      } else if (this.dotColor != -1) {
         float g = 0.4F + 0.6F * MedicalGuiUtil.pulse(1000L);
         MedicalGuiUtil.fillCircle(guiGraphics, i + 5, j + 6, 3, MedicalGuiUtil.withAlpha(this.dotColor, g));
      }

      int n = this.value == null ? 0 : font.width(this.value);
      int m = i + k - n - 2;

      for (ResourceLocation resourceLocation : this.badges) {
         m -= 10;
         MedicalGuiUtil.drawIcon(guiGraphics, resourceLocation, m, j + 1, 9, 1.0F);
      }

      int o = n + this.badges.size() * 10;
      String string = font.plainSubstrByWidth(this.label.getString(), k - 14 - o - 4);
      guiGraphics.drawString(font, Component.literal(string).withStyle(this.label.getStyle()), l, j + 2, -2039584, false);
      if (this.value != null) {
         guiGraphics.drawString(font, this.value, i + k - n, j + 2, 0xFF000000 | this.valueColor, false);
      }

      if (this.bar >= 0.0F) {
         MedicalGuiUtil.bar(guiGraphics, l, j + 12, k - 14, 3, this.bar, this.barColor);
      }
   }

   public static final class Builder {
      private final Component label;
      private Component value;
      private int valueColor = 16777215;
      private float bar = -1.0F;
      private int barColor = 16777215;
      private ItemStack icon;
      private ResourceLocation sprite;
      private int dotColor = -1;
      private ResourceLocation texture;
      private List<ResourceLocation> badges = List.of();
      private boolean highlight;
      private List<Component> tooltip = List.of();
      private Consumer<StatusRowWidget> onClick;

      private Builder(Component component) {
         this.label = component;
      }

      public StatusRowWidget.Builder value(Component component, int i) {
         this.value = component;
         this.valueColor = i;
         return this;
      }

      public StatusRowWidget.Builder bar(float f, int i) {
         this.bar = f;
         this.barColor = i;
         return this;
      }

      public StatusRowWidget.Builder icon(ItemStack itemStack) {
         this.icon = itemStack;
         return this;
      }

      public StatusRowWidget.Builder sprite(ResourceLocation resourceLocation) {
         this.sprite = resourceLocation;
         return this;
      }

      public StatusRowWidget.Builder texture(ResourceLocation resourceLocation, boolean bl) {
         this.texture = resourceLocation;
         if (bl) {
            this.dotColor = -2;
         }

         return this;
      }

      public StatusRowWidget.Builder badges(List<ResourceLocation> list) {
         this.badges = list;
         return this;
      }

      public StatusRowWidget.Builder highlight(boolean bl) {
         this.highlight = bl;
         return this;
      }

      public StatusRowWidget.Builder dot(int i) {
         this.dotColor = i;
         return this;
      }

      public StatusRowWidget.Builder tooltip(List<Component> list) {
         this.tooltip = list;
         return this;
      }

      public StatusRowWidget.Builder onClick(Consumer<StatusRowWidget> consumer) {
         this.onClick = consumer;
         return this;
      }

      public StatusRowWidget build() {
         return new StatusRowWidget(this);
      }
   }
}
