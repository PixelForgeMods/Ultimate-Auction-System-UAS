package net.austizz.ultimate_auction_system.banking;

import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;

public record UasItemResult(
        boolean success,
        String reason,
        ItemStack itemStack,
        String referenceId,
        BigDecimal amount
) {
    public UasItemResult {
        reason = reason == null ? "" : reason;
        itemStack = itemStack == null ? null : itemStack.copy();
        referenceId = referenceId == null ? "" : referenceId;
        amount = amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    public static UasItemResult ok(ItemStack itemStack, String referenceId, BigDecimal amount) {
        return new UasItemResult(true, "", itemStack, referenceId, amount);
    }

    public static UasItemResult fail(String reason) {
        return new UasItemResult(
                false,
                reason == null || reason.isBlank() ? "UBS item operation failed" : reason,
                null,
                "",
                BigDecimal.ZERO
        );
    }
}
