package com.duckerquacker.lastaid.shim.forge.common.util;

public interface INBTSerializable<T> {

    T serializeNBT();

    void deserializeNBT(T nbt);
}
