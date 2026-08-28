package net.austizz.ultimate_auction_system.network;

import net.austizz.ultimate_auction_system.AuctionAdminAuditEntry;
import net.austizz.ultimate_auction_system.AuctionAdminDashboardSnapshot;
import net.austizz.ultimate_auction_system.AuctionEconomyReport;
import net.austizz.ultimate_auction_system.AuctionPlayerBan;
import net.austizz.ultimate_auction_system.AuctionRecoveryEntry;
import net.austizz.ultimate_auction_system.AuctionSuspicionSignal;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AuctionAdminDashboardPayload(
        List<Stats> stats,
        List<EconomyReport> economyReports,
        List<Player> players,
        List<Ban> bans,
        List<Audit> auditLog,
        List<BannedEntry> bannedEntries,
        List<Suspicion> suspicionSignals,
        List<Recovery> recoveryEntries,
        List<AuctionEntrySummary> restrictedListings,
        List<AuctionEntrySummary> failedSettlements,
        String generatedAt
) {
    public static final AuctionAdminDashboardPayload EMPTY = new AuctionAdminDashboardPayload(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");

    public static final StreamCodec<RegistryFriendlyByteBuf, AuctionAdminDashboardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                Stats.STREAM_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, payload.stats());
                EconomyReport.STREAM_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, payload.economyReports());
                Player.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.players());
                Ban.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.bans());
                Audit.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.auditLog());
                BannedEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)).encode(buf, payload.bannedEntries());
                Suspicion.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.suspicionSignals());
                Recovery.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.recoveryEntries());
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.restrictedListings());
                AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).encode(buf, payload.failedSettlements());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.generatedAt());
            },
            buf -> new AuctionAdminDashboardPayload(
                    Stats.STREAM_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                    EconomyReport.STREAM_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                    Player.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    Ban.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    Audit.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    BannedEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)).decode(buf),
                    Suspicion.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    Recovery.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    AuctionEntrySummary.STREAM_CODEC.apply(ByteBufCodecs.list(128)).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    public static AuctionAdminDashboardPayload fromSnapshot(AuctionAdminDashboardSnapshot snapshot) {
        if (snapshot == null) {
            return EMPTY;
        }
        return new AuctionAdminDashboardPayload(
                snapshot.stats().stream().map(Stats::fromSnapshot).toList(),
                snapshot.economyReports().stream().map(EconomyReport::fromReport).toList(),
                snapshot.players().stream().map(Player::fromSnapshot).toList(),
                snapshot.bans().stream().map(Ban::fromBan).toList(),
                snapshot.auditLog().stream().map(Audit::fromEntry).toList(),
                snapshot.bannedEntries().stream().map(BannedEntry::fromSnapshot).toList(),
                snapshot.suspicionSignals().stream().map(Suspicion::fromSignal).toList(),
                snapshot.recoveryEntries().stream().map(Recovery::fromEntry).toList(),
                snapshot.restrictedListings().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.failedSettlements().stream().map(AuctionEntrySummary::fromListing).toList(),
                snapshot.generatedAt()
        );
    }

    public record Stats(
            String label,
            int auctionsCreated,
            int activeAuctions,
            int soldAuctions,
            int cancelledAuctions,
            int failedSettlements,
            int activeSellers,
            int activeBidders,
            String bidVolume,
            String soldValue,
            String estimatedListingFees,
            String estimatedSalesTax,
            String averageSale
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Stats> STREAM_CODEC = StreamCodec.of(
                (buf, stats) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, stats.label());
                    ByteBufCodecs.INT.encode(buf, stats.auctionsCreated());
                    ByteBufCodecs.INT.encode(buf, stats.activeAuctions());
                    ByteBufCodecs.INT.encode(buf, stats.soldAuctions());
                    ByteBufCodecs.INT.encode(buf, stats.cancelledAuctions());
                    ByteBufCodecs.INT.encode(buf, stats.failedSettlements());
                    ByteBufCodecs.INT.encode(buf, stats.activeSellers());
                    ByteBufCodecs.INT.encode(buf, stats.activeBidders());
                    ByteBufCodecs.STRING_UTF8.encode(buf, stats.bidVolume());
                    ByteBufCodecs.STRING_UTF8.encode(buf, stats.soldValue());
                    ByteBufCodecs.STRING_UTF8.encode(buf, stats.estimatedListingFees());
                    ByteBufCodecs.STRING_UTF8.encode(buf, stats.estimatedSalesTax());
                    ByteBufCodecs.STRING_UTF8.encode(buf, stats.averageSale());
                },
                buf -> new Stats(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        static Stats fromSnapshot(AuctionAdminDashboardSnapshot.Stats stats) {
            return new Stats(
                    stats.label(),
                    stats.auctionsCreated(),
                    stats.activeAuctions(),
                    stats.soldAuctions(),
                    stats.cancelledAuctions(),
                    stats.failedSettlements(),
                    stats.activeSellers(),
                    stats.activeBidders(),
                    stats.bidVolume(),
                    stats.soldValue(),
                    stats.estimatedListingFees(),
                    stats.estimatedSalesTax(),
                    stats.averageSale()
            );
        }
    }

    public record EconomyReport(
            String label,
            int activeListings,
            int completedSales,
            int failedSettlements,
            String grossVolume,
            String fees,
            String taxes,
            List<EconomyRow> topSellers,
            List<EconomyRow> topCategories,
            List<EconomyRow> topItems
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EconomyReport> STREAM_CODEC = StreamCodec.of(
                (buf, report) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, report.label());
                    ByteBufCodecs.INT.encode(buf, report.activeListings());
                    ByteBufCodecs.INT.encode(buf, report.completedSales());
                    ByteBufCodecs.INT.encode(buf, report.failedSettlements());
                    ByteBufCodecs.STRING_UTF8.encode(buf, report.grossVolume());
                    ByteBufCodecs.STRING_UTF8.encode(buf, report.fees());
                    ByteBufCodecs.STRING_UTF8.encode(buf, report.taxes());
                    EconomyRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, report.topSellers());
                    EconomyRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, report.topCategories());
                    EconomyRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)).encode(buf, report.topItems());
                },
                buf -> new EconomyReport(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        EconomyRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                        EconomyRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)).decode(buf),
                        EconomyRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)).decode(buf)
                )
        );

        static EconomyReport fromReport(AuctionEconomyReport report) {
            return new EconomyReport(
                    report.label(),
                    report.activeListings(),
                    report.completedSales(),
                    report.failedSettlements(),
                    report.grossVolume(),
                    report.fees(),
                    report.taxes(),
                    report.topSellers().stream().map(EconomyRow::fromReport).toList(),
                    report.topCategories().stream().map(EconomyRow::fromReport).toList(),
                    report.topItems().stream().map(EconomyRow::fromReport).toList()
            );
        }
    }

    public record EconomyRow(String label, int count, String amount) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EconomyRow> STREAM_CODEC = StreamCodec.of(
                (buf, row) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.label());
                    ByteBufCodecs.INT.encode(buf, row.count());
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.amount());
                },
                buf -> new EconomyRow(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        static EconomyRow fromReport(AuctionEconomyReport.Row row) {
            return new EconomyRow(row.label(), row.count(), row.amount());
        }
    }

    public record Player(
            UUID playerId,
            String playerName,
            int activeListings,
            int maxActiveListings,
            int bidCount,
            int soldCount,
            int boughtCount,
            int cancelledCount,
            String bidVolume,
            String soldValue,
            int deliveryCount,
            String deliveryPreview,
            boolean blockCreate,
            boolean blockBid,
            boolean blockBuyout,
            boolean blockWatch,
            String banReason,
            String banExpiresAt,
            boolean banActive
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Player> STREAM_CODEC = StreamCodec.of(
                (buf, player) -> {
                    UasNetworkCodecs.UUID_CODEC.encode(buf, player.playerId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, player.playerName());
                    ByteBufCodecs.INT.encode(buf, player.activeListings());
                    ByteBufCodecs.INT.encode(buf, player.maxActiveListings());
                    ByteBufCodecs.INT.encode(buf, player.bidCount());
                    ByteBufCodecs.INT.encode(buf, player.soldCount());
                    ByteBufCodecs.INT.encode(buf, player.boughtCount());
                    ByteBufCodecs.INT.encode(buf, player.cancelledCount());
                    ByteBufCodecs.STRING_UTF8.encode(buf, player.bidVolume());
                    ByteBufCodecs.STRING_UTF8.encode(buf, player.soldValue());
                    ByteBufCodecs.INT.encode(buf, player.deliveryCount());
                    ByteBufCodecs.STRING_UTF8.encode(buf, player.deliveryPreview());
                    ByteBufCodecs.BOOL.encode(buf, player.blockCreate());
                    ByteBufCodecs.BOOL.encode(buf, player.blockBid());
                    ByteBufCodecs.BOOL.encode(buf, player.blockBuyout());
                    ByteBufCodecs.BOOL.encode(buf, player.blockWatch());
                    ByteBufCodecs.STRING_UTF8.encode(buf, player.banReason());
                    ByteBufCodecs.STRING_UTF8.encode(buf, player.banExpiresAt());
                    ByteBufCodecs.BOOL.encode(buf, player.banActive());
                },
                buf -> new Player(
                        UasNetworkCodecs.UUID_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)
                )
        );

        static Player fromSnapshot(AuctionAdminDashboardSnapshot.Player player) {
            return new Player(
                    player.playerId(),
                    player.playerName(),
                    player.activeListings(),
                    player.maxActiveListings(),
                    player.bidCount(),
                    player.soldCount(),
                    player.boughtCount(),
                    player.cancelledCount(),
                    player.bidVolume(),
                    player.soldValue(),
                    player.deliveryCount(),
                    player.deliveryPreview(),
                    player.blockCreate(),
                    player.blockBid(),
                    player.blockBuyout(),
                    player.blockWatch(),
                    player.banReason(),
                    player.banExpiresAt(),
                    player.banActive()
            );
        }
    }

    public record Ban(
            UUID playerId,
            String playerName,
            boolean blockCreate,
            boolean blockBid,
            boolean blockBuyout,
            boolean blockWatch,
            String reason,
            String createdByName,
            String createdAt,
            String expiresAt,
            boolean active,
            String revokedAt,
            String revokedReason
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Ban> STREAM_CODEC = StreamCodec.of(
                (buf, ban) -> {
                    UasNetworkCodecs.UUID_CODEC.encode(buf, ban.playerId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.playerName());
                    ByteBufCodecs.BOOL.encode(buf, ban.blockCreate());
                    ByteBufCodecs.BOOL.encode(buf, ban.blockBid());
                    ByteBufCodecs.BOOL.encode(buf, ban.blockBuyout());
                    ByteBufCodecs.BOOL.encode(buf, ban.blockWatch());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.reason());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.createdByName());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.createdAt());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.expiresAt());
                    ByteBufCodecs.BOOL.encode(buf, ban.active());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.revokedAt());
                    ByteBufCodecs.STRING_UTF8.encode(buf, ban.revokedReason());
                },
                buf -> new Ban(
                        UasNetworkCodecs.UUID_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        static Ban fromBan(AuctionPlayerBan ban) {
            return new Ban(
                    ban.playerId(),
                    ban.playerName(),
                    ban.blockCreate(),
                    ban.blockBid(),
                    ban.blockBuyout(),
                    ban.blockWatch(),
                    ban.reason(),
                    ban.createdByName(),
                    time(ban.createdAt()),
                    ban.expiresAt().map(AuctionAdminDashboardPayload::time).orElse("Never"),
                    ban.active(),
                    ban.revokedAt().map(AuctionAdminDashboardPayload::time).orElse(""),
                    ban.revokedReason()
            );
        }
    }

    public record Audit(
            String action,
            String adminName,
            String target,
            String reason,
            boolean success,
            String message,
            String createdAt
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Audit> STREAM_CODEC = StreamCodec.of(
                (buf, audit) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, audit.action());
                    ByteBufCodecs.STRING_UTF8.encode(buf, audit.adminName());
                    ByteBufCodecs.STRING_UTF8.encode(buf, audit.target());
                    ByteBufCodecs.STRING_UTF8.encode(buf, audit.reason());
                    ByteBufCodecs.BOOL.encode(buf, audit.success());
                    ByteBufCodecs.STRING_UTF8.encode(buf, audit.message());
                    ByteBufCodecs.STRING_UTF8.encode(buf, audit.createdAt());
                },
                buf -> new Audit(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        static Audit fromEntry(AuctionAdminAuditEntry entry) {
            return new Audit(
                    entry.action(),
                    entry.adminName(),
                    entry.target(),
                    entry.reason(),
                    entry.success(),
                    entry.message(),
                    time(entry.createdAt())
            );
        }
    }

    public record BannedEntry(
            String entry,
            String type,
            String label,
            int matchingActiveAuctions
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, BannedEntry> STREAM_CODEC = StreamCodec.of(
                (buf, entry) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.entry());
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.type());
                    ByteBufCodecs.STRING_UTF8.encode(buf, entry.label());
                    ByteBufCodecs.INT.encode(buf, entry.matchingActiveAuctions());
                },
                buf -> new BannedEntry(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf)
                )
        );

        static BannedEntry fromSnapshot(AuctionAdminDashboardSnapshot.BannedEntry entry) {
            return new BannedEntry(entry.entry(), entry.type(), entry.label(), entry.matchingActiveAuctions());
        }
    }

    public record Suspicion(
            String type,
            UUID auctionId,
            String itemName,
            UUID primaryPlayerId,
            String primaryPlayerName,
            UUID secondaryPlayerId,
            String secondaryPlayerName,
            int evidenceCount,
            int windowSeconds,
            String startAmount,
            String endAmount,
            String observedAt
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Suspicion> STREAM_CODEC = StreamCodec.of(
                (buf, signal) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.type());
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, signal.auctionId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.itemName());
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, signal.primaryPlayerId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.primaryPlayerName());
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, signal.secondaryPlayerId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.secondaryPlayerName());
                    ByteBufCodecs.INT.encode(buf, signal.evidenceCount());
                    ByteBufCodecs.INT.encode(buf, signal.windowSeconds());
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.startAmount());
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.endAmount());
                    ByteBufCodecs.STRING_UTF8.encode(buf, signal.observedAt());
                },
                buf -> new Suspicion(
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        static Suspicion fromSignal(AuctionSuspicionSignal signal) {
            return new Suspicion(
                    signal.type(),
                    signal.auctionId(),
                    signal.itemName(),
                    signal.primaryPlayerId(),
                    signal.primaryPlayerName(),
                    signal.secondaryPlayerId(),
                    signal.secondaryPlayerName(),
                    signal.evidenceCount(),
                    signal.windowSeconds(),
                    UasMoneyFormatter.display(signal.startAmount()),
                    UasMoneyFormatter.display(signal.endAmount()),
                    time(signal.observedAt())
            );
        }
    }

    public record Recovery(
            UUID recoveryId,
            UUID auctionId,
            UUID sellerId,
            String sellerName,
            String itemName,
            int totalItemCount,
            String adminName,
            String reason,
            String recoveredAt,
            boolean active,
            String releasedByName,
            String releasedAt
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Recovery> STREAM_CODEC = StreamCodec.of(
                (buf, recovery) -> {
                    UasNetworkCodecs.UUID_CODEC.encode(buf, recovery.recoveryId());
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, recovery.auctionId());
                    UasNetworkCodecs.OPTIONAL_UUID_CODEC.encode(buf, recovery.sellerId());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.sellerName());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.itemName());
                    ByteBufCodecs.INT.encode(buf, recovery.totalItemCount());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.adminName());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.reason());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.recoveredAt());
                    ByteBufCodecs.BOOL.encode(buf, recovery.active());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.releasedByName());
                    ByteBufCodecs.STRING_UTF8.encode(buf, recovery.releasedAt());
                },
                buf -> new Recovery(
                        UasNetworkCodecs.UUID_CODEC.decode(buf),
                        UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                        UasNetworkCodecs.OPTIONAL_UUID_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)
                )
        );

        static Recovery fromEntry(AuctionRecoveryEntry entry) {
            return new Recovery(
                    entry.recoveryId(),
                    entry.auctionId(),
                    entry.sellerId(),
                    entry.sellerName(),
                    entry.itemName(),
                    entry.totalItemCount(),
                    entry.adminName(),
                    entry.reason(),
                    time(entry.recoveredAt()),
                    entry.active(),
                    entry.releasedByName(),
                    entry.releasedAt().map(AuctionAdminDashboardPayload::time).orElse("")
            );
        }
    }

    private static String time(LocalDateTime time) {
        return time == null ? "" : time.toString();
    }
}
