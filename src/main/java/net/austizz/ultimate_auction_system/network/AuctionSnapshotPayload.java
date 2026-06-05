package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionHouseSnapshot;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record AuctionSnapshotPayload(
        List<AuctionEntrySummary> browseListings,
        List<AuctionEntrySummary> myBids,
        List<AuctionEntrySummary> myAuctions,
        List<AuctionDeliverySummary> deliveries,
        AuctionAccountSummary account,
        double listingFeeRate,
        String message,
        boolean success
) implements CustomPacketPayload {
    public static final Type<AuctionSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "auction_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.browseListings());
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.myBids());
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.myAuctions());
                AuctionDeliverySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.deliveries());
                AuctionAccountSummary.STREAM_CODEC.encode(buf, payload.account());
                ByteBufCodecs.DOUBLE.encode(buf, payload.listingFeeRate());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                ByteBufCodecs.BOOL.encode(buf, payload.success());
            },
            buf -> new AuctionSnapshotPayload(
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionDeliverySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    AuctionAccountSummary.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)
            )
    );

    public static AuctionSnapshotPayload fromSnapshot(AuctionHouseSnapshot snapshot) {
        return new AuctionSnapshotPayload(
                snapshot.browseListings().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.myBids().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.myAuctions().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.deliveries().stream().map(AuctionDeliverySummary::fromEntry).toList(),
                AuctionAccountSummary.fromSnapshot(snapshot.primaryAccount()),
                snapshot.listingFeeRate(),
                snapshot.message(),
                snapshot.success()
        );
    }

    @Override
    public Type<AuctionSnapshotPayload> type() {
        return TYPE;
    }
}
