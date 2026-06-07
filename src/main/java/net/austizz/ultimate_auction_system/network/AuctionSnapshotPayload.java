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
        List<AuctionModFilterSummaryPayload> modFilters,
        AuctionAccountSummary account,
        AuctionPendingListingSummary pendingListing,
        double listingFeeRate,
        String message,
        boolean success,
        boolean adminMode,
        AuctionAdminDashboardPayload adminDashboard
) implements CustomPacketPayload {
    public static final Type<AuctionSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateAuctionSystem.MODID, "auction_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.browseListings());
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.myBids());
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.myAuctions());
                AuctionDeliverySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.deliveries());
                AuctionModFilterSummaryPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.modFilters());
                AuctionAccountSummary.STREAM_CODEC.encode(buf, payload.account());
                AuctionPendingListingSummary.STREAM_CODEC.encode(buf, payload.pendingListing());
                ByteBufCodecs.DOUBLE.encode(buf, payload.listingFeeRate());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.message());
                ByteBufCodecs.BOOL.encode(buf, payload.success());
                ByteBufCodecs.BOOL.encode(buf, payload.adminMode());
                AuctionAdminDashboardPayload.STREAM_CODEC.encode(buf, payload.adminDashboard());
            },
            buf -> new AuctionSnapshotPayload(
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionDeliverySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    AuctionModFilterSummaryPayload.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    AuctionAccountSummary.STREAM_CODEC.decode(buf),
                    AuctionPendingListingSummary.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    AuctionAdminDashboardPayload.STREAM_CODEC.decode(buf)
            )
    );

    public AuctionSnapshotPayload {
        adminDashboard = adminDashboard == null ? AuctionAdminDashboardPayload.EMPTY : adminDashboard;
    }

    public static AuctionSnapshotPayload fromSnapshot(AuctionHouseSnapshot snapshot) {
        return new AuctionSnapshotPayload(
                snapshot.browseListings().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.myBids().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.myAuctions().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.deliveries().stream().map(AuctionDeliverySummary::fromEntry).toList(),
                snapshot.modFilters().stream().map(AuctionModFilterSummaryPayload::fromSummary).toList(),
                AuctionAccountSummary.fromSnapshot(snapshot.primaryAccount()),
                AuctionPendingListingSummary.fromPreview(snapshot.pendingListing()),
                snapshot.listingFeeRate(),
                snapshot.message(),
                snapshot.success(),
                snapshot.adminMode(),
                AuctionAdminDashboardPayload.fromSnapshot(snapshot.adminDashboard())
        );
    }

    @Override
    public Type<AuctionSnapshotPayload> type() {
        return TYPE;
    }
}
