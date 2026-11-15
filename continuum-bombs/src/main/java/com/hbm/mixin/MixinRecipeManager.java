package com.hbm.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FORK (bombs only): drops HBM's own crafting, smelting and similar recipes before the recipe manager reads them.
 * They only exist for the rest of HBM, which is switched off here, and they would otherwise show up by name in
 * JEI's crafting view, the recipe book and the {@code /recipe} command. The bomb recipes are in the
 * {@code hbmbombs} namespace and stay. Machine recipe types of HBM (assembly, shredder, ...) are left alone,
 * because the mod's own recipe loaders may still read them.
 */
@Mixin(RecipeManager.class)
public class MixinRecipeManager {

    private static final Set<String> VIEWER_TYPES = Set.of(
            "minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:smelting", "minecraft:blasting",
            "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing",
            "minecraft:smithing_transform", "minecraft:smithing_trim", "hbm:component_shaped", "hbm:component_shapeless");

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void hbm$dropHbmViewerRecipes(Map<?, JsonElement> recipes, net.minecraft.server.packs.resources.ResourceManager manager,
            net.minecraft.util.profiling.ProfilerFiller profiler, CallbackInfo ci) {
        recipes.entrySet().removeIf(entry -> {
            if (!entry.getKey().toString().startsWith("hbm:")) return false;
            JsonElement json = entry.getValue();
            if (!json.isJsonObject()) return false;
            JsonObject object = (JsonObject) json;
            return object.has("type") && VIEWER_TYPES.contains(object.get("type").getAsString());
        });
    }
}
