package com.hbm.creativetabs;

import com.hbm.items.weapon.sedna.ItemGunBaseNT;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.hbm.Constants;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * Registers the HBM creative tabs (one per {@link HbmTab}) and tracks their contents.
 *
 * <p>Tabs are built with the vanilla {@link CreativeModeTab} builder (cross-loader). Contents can't
 * be filled from common because {@code CreativeModeTab.Output} is protected, so {@link #CONTENTS}
 * records each tab's registrations; {@link #displayStacks} resolves the final listing for each loader's public
 * API (NeoForge {@code BuildCreativeModeTabContentsEvent}, Fabric {@code CreativeModeTabEvents}).
 * Content classes call {@link #addTo(HbmTab, RegistryObject)} during registration.
 */
public final class ModTabs {

    private ModTabs() {}

    // The registry key is named lazily, and CONTENTS/STACK_CONTENTS resolve the tab keys on first read:
    // Reg's .tab(...) reaches this class while Dejson initialises a registration class in a plain JVM at
    // build time, where 1.20.1 cannot create a ResourceKey (see Golem's RegistryObject).
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(() -> Registries.CREATIVE_MODE_TAB, Constants.MOD_ID);

    private static final EnumMap<HbmTab, List<RegistryObject<? extends Item>>> LISTS = new EnumMap<>(HbmTab.class);
    private static final EnumMap<HbmTab, RegistryObject<CreativeModeTab>> TAB_OBJECTS = new EnumMap<>(HbmTab.class);

    /** Tab key -> registration-order item handles. Final display ordering is resolved by {@link #displayStacks}. */
    public static final Map<ResourceKey<CreativeModeTab>, List<RegistryObject<? extends Item>>> CONTENTS = new ByTabKey<>(LISTS, concat());

    private static final EnumMap<HbmTab, List<Supplier<List<ItemStack>>>> STACK_LISTS = new EnumMap<>(HbmTab.class);
    /** Tab key -> custom display batches (e.g. one fluid bucket per fluid), resolved by {@link #displayStacks}. */
    public static final Map<ResourceKey<CreativeModeTab>, List<Supplier<List<ItemStack>>>> STACK_CONTENTS = new ByTabKey<>(STACK_LISTS, concat());

    static {
        for (HbmTab tab : HbmTab.values()) {
            LISTS.put(tab, new ArrayList<>());
            STACK_LISTS.put(tab, new ArrayList<>());
        }
        // FORK (bombs only): a single creative tab, the bomb tab. Every other HbmTab is an alias of it, so content that
        // is still listed under another tab ends up here and is then cut down to the nukes (see shouldDisplay).
        List<RegistryObject<? extends Item>> bombContents = LISTS.get(HbmTab.BOMBS);
        RegistryObject<CreativeModeTab> bombsTab = TABS.register(HbmTab.BOMBS.id, () ->
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .title(Component.translatable("itemGroup.hbm." + HbmTab.BOMBS.id))
                        // Icon = Little Boy (FORK).
                        .icon(() -> new ItemStack(com.hbm.blocks.bomb.ModBombBlocks.nuke_boy.get().asItem())) // Little Boy
                        .build());
        for (HbmTab tab : HbmTab.values()) TAB_OBJECTS.put(tab, bombsTab);
    }

    /** Concatenates the lists of tabs that share one tab object. */
    private static <T> java.util.function.BinaryOperator<List<T>> concat() {
        return (a, b) -> {
            List<T> merged = new ArrayList<>(a);
            merged.addAll(b);
            return merged;
        };
    }

    /**
     * A per-tab table read by tab key, in {@link HbmTab} order. Built on the first read, because the keys
     * are the registered tabs' keys and a key is only created when first asked for. Read-only.
     */
    private static final class ByTabKey<V> extends AbstractMap<ResourceKey<CreativeModeTab>, V> {

        private final EnumMap<HbmTab, V> byTab;
        private final java.util.function.BinaryOperator<V> merge;
        private volatile Map<ResourceKey<CreativeModeTab>, V> byKey;

        ByTabKey(EnumMap<HbmTab, V> byTab, java.util.function.BinaryOperator<V> merge) {
            this.byTab = byTab;
            this.merge = merge;
        }

        private Map<ResourceKey<CreativeModeTab>, V> resolved() {
            Map<ResourceKey<CreativeModeTab>, V> map = byKey;
            if (map == null) {
                map = new LinkedHashMap<>();
                for (Map.Entry<HbmTab, V> entry : byTab.entrySet()) {
                    map.merge(TAB_OBJECTS.get(entry.getKey()).getKey(), entry.getValue(), merge);
                }
                byKey = map = Collections.unmodifiableMap(map);
            }
            return map;
        }

        @Override
        public V get(Object key) {
            return resolved().get(key);
        }

        @Override
        public Set<Map.Entry<ResourceKey<CreativeModeTab>, V>> entrySet() {
            return resolved().entrySet();
        }
    }

    /** Add an item (or block item) to a tab's display list. */
    public static void addTo(HbmTab tab, RegistryObject<? extends Item> item) {
        List<RegistryObject<? extends Item>> contents = LISTS.get(tab);
        if (!contents.contains(item)) contents.add(item);
    }

    /** Add a batch of custom display stacks to a tab (resolved lazily at tab-build time). */
    public static void addStacksTo(HbmTab tab, Supplier<List<ItemStack>> stacks) {
        List<Supplier<List<ItemStack>>> contents = STACK_LISTS.get(tab);
        if (!contents.contains(stacks)) contents.add(stacks);
    }

    private static final EnumMap<HbmTab, EntryOrder<String>> ORDER = new EnumMap<>(HbmTab.class);
    private static final EnumMap<HbmTab, Map<RegistryObject<? extends Item>, Supplier<List<ItemStack>>>> DISPLAY_STACKS = new EnumMap<>(HbmTab.class);

    /** Moves the existing entries after the anchor's first stack; shared anchors keep declaration order. */
    public static void insertAfter(HbmTab tab, String anchor, String... entries) {
        ORDER.computeIfAbsent(tab, ignored -> new EntryOrder<>()).insertAfter(anchor, List.of(entries));
    }

    /** Moves the existing entries before the anchor's first stack; inserted entries may themselves be anchors. */
    public static void insertBefore(HbmTab tab, String anchor, String... entries) {
        ORDER.computeIfAbsent(tab, ignored -> new EntryOrder<>()).insertBefore(anchor, List.of(entries));
    }

    /** Moves existing entries to the front of a tab, retaining every component-bearing variant. */
    public static void insertFirst(HbmTab tab, String... entries) {
        ORDER.computeIfAbsent(tab, ignored -> new EntryOrder<>()).insertFirst(List.of(entries));
    }

    /** Removes every display variant of an existing entry. Missing entries and anchors refuse to build. */
    public static void remove(HbmTab tab, String entry) {
        ORDER.computeIfAbsent(tab, ignored -> new EntryOrder<>()).remove(entry);
    }

    /** Replaces an item's default display stack with its real component-bearing variants. */
    public static void displayStacks(HbmTab tab, RegistryObject<? extends Item> item, Supplier<List<ItemStack>> stacks) {
        DISPLAY_STACKS.computeIfAbsent(tab, ignored -> new LinkedHashMap<>()).put(item, stacks);
    }

    /** The final common listing consumed by every loader, including custom display variants and addon positions. */
    public static List<ItemStack> displayStacks(ResourceKey<CreativeModeTab> tab) {
        var contents = CONTENTS.get(tab);
        if (contents == null) return List.of();
        HbmTab group = HbmTab.BOMBS; // FORK (bombs only): the one remaining tab
        var replacements = DISPLAY_STACKS.getOrDefault(group, Map.of());
        var result = new ArrayList<ItemStack>();
        for (var handle : contents) {
            var item = handle.get();
            if (!shouldDisplay(tab, item)) continue;
            var replacement = replacements.get(handle);
            if (replacement == null) result.add(new ItemStack(item));
            else for (var stack : replacement.get()) if (shouldDisplay(tab, stack.getItem())) result.add(stack);
        }
        for (var supplier : STACK_CONTENTS.get(tab)) {
            for (var stack : supplier.get()) if (shouldDisplay(tab, stack.getItem())) result.add(stack);
        }
        var order = ORDER.get(group);
        if (order == null) return result;
        try {
            return order.apply(result, stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        } catch (RuntimeException ex) {
            // the ordering rules name items this fork no longer lists: keep registration order
            return result;
        }
    }

    public static void accept(ResourceKey<CreativeModeTab> tab, Consumer<ItemStack> output) {
        displayStacks(tab).forEach(output);
    }

    public static RegistryObject<CreativeModeTab> get(HbmTab tab) {
        return TAB_OBJECTS.get(tab);
    }

    /** Applies upstream's weapon-quality rule at the shared tab boundary, for every loader. */
    public static boolean shouldDisplay(ResourceKey<CreativeModeTab> tab, Item item) {
        // FORK (bombs only): the creative tabs show nothing but the nukes and their parts, and only in the bomb tab.
        if (!get(HbmTab.BOMBS).getKey().equals(tab)) return false;
        return isNukeItem(item);
    }

    /** FORK: only the bombs and parts that can be crafted with the Mekanism recipes of this fork (no kits, no prototype). */
    private static final java.util.Set<String> NUKE_ITEMS = java.util.Set.of(
            "nuke_boy", "nuke_man", "nuke_gadget", "nuke_mike", "nuke_tsar", "nuke_n2", "nuke_solinium",
            "nuke_fstbmb", "nuke_drop_rack", "solinium_core", "solinium_igniter", "solinium_propellant", "egg_balefire",
            "battery_spark", "bomb_casing", "fissile_core", "exotic_core", "thermonuclear_stage",
            "boy_bullet", "boy_igniter", "boy_propellant", "boy_shielding", "boy_target",
            "gadget_core", "gadget_explosive8", "gadget_wireing", "man_core", "man_explosive8", "man_igniter",
            "mike_cooling_unit", "mike_core", "mike_deut", "n2_charge", "tsar_core");

    /** Shown only with {@code missiles=true} in config/hbm_bombs.properties: missiles, warheads, launchers, designators. */
    private static final java.util.regex.Pattern MISSILE_ITEM = java.util.regex.Pattern.compile(
            "missile_.*|mp_.*|.*warhead.*|designator.*|launch_pad.*|launch_table.*|compact_launcher.*|.*_launcher");

    public static boolean isNukeItem(Item item) {
        var id = BuiltInRegistries.ITEM.getKey(item); // `var`: the id type is ResourceLocation below 26.1 and Identifier from 26.1
        if (id == null || !Constants.MOD_ID.equals(id.getNamespace())) return false;
        if (NUKE_ITEMS.contains(id.getPath())) return true;
        return com.hbm.compat.BombsForkConfig.missilesEnabled() && MISSILE_ITEM.matcher(id.getPath()).matches();
    }

    /** Forces class initialization so the static registrations above run. */
    public static void init() {}
}
