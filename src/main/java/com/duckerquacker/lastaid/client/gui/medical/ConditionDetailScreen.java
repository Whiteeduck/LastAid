package com.duckerquacker.lastaid.client.gui.medical;

import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import com.duckerquacker.lastaid.common.medical.ClientMedicalState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public class ConditionDetailScreen extends Screen {
   private static final int WIDTH = 260;
   private static final int HEIGHT = 200;
   private final Screen parent;
   private final ResourceLocation conditionId;
   private ConditionSnapshot snapshot;
   private int left;
   private int top;
   private double scroll;
   private List<FormattedCharSequence> wrapped = List.of();
   private int buttonRows;
   private int lastActions = -1;

   public ConditionDetailScreen(Screen screen, ConditionSnapshot conditionSnapshot) {
      super(conditionSnapshot.name());
      this.parent = screen;
      this.conditionId = conditionSnapshot.id();
      this.snapshot = conditionSnapshot;
   }

   protected void init() {
      this.left = (this.width - 260) / 2;
      this.top = (this.height - 200) / 2;
      this.rewrap();
      this.buttonRows = 0;
      // wound: treatment buttons (extract arrow, tourniquet, suture, alcohol, take the dressing off)
      if (this.conditionId.getNamespace().equals("lastaid")
         && this.conditionId.getPath().startsWith(com.duckerquacker.lastaid.common.medical.wounds.Wounds.SNAPSHOT_PREFIX)) {
         int zone = this.snapshot.clientFlags() & 15;
         int mask = this.snapshot.clientFlags() >> 4;
         this.lastActions = mask;
         int x = this.left + 6;
         int row = 0;
         for (int action : com.duckerquacker.lastaid.common.medical.wounds.Wounds.ACTIONS) {
            if ((mask & action) == 0) {
               continue;
            }
            Component label = Component.translatable("lastaid.wound.action." + action);
            int w = this.font.width(label) + 12;
            if (x + w > this.left + 254) {
               row++;
               x = this.left + 6;
            }
            final int act = action;
            this.addRenderableWidget(
               net.minecraft.client.gui.components.Button.builder(label, button -> com.duckerquacker.lastaid.client.medical.ClientBones.sendWoundAction(zone, act))
                  .bounds(x, this.top + 200 - 26 - row * 20, w, 18)
                  .build()
            );
            x += w + 4;
            this.buttonRows = row + 1;
         }
      }
      // dislocation: reduce it right here
      if (this.conditionId.getNamespace().equals("lastaid")
         && this.conditionId.getPath().startsWith(com.duckerquacker.lastaid.common.medical.bone.DislocationCondition.SNAPSHOT_PREFIX)) {
         int limbIndex = this.snapshot.clientFlags();
         if (limbIndex >= 0 && limbIndex < com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.VALUES.length) {
            com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb limb = com.duckerquacker.lastaid.common.medical.cold.Frostbite.Limb.VALUES[limbIndex];
            this.addRenderableWidget(
               net.minecraft.client.gui.components.Button.builder(Component.translatable("lastaid.dislocation.button"), button -> {
                     com.duckerquacker.lastaid.client.medical.ClientBones.sendReduce(limb);
                     if (this.minecraft != null) {
                        this.minecraft.setScreen(null);
                     }
                  })
                  .bounds(this.left + 260 - 96, this.top + 200 - 26, 88, 18)
                  .build()
            );
         }
      }
   }

   private void rewrap() {
      ArrayList arrayList = new ArrayList();

      for (Component component : this.snapshot.detailLines()) {
         if (component.getString().isEmpty()) {
            arrayList.add(FormattedCharSequence.EMPTY);
         } else {
            arrayList.addAll(this.font.split(component, 240));
         }
      }

      this.wrapped = arrayList;
   }

   public void tick() {
      ConditionSnapshot conditionSnapshot = ClientMedicalState.find(this.conditionId);
      if (conditionSnapshot == null) {
         this.onClose();
      } else {
         if (conditionSnapshot != this.snapshot) {
            this.snapshot = conditionSnapshot;
            this.rewrap();
            if (this.lastActions >= 0 && conditionSnapshot.clientFlags() >> 4 != this.lastActions) {
               this.rebuildWidgets();
            }
         }
      }
   }

   private int contentTop() {
      return this.top + 36;
   }

   private int contentHeight() {
      // leave room for the action button (dislocation: "Reduce")
      if (this.buttonRows > 1) {
         return 156 - this.buttonRows * 20 - 6;
      }
      return this.children().isEmpty() ? 156 : 130;
   }

   public void render(GuiGraphics guiGraphics, int i, int j, float f) {
      guiGraphics.fill(0, 0, this.width, this.height, 1711276032);
      guiGraphics.fill(this.left, this.top, this.left + 260, this.top + 200, -300607974);
      MedicalGuiUtil.outline(guiGraphics, this.left, this.top, this.left + 260, this.top + 200, -12960443);
      int k = MedicalGuiUtil.conditionColor(this.snapshot);
      guiGraphics.fill(this.left, this.top, this.left + 3, this.top + 200, 0xFF000000 | k);
      if (this.snapshot.icon() != null) {
         MedicalGuiUtil.drawIcon(guiGraphics, this.snapshot.icon(), this.left + 6, this.top + 4, 12, 1.0F);
      } else {
         MedicalGuiUtil.fillCircle(guiGraphics, this.left + 12, this.top + 10, 3, 0xFF000000 | k);
      }

      MutableComponent mutableComponent = this.snapshot.name().copy().withStyle(ChatFormatting.BOLD);
      guiGraphics.drawString(this.font, mutableComponent, this.left + 20, this.top + 6, -1, false);
      if (this.snapshot.severe()) {
         guiGraphics.drawString(
            this.font, Component.translatable("lastaid.gui.severe_tag"), this.left + 24 + this.font.width(mutableComponent), this.top + 6, -5636096, false
         );
      }

      if (!this.snapshot.tooltipLines().isEmpty()) {
         guiGraphics.drawString(this.font, this.snapshot.tooltipLines().get(0), this.left + 20, this.top + 17, -4473925, false);
      }

      if (this.snapshot.progress() >= 0.0F) {
         MedicalGuiUtil.bar(guiGraphics, this.left + 20, this.top + 28, 220, 3, this.snapshot.progress(), 5635925);
      }

      boolean bl = this.isOverClose(i, j);
      guiGraphics.drawString(this.font, "×", this.left + 260 - 11, this.top + 4, bl ? -43691 : -5592406, false);
      int l = this.contentTop();
      int m = this.contentHeight();
      int n = this.wrapped.size() * (9 + 1);
      this.scroll = Mth.clamp(this.scroll, 0.0, Math.max(0, n - m));
      guiGraphics.enableScissor(this.left + 4, l, this.left + 260 - 4, l + m);
      int o = l - (int)this.scroll;

      for (FormattedCharSequence formattedCharSequence : this.wrapped) {
         guiGraphics.drawString(this.font, formattedCharSequence, this.left + 10, o, -2236963, false);
         o += 9 + 1;
      }

      guiGraphics.disableScissor();
      if (n > m) {
         int p = Math.max(10, m * m / n);
         int q = l + (int)((m - p) * (this.scroll / (n - m)));
         guiGraphics.fill(this.left + 260 - 5, q, this.left + 260 - 3, q + p, -8947849);
      }

      guiGraphics.drawCenteredString(
         this.font,
         Component.translatable("lastaid.gui.detail_hint").withStyle(style -> style.withColor(TextColor.fromRgb(7829367))),
         this.left + 130,
         this.top + 200 - 10,
         -8947849
      );
      super.render(guiGraphics, i, j, f);
   }

   private boolean isOverClose(double d, double e) {
      return d >= this.left + 260 - 14 && d < this.left + 260 - 2 && e >= this.top + 2 && e < this.top + 14;
   }

   public boolean mouseClicked(double d, double e, int i) {
      if (super.mouseClicked(d, e, i)) {
         return true;
      }
      if (this.isOverClose(d, e)) {
         this.onClose();
         return true;
      } else {
         return super.mouseClicked(d, e, i);
      }
   }

   public boolean mouseScrolled(double e, double f, double g, double d) {
      this.scroll -= d * 10.0;
      return true;
   }

   public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }
}
