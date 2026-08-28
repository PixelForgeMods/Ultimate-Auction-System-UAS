package net.austizz.ultimate_auction_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

final class UasItemStackNbt {
    private UasItemStackNbt() {
    }

    static CompoundTag saveOptional(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null) {
            return new CompoundTag();
        }
        return asCompound(stack.saveOptional(registries));
    }

    static CompoundTag asCompound(Tag saved) {
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }
}
