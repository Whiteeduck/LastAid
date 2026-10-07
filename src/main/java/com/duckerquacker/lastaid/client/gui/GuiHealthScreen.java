package com.duckerquacker.lastaid.client.gui;

import com.duckerquacker.lastaid.api.damagesystem.AbstractPlayerDamageModel;
import com.duckerquacker.lastaid.client.gui.medical.BodyStatusScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GuiHealthScreen extends BodyStatusScreen {
   public static final int xSize = 256;
   public static final int ySize = 137;
   public static final ItemStack BED_ITEMSTACK = new ItemStack(Items.RED_BED);
   public static GuiHealthScreen INSTANCE;
   public static boolean isOpen = false;
   private static int funTicks;

   public GuiHealthScreen(AbstractPlayerDamageModel abstractPlayerDamageModel) {
      this(abstractPlayerDamageModel, null);
   }

   public GuiHealthScreen(AbstractPlayerDamageModel abstractPlayerDamageModel, InteractionHand interactionHand) {
      super(abstractPlayerDamageModel, interactionHand);
   }

   public static void tickFun() {
      funTicks++;
      if (funTicks > 500) {
         funTicks = 0;
      }
   }

   @Override
   protected void init() {
      isOpen = true;
      super.init();
   }

   public void onClose() {
      isOpen = false;
      super.onClose();
   }
}
