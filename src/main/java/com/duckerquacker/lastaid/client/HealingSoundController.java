package com.duckerquacker.lastaid.client;

import com.duckerquacker.lastaid.FirstAidConfig;
import com.duckerquacker.lastaid.api.healing.ItemHealing;
import com.duckerquacker.lastaid.api.medicine.ItemMedicine;
import com.duckerquacker.lastaid.common.RegistryObjects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class HealingSoundController {
   @Nullable
   private static ResourceLocation activeMedicineStartSound;
   @Nullable
   private static ItemStack activeMedicineStack;
   @Nullable
   private static HealingSoundController.ItemUseSound activeMedicineLoopSound;
   @Nullable
   private static HealingSoundController.ItemUseSound activeHealingSound;
   @Nullable
   private static SoundInstance activeRescueSound;
   @Nullable
   private static ResourceLocation activeRescueSoundLocation;

   private HealingSoundController() {
   }

   public static void tick(Minecraft minecraft) {
      SoundManager soundManager = minecraft.getSoundManager();
      if (!(Boolean)FirstAidConfig.CLIENT.enableSounds.get()) {
         stopMedicineSounds(soundManager);
         stopHealingSound(soundManager);
         stopRescueInteractionSound(soundManager);
      } else {
         LocalPlayer player = minecraft.player;
         if (player != null && player.isAlive()) {
            updateMedicineSounds(soundManager, player);
         } else {
            stopMedicineSounds(soundManager);
         }

         if (player != null && player.isAlive()) {
            ItemStack useStack = player.getUseItem();
            if (player.isUsingItem()
               && !ItemMedicine.isClientReuseBlocked(player)
               && useStack.getItem() instanceof ItemHealing itemHealing
               && itemHealing.getApplySoundMode(useStack) == ItemHealing.ApplySoundMode.WHILE_USING
               && itemHealing.getApplySoundEvent(useStack) != null) {
               if (activeHealingSound == null || activeHealingSound.isStopped() || !activeHealingSound.matches(player, useStack)) {
                  stopHealingSound(soundManager);
                  activeHealingSound = new HealingSoundController.ItemUseSound(player, useStack.copyWithCount(1), itemHealing.getApplySoundEvent(useStack));
                  soundManager.play(activeHealingSound);
               }
            } else {
               stopHealingSound(soundManager);
            }
         } else {
            stopHealingSound(soundManager);
         }
      }
   }

   public static void clear() {
      SoundManager soundManager = Minecraft.getInstance().getSoundManager();
      stopMedicineSounds(soundManager);
      stopHealingSound(soundManager);
      stopRescueInteractionSound(soundManager);
   }

   public static void playHealingApplySound(ItemStack stack) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      if ((Boolean)FirstAidConfig.CLIENT.enableSounds.get() && player != null && stack.getItem() instanceof ItemHealing itemHealing) {
         SoundEvent soundEvent = itemHealing.getApplySoundEvent(stack);
         if (soundEvent != null && itemHealing.getApplySoundMode(stack) == ItemHealing.ApplySoundMode.ON_COMPLETE) {
            player.playSound(soundEvent, 1.0F, 1.0F);
         }
      }
   }

   public static void playRescueInteractionSound(ItemStack stack) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      SoundManager soundManager = minecraft.getSoundManager();
      if (!(Boolean)FirstAidConfig.CLIENT.enableSounds.get() || player == null) {
         return;
      }

      SoundEvent soundEvent = stack.is((Item)RegistryObjects.DEFIBRILLATOR.get())
               ? (SoundEvent)RegistryObjects.DEFIBRILLATOR_USE.value()
         : stack.is((Item)RegistryObjects.ADRENALINE_INJECTOR.get())
         ? (SoundEvent)RegistryObjects.ADRENALINE_INJECTOR_USE.value()
         : (SoundEvent)RegistryObjects.BANDAGE_USE.value();
      // Tracked instance so releasing the hold can hard-stop mid-clip.
      stopRescueInteractionSound(soundManager);
      SimpleSoundInstance instance = SimpleSoundInstance.forUI(soundEvent, 1.0F, 1.0F);
      activeRescueSound = instance;
      activeRescueSoundLocation = soundEvent.getLocation();
      soundManager.play(instance);
   }

   public static void stopRescueInteractionSound() {
      stopRescueInteractionSound(Minecraft.getInstance().getSoundManager());
   }

   private static void stopRescueInteractionSound(SoundManager soundManager) {
      if (activeRescueSound != null) {
         soundManager.stop(activeRescueSound);
         activeRescueSound = null;
      }
      if (activeRescueSoundLocation != null) {
         soundManager.stop(activeRescueSoundLocation, SoundSource.PLAYERS);
         activeRescueSoundLocation = null;
      }
   }

   private static void updateMedicineSounds(SoundManager soundManager, LocalPlayer player) {
      ItemStack useStack = player.getUseItem();
      if (player.isUsingItem() && !ItemMedicine.isClientReuseBlocked(player) && useStack.getItem() instanceof ItemMedicine itemMedicine) {
         if (!matchesCurrentMedicine(player, useStack)) {
            stopMedicineSounds(soundManager);
            activeMedicineStack = useStack.copyWithCount(1);
            SoundEvent startSound = itemMedicine.getUseStartSound(useStack);
            if (startSound != null) {
               activeMedicineStartSound = startSound.getLocation();
               player.playSound(startSound, 1.0F, 1.0F);
            }
         }

         SoundEvent loopSound = itemMedicine.getUseLoopSound(useStack);
         if (loopSound != null) {
            if (activeMedicineLoopSound == null || !activeMedicineLoopSound.matches(player, useStack)) {
               stopMedicineLoopSound(soundManager);
               activeMedicineLoopSound = new HealingSoundController.ItemUseSound(player, useStack.copyWithCount(1), loopSound);
               soundManager.play(activeMedicineLoopSound);
            }
         } else {
            stopMedicineLoopSound(soundManager);
         }
      } else {
         stopMedicineSounds(soundManager);
      }
   }

   private static boolean matchesCurrentMedicine(LocalPlayer player, ItemStack stack) {
      return activeMedicineStack != null && player.isUsingItem() && ItemStack.isSameItemSameComponents(activeMedicineStack, stack);
   }

   private static void stopMedicineSounds(SoundManager soundManager) {
      stopMedicineLoopSound(soundManager);
      if (activeMedicineStartSound != null) {
         soundManager.stop(activeMedicineStartSound, SoundSource.PLAYERS);
         activeMedicineStartSound = null;
      }

      activeMedicineStack = null;
   }

   private static void stopMedicineLoopSound(SoundManager soundManager) {
      if (activeMedicineLoopSound != null) {
         activeMedicineLoopSound.stop();
         soundManager.stop(activeMedicineLoopSound);
         // Also stop by id: catches an instance that was replaced before its channel existed (the old "endless loop")
         soundManager.stop(activeMedicineLoopSound.getLocation(), SoundSource.PLAYERS);
         activeMedicineLoopSound = null;
      }
   }

   private static void stopHealingSound(SoundManager soundManager) {
      if (activeHealingSound != null) {
         activeHealingSound.stop();
         soundManager.stop(activeHealingSound);
         soundManager.stop(activeHealingSound.getLocation(), SoundSource.PLAYERS);
         activeHealingSound = null;
      }
   }

   private static final class ItemUseSound implements TickableSoundInstance {
      private final LocalPlayer player;
      private final ItemStack stack;
      private final ResourceLocation location;
      private Sound sound = SoundManager.EMPTY_SOUND;
      private boolean stopped;
      /** Hard safety cap: a "while using" loop can never outlive the item's use duration (+1 s). */
      private final int maxTicks;
      private int age;

      private ItemUseSound(LocalPlayer player, ItemStack stack, SoundEvent event) {
         this.player = player;
         this.stack = stack;
         this.location = event.getLocation();
         int duration;
         try {
            duration = stack.getUseDuration(player);
         } catch (RuntimeException e) {
            duration = 0;
         }
         this.maxTicks = duration > 0 ? duration + 20 : 20 * 60;
      }

      private void stop() {
         this.stopped = true;
      }

      private boolean matches(LocalPlayer player, ItemStack stack) {
         return this.player == player && ItemStack.isSameItemSameComponents(this.stack, stack);
      }

      public boolean isStopped() {
         return this.stopped || !this.player.isAlive() || !this.matchesCurrentUse();
      }

      public void tick() {
         if (++this.age > this.maxTicks || !this.matchesCurrentUse()) {
            this.stopped = true;
         }
      }

      private boolean matchesCurrentUse() {
         return this.player.isAlive()
            && this.player.isUsingItem()
            && !ItemMedicine.isClientReuseBlocked(this.player)
            && ItemStack.isSameItemSameComponents(this.stack, this.player.getUseItem());
      }

      @Nonnull
      public ResourceLocation getLocation() {
         return this.location;
      }

      @Nullable
      public WeighedSoundEvents resolve(@Nonnull SoundManager soundManager) {
         WeighedSoundEvents events = soundManager.getSoundEvent(this.location);
         if (events != null) {
            this.sound = events.getSound(SoundInstance.createUnseededRandom());
         }

         return events;
      }

      @Nonnull
      public Sound getSound() {
         return this.sound;
      }

      @Nonnull
      public SoundSource getSource() {
         return SoundSource.PLAYERS;
      }

      public boolean isLooping() {
         return true;
      }

      public boolean isRelative() {
         return false;
      }

      public int getDelay() {
         return 0;
      }

      public float getVolume() {
         return 0.85F;
      }

      public float getPitch() {
         return 1.0F;
      }

      public double getX() {
         return this.player.getX();
      }

      public double getY() {
         return this.player.getY();
      }

      public double getZ() {
         return this.player.getZ();
      }

      @Nonnull
      public Attenuation getAttenuation() {
         return Attenuation.NONE;
      }
   }
}
