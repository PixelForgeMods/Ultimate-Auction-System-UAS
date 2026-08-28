package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record AuctionActionPayload(
        String action,
        UUID auctionId,
        UUID deliveryId,
        int slot,
        List<Integer> slots,
        String title,
        String amount,
        String startingBid,
        String buyoutPrice,
        String reservePrice,
        String format,
        int durationHours,
        String endDateTime,
        String description,
        String search,
        String category,
        String sort,
        String minimumPrice,
        String maximumPrice,
        long maximumHoursLeft,
        String modId,
        UUID accountId,
        boolean adminMode
) implements CustomPacketPayload {
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    public static final Type<AuctionActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "auction_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.action());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.auctionId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.deliveryId());
                ByteBufCodecs.INT.encode(buf, payload.slot());
                ByteBufCodecs.INT.apply(ByteBufCodecs.list(18)).encode(buf, payload.slots());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.title());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.amount());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.startingBid());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.buyoutPrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.reservePrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.format());
                ByteBufCodecs.INT.encode(buf, payload.durationHours());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.endDateTime());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.description());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.search());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.category());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.sort());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.minimumPrice());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.maximumPrice());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.maximumHoursLeft());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.modId());
                UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, payload.accountId());
                ByteBufCodecs.BOOL.encode(buf, payload.adminMode());
            },
            buf -> new AuctionActionPayload(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.apply(ByteBufCodecs.list(18)).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    public AuctionActionPayload {
        slots = slots == null ? List.of() : slots.stream().filter(selectedSlot -> selectedSlot != null).limit(18).toList();
        title = title == null ? "" : title;
        reservePrice = reservePrice == null ? "" : reservePrice;
        format = format == null ? "" : format;
        description = normalizeDescription(description);
    }

    private static String normalizeDescription(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').strip();
        return normalized.length() > MAX_DESCRIPTION_LENGTH ? normalized.substring(0, MAX_DESCRIPTION_LENGTH) : normalized;
    }

    public static AuctionActionPayload refresh(String search, String category, String sort, String min, String max, long hoursLeft) {
        return refresh(search, category, sort, min, max, hoursLeft, false);
    }

    public static AuctionActionPayload refresh(String search, String category, String sort, String min, String max, long hoursLeft, boolean adminMode) {
        return refresh(search, category, sort, min, max, hoursLeft, "", adminMode);
    }

    public static AuctionActionPayload refresh(String search, String category, String sort, String min, String max, long hoursLeft, String modId, boolean adminMode) {
        return new AuctionActionPayload("REFRESH", null, null, -1, List.of(), "", "", "", "", "", "", 0, "", "", search, category, sort, min, max, hoursLeft, modId, null, adminMode);
    }

    public List<Integer> selectedSlots() {
        if (slots != null && !slots.isEmpty()) {
            return slots;
        }
        return slot >= 0 ? List.of(slot) : List.of();
    }

    @Override
    public Type<AuctionActionPayload> type() {
        return TYPE;
    }
}
