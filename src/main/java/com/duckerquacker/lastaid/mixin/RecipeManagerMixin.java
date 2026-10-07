package com.duckerquacker.lastaid.mixin;

import com.google.gson.JsonElement;
import com.duckerquacker.lastaid.common.CraftingSettings;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void lastaid$adjustResults(Map<ResourceLocation, JsonElement> jsons,
                                        ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        for (var holder : ((RecipeManager) (Object) this).getRecipes()) {
            CraftingSettings.adjust(holder.id().getNamespace(), holder.id().getPath(), holder.value());
        }
    }
}
