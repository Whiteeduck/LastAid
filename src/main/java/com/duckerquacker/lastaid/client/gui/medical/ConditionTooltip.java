package com.duckerquacker.lastaid.client.gui.medical;

import com.duckerquacker.lastaid.api.damagesystem.AbstractDamageablePart;
import com.duckerquacker.lastaid.api.enums.EnumPlayerPart;
import com.duckerquacker.lastaid.api.medical.ConditionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

public final class ConditionTooltip {
   private static final Component SEPARATOR = Component.literal("─────────────").withStyle(ChatFormatting.DARK_GRAY);

   private ConditionTooltip() {
   }

   public static Component partName(EnumPlayerPart enumPlayerPart) {
      return Component.translatable("lastaid.gui." + enumPlayerPart.toString().toLowerCase(Locale.ROOT));
   }

   public static List<Component> build(
      EnumPlayerPart enumPlayerPart, AbstractDamageablePart abstractDamageablePart, List<ConditionSnapshot> list, Component component
   ) {
      ArrayList arrayList = new ArrayList();
      arrayList.add(
         Component.literal(partName(enumPlayerPart).getString().toUpperCase(Locale.ROOT))
            .withStyle(new ChatFormatting[]{ChatFormatting.BOLD, ChatFormatting.WHITE})
      );
      int i = MedicalGuiUtil.healthColor(abstractDamageablePart.currentHealth, abstractDamageablePart.getMaxHealth());
      arrayList.add(
         Component.translatable(
               "lastaid.gui.hp",
               new Object[]{
                  String.format(Locale.ROOT, "%.1f", abstractDamageablePart.currentHealth),
                  String.format(Locale.ROOT, "%.1f", (float)abstractDamageablePart.getMaxHealth())
               }
            )
            .withStyle(style -> style.withColor(TextColor.fromRgb(i)))
      );
      if (abstractDamageablePart.activeHealer != null) {
         arrayList.add(
            Component.translatable("lastaid.gui.being_treated", new Object[]{abstractDamageablePart.activeHealer.stack.getHoverName()})
               .withStyle(ChatFormatting.AQUA)
         );
      }

      arrayList.add(SEPARATOR);
      if (list.isEmpty()) {
         arrayList.add(Component.translatable("lastaid.gui.no_conditions").withStyle(ChatFormatting.GRAY));
      } else {
         arrayList.add(Component.translatable("lastaid.gui.conditions").withStyle(ChatFormatting.GRAY));

         for (ConditionSnapshot conditionSnapshot : list) {
            int j = MedicalGuiUtil.conditionColor(conditionSnapshot);
            MutableComponent mutableComponent = Component.literal("  ● ")
               .withStyle(style -> style.withColor(TextColor.fromRgb(j)))
               .append(conditionSnapshot.name().copy().withStyle(ChatFormatting.WHITE));
            if (conditionSnapshot.severe()) {
               mutableComponent.append(Component.literal(" ")).append(Component.translatable("lastaid.gui.severe_tag").withStyle(ChatFormatting.DARK_RED));
            }

            arrayList.add(mutableComponent);

            for (Component component1 : conditionSnapshot.tooltipLines()) {
               arrayList.add(Component.literal("     ").append(component1));
            }
         }

         arrayList.add(SEPARATOR);
         arrayList.add(Component.translatable("lastaid.gui.click_details").withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC}));
      }

      if (component != null) {
         arrayList.add(component);
      }

      return arrayList;
   }
}
