package net.austizz.ultimate_auction_system;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class AuctionSuspicionAnalyzer {
    public record Rules(
            boolean enabled,
            boolean sellerSelfBidSignalEnabled,
            int rapidBidWindowSeconds,
            int rapidBidCount,
            int repeatedBidderPairCount,
            int repeatedCancelWindowHours,
            int repeatedCancelCount
    ) {
        public static Rules fromConfig() {
            return new Rules(
                    Config.auditSuspiciousBidPatterns,
                    Config.auditSellerSelfBidSignals,
                    Config.suspiciousRapidBidWindowSeconds,
                    Config.suspiciousRapidBidCount,
                    Config.suspiciousRepeatedBidderPairCount,
                    Config.suspiciousCancelWindowHours,
                    Config.suspiciousCancelCount
            );
        }
    }

    public List<AuctionSuspicionSignal> analyze(AuctionItem item, Rules rules, Function<UUID, String> playerNames) {
        if (item == null || rules == null || !rules.enabled()) {
            return List.of();
        }
        return analyzeBidRecords(item.getAuctionId(), item.getDisplayTitle(), item.getPlayerId(), item.getBidRecords(), rules, playerNames);
    }

    public List<AuctionSuspicionSignal> analyzeBidRecords(UUID auctionId,
                                                          String itemName,
                                                          UUID sellerId,
                                                          List<AuctionBidRecord> records,
                                                          Rules rules,
                                                          Function<UUID, String> playerNames) {
        if (rules == null || !rules.enabled()) {
            return List.of();
        }
        Function<UUID, String> names = playerNames == null ? id -> "" : playerNames;
        ArrayList<AuctionSuspicionSignal> signals = new ArrayList<>();
        List<AuctionBidRecord> safeRecords = records == null ? List.of() : records;
        List<AuctionBidRecord> accepted = acceptedBids(safeRecords);
        rapidBidEscalation(auctionId, itemName, sellerId, accepted, rules, names).ifPresent(signals::add);
        repeatedBidderPair(auctionId, itemName, accepted, rules, names).ifPresent(signals::add);
        sellerSelfBid(auctionId, itemName, sellerId, safeRecords, rules, names).ifPresent(signals::add);
        return signals.stream()
                .sorted(Comparator.comparing(AuctionSuspicionSignal::observedAt).reversed())
                .toList();
    }

    public List<AuctionSuspicionSignal> repeatedCancellationSignals(Collection<AuctionItem> items,
                                                                    Rules rules,
                                                                    Function<UUID, String> playerNames) {
        if (items == null || rules == null || !rules.enabled() || rules.repeatedCancelCount() <= 1) {
            return List.of();
        }
        Function<UUID, String> names = playerNames == null ? id -> "" : playerNames;
        LocalDateTime cutoff = LocalDateTime.now().minusHours(Math.max(1, rules.repeatedCancelWindowHours()));
        Map<UUID, List<AuctionItem>> bySeller = new HashMap<>();
        for (AuctionItem item : items) {
            if (item == null || item.getPlayerId() == null || item.getState() != AuctionState.CANCELLED) {
                continue;
            }
            if (item.getUpdatedAt() == null || item.getUpdatedAt().isBefore(cutoff)) {
                continue;
            }
            bySeller.computeIfAbsent(item.getPlayerId(), ignored -> new ArrayList<>()).add(item);
        }

        ArrayList<AuctionSuspicionSignal> signals = new ArrayList<>();
        for (Map.Entry<UUID, List<AuctionItem>> entry : bySeller.entrySet()) {
            List<AuctionItem> cancelled = entry.getValue();
            if (cancelled.size() < rules.repeatedCancelCount()) {
                continue;
            }
            LocalDateTime observedAt = cancelled.stream()
                    .map(AuctionItem::getUpdatedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDateTime.now());
            signals.add(new AuctionSuspicionSignal(
                    AuctionSuspicionSignal.REPEATED_CANCELLED_LISTINGS,
                    null,
                    "",
                    entry.getKey(),
                    names.apply(entry.getKey()),
                    null,
                    "",
                    cancelled.size(),
                    Math.max(1, rules.repeatedCancelWindowHours()) * 3600,
                    java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO,
                    observedAt
            ));
        }
        return signals.stream()
                .sorted(Comparator.comparing(AuctionSuspicionSignal::observedAt).reversed())
                .toList();
    }

    public AuctionSuspicionSignal sellerSelfBidAttempt(AuctionItem item, UUID bidderId, Function<UUID, String> playerNames) {
        Function<UUID, String> names = playerNames == null ? id -> "" : playerNames;
        return new AuctionSuspicionSignal(
                AuctionSuspicionSignal.SELLER_SELF_BID,
                item == null ? null : item.getAuctionId(),
                item == null ? "" : item.getDisplayTitle(),
                bidderId,
                names.apply(bidderId),
                item == null ? null : item.getPlayerId(),
                item == null ? "" : names.apply(item.getPlayerId()),
                1,
                0,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                LocalDateTime.now()
        );
    }

    private Optional<AuctionSuspicionSignal> rapidBidEscalation(UUID auctionId,
                                                               String itemName,
                                                               UUID sellerId,
                                                               List<AuctionBidRecord> accepted,
                                                               Rules rules,
                                                               Function<UUID, String> playerNames) {
        int threshold = Math.max(2, rules.rapidBidCount());
        int windowSeconds = Math.max(1, rules.rapidBidWindowSeconds());
        if (accepted.size() < threshold) {
            return Optional.empty();
        }
        for (int start = 0; start < accepted.size(); start++) {
            int end = start;
            while (end + 1 < accepted.size()
                    && Duration.between(accepted.get(start).getTimestamp(), accepted.get(end + 1).getTimestamp()).getSeconds() <= windowSeconds) {
                end++;
            }
            int count = end - start + 1;
            if (count >= threshold) {
                AuctionBidRecord first = accepted.get(start);
                AuctionBidRecord last = accepted.get(end);
                return Optional.of(new AuctionSuspicionSignal(
                        AuctionSuspicionSignal.RAPID_BID_ESCALATION,
                        auctionId,
                        itemName,
                        last.getBidderId(),
                        playerNames.apply(last.getBidderId()),
                        sellerId,
                        playerNames.apply(sellerId),
                        count,
                        windowSeconds,
                        first.getAmount(),
                        last.getAmount(),
                        last.getTimestamp()
                ));
            }
        }
        return Optional.empty();
    }

    private Optional<AuctionSuspicionSignal> repeatedBidderPair(UUID auctionId,
                                                               String itemName,
                                                               List<AuctionBidRecord> accepted,
                                                               Rules rules,
                                                               Function<UUID, String> playerNames) {
        int threshold = Math.max(2, rules.repeatedBidderPairCount());
        if (accepted.size() < threshold + 1) {
            return Optional.empty();
        }
        Map<BidderPair, Integer> pairCounts = new HashMap<>();
        Map<BidderPair, LocalDateTime> lastSeen = new HashMap<>();
        for (int i = 1; i < accepted.size(); i++) {
            UUID previous = accepted.get(i - 1).getBidderId();
            UUID current = accepted.get(i).getBidderId();
            if (previous == null || current == null || previous.equals(current)) {
                continue;
            }
            BidderPair pair = BidderPair.of(previous, current);
            pairCounts.merge(pair, 1, Integer::sum);
            lastSeen.put(pair, accepted.get(i).getTimestamp());
        }

        BidderPair bestPair = null;
        int bestCount = 0;
        for (Map.Entry<BidderPair, Integer> entry : pairCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestPair = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        if (bestPair == null || bestCount < threshold) {
            return Optional.empty();
        }
        return Optional.of(new AuctionSuspicionSignal(
                AuctionSuspicionSignal.REPEATED_BIDDER_PAIR,
                auctionId,
                itemName,
                bestPair.left(),
                playerNames.apply(bestPair.left()),
                bestPair.right(),
                playerNames.apply(bestPair.right()),
                bestCount,
                0,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                lastSeen.getOrDefault(bestPair, LocalDateTime.now())
        ));
    }

    private Optional<AuctionSuspicionSignal> sellerSelfBid(UUID auctionId,
                                                          String itemName,
                                                          UUID sellerId,
                                                          List<AuctionBidRecord> records,
                                                          Rules rules,
                                                          Function<UUID, String> playerNames) {
        if (!rules.sellerSelfBidSignalEnabled() || sellerId == null || records == null) {
            return Optional.empty();
        }
        int count = 0;
        LocalDateTime last = null;
        for (AuctionBidRecord record : records) {
            if (record != null && sellerId.equals(record.getBidderId())) {
                count++;
                last = record.getTimestamp();
            }
        }
        if (count <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AuctionSuspicionSignal(
                AuctionSuspicionSignal.SELLER_SELF_BID,
                auctionId,
                itemName,
                sellerId,
                playerNames.apply(sellerId),
                null,
                "",
                count,
                0,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                last == null ? LocalDateTime.now() : last
        ));
    }

    private static List<AuctionBidRecord> acceptedBids(List<AuctionBidRecord> records) {
        return records.stream()
                .filter(record -> record != null && record.isAccepted())
                .sorted(Comparator.comparing(AuctionBidRecord::getTimestamp))
                .toList();
    }

    private record BidderPair(UUID left, UUID right) {
        static BidderPair of(UUID first, UUID second) {
            if (first.compareTo(second) <= 0) {
                return new BidderPair(first, second);
            }
            return new BidderPair(second, first);
        }
    }
}
