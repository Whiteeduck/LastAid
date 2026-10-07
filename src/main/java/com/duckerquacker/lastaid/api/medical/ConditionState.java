package com.duckerquacker.lastaid.api.medical;

import net.minecraft.nbt.CompoundTag;

public abstract class ConditionState {
   private boolean dirty = true;

   public abstract CompoundTag save();

   public abstract void load(CompoundTag compoundTag);

   public void markDirty() {
      this.dirty = true;
   }

   public boolean consumeDirty() {
      boolean bl = this.dirty;
      this.dirty = false;
      return bl;
   }
}
