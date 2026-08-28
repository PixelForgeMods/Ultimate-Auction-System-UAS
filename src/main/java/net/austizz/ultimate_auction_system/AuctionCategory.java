package net.austizz.ultimate_auction_system;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.Locale;

public enum AuctionCategory {
    ALL("All"),
    WEAPONS("Weapons"),
    ARMOR("Armor"),
    TOOLS("Tools"),
    CONSUMABLES("Consumables"),
    BLOCKS("Blocks"),
    MISC("Misc");

    private final String label;

    AuctionCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean matches(ItemStack stack) {
        if (this == ALL) {
            return true;
        }
        return categorize(stack) == this;
    }

    public static AuctionCategory fromToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        try {
            return AuctionCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }

    public static AuctionCategory categorize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return MISC;
        }

        Item item = stack.getItem();
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) {
            return WEAPONS;
        }
        if (item instanceof ArmorItem) {
            return ARMOR;
        }
        if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem) {
            return TOOLS;
        }
        if (stack.getFoodProperties(null) != null) {
            return CONSUMABLES;
        }
        if (item instanceof BlockItem) {
            return BLOCKS;
        }
        return MISC;
    }
}
