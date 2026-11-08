package com.hbm.handler.jei;

//? if jei {

import dev.golem.core.jei.JeiViewer;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import java.util.List;
import java.util.function.Supplier;

/**
 * The loader-agnostic body of the mod's JEI plugin. Each loader's thin {@code @JeiPlugin} entrypoint delegates its
 * phases here, and each phase hands HBM's recipe-viewer declarations ({@link HbmRecipeViewer},
 * {@link HbmViewerSubtypes}) to Golem Core's {@link JeiViewer}; the REI plugins ({@code com.hbm.handler.rei}) feed the
 * same declarations to REI. Only the JEI fluid-ingredient read-back below is JEI's alone.
 */
public final class HbmJeiPluginCommon {

    private HbmJeiPluginCommon() {}

    /** The names of the HBM fluid ingredients JEI lists; set once its runtime exists, read by the harness. */
    public static volatile Supplier<List<String>> fluidNames = null;

    public static void registerItemSubtypes(ISubtypeRegistration reg) {
        JeiViewer.subtypes(reg, HbmViewerSubtypes::register);
    }

    public static void registerExtraIngredients(IExtraIngredientRegistration reg) {
        JeiViewer.ingredients(reg, HbmRecipeViewer.INSTANCE);
    }

    public static void registerCategories(IRecipeCategoryRegistration reg) {
        JeiViewer.categories(reg, HbmRecipeViewer.INSTANCE);
    }

    public static void registerRecipes(IRecipeRegistration reg) {
        JeiViewer.recipes(reg, HbmRecipeViewer.INSTANCE);
    }

    public static void registerGuiHandlers(IGuiHandlerRegistration reg) {
        JeiViewer.clickAreas(reg, HbmRecipeViewer.INSTANCE);
    }

    public static void registerRecipeTransferHandlers(IRecipeTransferRegistration reg) {
        JeiViewer.transfers(reg, HbmRecipeViewer.INSTANCE);
    }

    public static void registerCatalysts(IRecipeCatalystRegistration reg) {
        JeiViewer.stations(reg, HbmRecipeViewer.INSTANCE);
    }

    /**
     * Runs once JEI's runtime exists: HBM's runtime categories (the arc furnace's furnace pass, {@link HbmRecipeViewer#runtime}),
     * then the JEI-only read-back of HBM fluids as JEI fluid ingredients. JEI lists every
     * registered source fluid itself; this only reads the list back, for the log and the harness.
     */
    public static void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        JeiViewer.runtime(runtime, HbmRecipeViewer.INSTANCE);
        java.util.List<String> fluids = hbmFluidIngredients(runtime);
        fluidNames = () -> hbmFluidIngredients(runtime);
        com.hbm.Constants.LOG.info("[JEI] {} HBM fluid(s) listed as fluid ingredients", fluids.size());
    }

    /**
     * FORK (bombs only): hides every HBM item and fluid from JEI's ingredient list except the bombs and their parts
     * (the same list as the creative tab, {@link com.hbm.creativetabs.ModTabs#isNukeItem}). The rest of HBM is switched
     * off in this fork, so listing it only clutters JEI.
     */
    public static void hideNonBombIngredients(mezz.jei.api.runtime.IJeiRuntime runtime) {
        try {
            var manager = runtime.getIngredientManager();
            java.util.List<net.minecraft.world.item.ItemStack> hiddenItems = new java.util.ArrayList<>();
            for (net.minecraft.world.item.ItemStack stack : manager.getAllIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)) {
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id != null && com.hbm.Constants.MOD_ID.equals(id.getNamespace())
                        && !com.hbm.creativetabs.ModTabs.isNukeItem(stack.getItem())) {
                    hiddenItems.add(stack);
                }
            }
            manager.removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hiddenItems);
            int hiddenFluids = hideHbmFluids(runtime, runtime.getJeiHelpers().getPlatformFluidHelper());
            com.hbm.Constants.LOG.info("[JEI] hid {} non-bomb HBM item(s) and {} HBM fluid(s)", hiddenItems.size(), hiddenFluids);
        } catch (RuntimeException e) {
            com.hbm.Constants.LOG.warn("[JEI] could not hide non-bomb HBM ingredients: {}", e.toString());
        }
    }

    private static <T> int hideHbmFluids(mezz.jei.api.runtime.IJeiRuntime runtime, mezz.jei.api.helpers.IPlatformFluidHelper<T> fluids) {
        mezz.jei.api.ingredients.IIngredientTypeWithSubtypes<net.minecraft.world.level.material.Fluid, T> type =
                fluids.getFluidIngredientType();
        var manager = runtime.getIngredientManager();
        java.util.List<T> hidden = new java.util.ArrayList<>();
        for (T ingredient : manager.getAllIngredients(type)) {
            net.minecraft.world.level.material.Fluid fluid = type.getBase(ingredient);
            var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
            if (dev.golem.core.fluid.TokenFluids.isToken(fluid)
                    || (id != null && com.hbm.Constants.MOD_ID.equals(id.getNamespace()))) {
                hidden.add(ingredient);
            }
        }
        manager.removeIngredientsAtRuntime(type, hidden);
        return hidden.size();
    }

    /** The display names of the JEI fluid ingredients that are HBM fluids. */
    private static java.util.List<String> hbmFluidIngredients(mezz.jei.api.runtime.IJeiRuntime runtime) {
        return hbmFluidIngredients(runtime, runtime.getJeiHelpers().getPlatformFluidHelper());
    }

    private static <T> java.util.List<String> hbmFluidIngredients(mezz.jei.api.runtime.IJeiRuntime runtime,
            mezz.jei.api.helpers.IPlatformFluidHelper<T> fluids) {
        mezz.jei.api.ingredients.IIngredientTypeWithSubtypes<net.minecraft.world.level.material.Fluid, T> type =
                fluids.getFluidIngredientType();
        mezz.jei.api.ingredients.IIngredientHelper<T> helper = runtime.getIngredientManager().getIngredientHelper(type);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (T ingredient : runtime.getIngredientManager().getAllIngredients(type)) {
            if (dev.golem.core.fluid.TokenFluids.isToken(type.getBase(ingredient))) names.add(helper.getDisplayName(ingredient));
        }
        return names;
    }
}
//?}
