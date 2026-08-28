package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class AuctionEconomyReporter {
    static final String DAY_LABEL = "24h";
    static final String WEEK_LABEL = "7d";
    static final String ALL_LABEL = "All";
    private static final int TOP_LIMIT = 5;

    public List<AuctionEconomyReport> buildReports(Collection<AuctionItem> items,
                                                   Function<UUID, String> playerNameResolver) {
        return buildReports(items, playerNameResolver, LocalDateTime.now());
    }

    List<AuctionEconomyReport> buildReports(Collection<AuctionItem> items,
                                            Function<UUID, String> playerNameResolver,
                                            LocalDateTime now) {
        List<Source> sources = items == null
                ? List.of()
                : items.stream()
                .filter(item -> item != null)
                .map(this::sourceFromItem)
                .toList();
        return buildReportsFromSources(sources, playerNameResolver, now);
    }

    List<AuctionEconomyReport> buildReportsFromSources(Collection<Source> sources,
                                                       Function<UUID, String> playerNameResolver,
                                                       LocalDateTime now) {
        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        List<Source> safeSources = sources == null ? List.of() : List.copyOf(sources);
        Function<UUID, String> safeResolver = playerNameResolver == null ? AuctionEconomyReporter::shortPlayerId : playerNameResolver;
        return List.of(
                buildReport(DAY_LABEL, safeSources, safeResolver, safeNow.minusHours(24)),
                buildReport(WEEK_LABEL, safeSources, safeResolver, safeNow.minusDays(7)),
                buildReport(ALL_LABEL, safeSources, safeResolver, null)
        );
    }

    public AuctionEconomyReport reportForToken(Collection<AuctionItem> items,
                                               Function<UUID, String> playerNameResolver,
                                               String token) {
        String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
        String label = switch (normalized) {
            case "day", "24h", "today" -> DAY_LABEL;
            case "week", "7d" -> WEEK_LABEL;
            case "all", "all-time", "alltime" -> ALL_LABEL;
            default -> ALL_LABEL;
        };
        return buildReports(items, playerNameResolver).stream()
                .filter(report -> label.equals(report.label()))
                .findFirst()
                .orElseGet(() -> buildReport(ALL_LABEL, List.of(), playerNameResolver, null));
    }

    private AuctionEconomyReport buildReport(String label,
                                             Collection<Source> sources,
                                             Function<UUID, String> playerNameResolver,
                                             LocalDateTime cutoff) {
        ReportAccumulator accumulator = new ReportAccumulator();
        for (Source source : sources) {
            if (source == null) {
                continue;
            }
            if (source.state() == AuctionState.ACTIVE && inWindow(source.createdAt(), cutoff)) {
                accumulator.activeListings++;
            }
            accumulateFinancialEvents(source, accumulator, cutoff);
            accumulateCompletedSale(source, accumulator, playerNameResolver, cutoff);
            accumulateFailedSettlement(source, accumulator, cutoff);
        }
        return new AuctionEconomyReport(
                label,
                accumulator.activeListings,
                accumulator.completedSales,
                accumulator.failedSettlements,
                moneyLabel(accumulator.grossVolume),
                moneyLabel(accumulator.fees),
                moneyLabel(accumulator.taxes),
                topRows(accumulator.topSellers),
                topRows(accumulator.topCategories),
                topRows(accumulator.topItems)
        );
    }

    private void accumulateFinancialEvents(Source source, ReportAccumulator accumulator, LocalDateTime cutoff) {
        for (AuctionFinancialEvent event : source.financialEvents()) {
            if (event == null || !event.success() || !inWindow(event.createdAt(), cutoff)) {
                continue;
            }
            if (AuctionHouse.EVENT_LISTING_FEE.equals(event.type()) || AuctionHouse.EVENT_CANCELLATION_FEE.equals(event.type())) {
                accumulator.fees = accumulator.fees.add(safeMoney(event.amount()));
            } else if (AuctionHouse.EVENT_SALES_TAX.equals(event.type())) {
                accumulator.taxes = accumulator.taxes.add(safeMoney(event.amount()));
            }
        }
    }

    private void accumulateCompletedSale(Source source,
                                         ReportAccumulator accumulator,
                                         Function<UUID, String> playerNameResolver,
                                         LocalDateTime cutoff) {
        AuctionFinancialEvent payout = latestSuccessfulEvent(source, AuctionHouse.EVENT_AUCTION_PAYOUT);
        if (payout == null || !inWindow(payout.createdAt(), cutoff)) {
            return;
        }
        BigDecimal gross = saleGross(source, payout);
        accumulator.completedSales++;
        accumulator.grossVolume = accumulator.grossVolume.add(gross);
        String sellerName = safePlayerName(playerNameResolver, source.sellerId());
        accumulator.topSellers.computeIfAbsent(sellerName, ignored -> new Bucket()).add(gross);
        accumulator.topCategories.computeIfAbsent(source.categoryLabel(), ignored -> new Bucket()).add(gross);
        accumulator.topItems.computeIfAbsent(source.itemLabel(), ignored -> new Bucket()).add(gross);
    }

    private void accumulateFailedSettlement(Source source, ReportAccumulator accumulator, LocalDateTime cutoff) {
        if (source.state() != AuctionState.FAILED_SETTLEMENT) {
            return;
        }
        LocalDateTime failureTime = latestFailedFinancialEvent(source)
                .map(AuctionFinancialEvent::createdAt)
                .orElse(source.updatedAt());
        if (inWindow(failureTime, cutoff)) {
            accumulator.failedSettlements++;
        }
    }

    private AuctionFinancialEvent latestSuccessfulEvent(Source source, String type) {
        List<AuctionFinancialEvent> events = source.financialEvents();
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        for (int index = events.size() - 1; index >= 0; index--) {
            AuctionFinancialEvent event = events.get(index);
            if (event != null && event.success() && normalized.equals(event.type())) {
                return event;
            }
        }
        return null;
    }

    private Optional<AuctionFinancialEvent> latestFailedFinancialEvent(Source source) {
        List<AuctionFinancialEvent> events = source.financialEvents();
        for (int index = events.size() - 1; index >= 0; index--) {
            AuctionFinancialEvent event = events.get(index);
            if (event != null && !event.success()) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    private BigDecimal saleGross(Source source, AuctionFinancialEvent payout) {
        BigDecimal gross = safeMoney(source.highestBid());
        if (gross.compareTo(BigDecimal.ZERO) > 0) {
            return gross;
        }
        return safeMoney(payout.amount()).add(source.financialEvents().stream()
                .filter(event -> event != null && event.success() && AuctionHouse.EVENT_SALES_TAX.equals(event.type()))
                .map(AuctionFinancialEvent::amount)
                .map(AuctionEconomyReporter::safeMoney)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private String categoryLabel(AuctionItem item) {
        if (item.isBundle()) {
            return "Bundle";
        }
        return AuctionCategory.categorize(item.getItem()).label();
    }

    private String itemLabel(AuctionItem item) {
        String label = item.getDisplayTitle();
        if (label != null && !label.isBlank()) {
            return label;
        }
        ItemStack stack = item.getItem();
        if (stack != null && !stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        return "auction item";
    }

    private Source sourceFromItem(AuctionItem item) {
        return new Source(
                item.getPlayerId(),
                itemLabel(item),
                categoryLabel(item),
                item.getState(),
                item.getHighestBid(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getFinancialEvents()
        );
    }

    private List<AuctionEconomyReport.Row> topRows(Map<String, Bucket> buckets) {
        return buckets.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Bucket>, BigDecimal>comparing(entry -> entry.getValue().amount).reversed()
                        .thenComparing(entry -> entry.getKey().toLowerCase(Locale.ROOT)))
                .limit(TOP_LIMIT)
                .map(entry -> new AuctionEconomyReport.Row(entry.getKey(), entry.getValue().count, moneyLabel(entry.getValue().amount)))
                .toList();
    }

    private static boolean inWindow(LocalDateTime time, LocalDateTime cutoff) {
        return cutoff == null || (time != null && !time.isBefore(cutoff));
    }

    private static BigDecimal safeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.max(BigDecimal.ZERO);
    }

    private static String moneyLabel(BigDecimal amount) {
        return UasMoneyFormatter.display(safeMoney(amount));
    }

    private static String safePlayerName(Function<UUID, String> resolver, UUID playerId) {
        String resolved = resolver == null ? null : resolver.apply(playerId);
        return resolved == null || resolved.isBlank() ? shortPlayerId(playerId) : resolved;
    }

    private static String shortPlayerId(UUID playerId) {
        return playerId == null ? "Unknown" : playerId.toString().substring(0, 8);
    }

    private static final class ReportAccumulator {
        private int activeListings;
        private int completedSales;
        private int failedSettlements;
        private BigDecimal grossVolume = BigDecimal.ZERO;
        private BigDecimal fees = BigDecimal.ZERO;
        private BigDecimal taxes = BigDecimal.ZERO;
        private final Map<String, Bucket> topSellers = new HashMap<>();
        private final Map<String, Bucket> topCategories = new HashMap<>();
        private final Map<String, Bucket> topItems = new HashMap<>();
    }

    private static final class Bucket {
        private int count;
        private BigDecimal amount = BigDecimal.ZERO;

        private void add(BigDecimal value) {
            count++;
            amount = amount.add(safeMoney(value));
        }
    }

    record Source(UUID sellerId,
                  String itemLabel,
                  String categoryLabel,
                  AuctionState state,
                  BigDecimal highestBid,
                  LocalDateTime createdAt,
                  LocalDateTime updatedAt,
                  List<AuctionFinancialEvent> financialEvents) {
        Source {
            itemLabel = itemLabel == null || itemLabel.isBlank() ? "auction item" : itemLabel;
            categoryLabel = categoryLabel == null || categoryLabel.isBlank() ? AuctionCategory.MISC.label() : categoryLabel;
            state = state == null ? AuctionState.ACTIVE : state;
            highestBid = highestBid == null ? BigDecimal.ZERO : highestBid.max(BigDecimal.ZERO);
            createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
            updatedAt = updatedAt == null ? createdAt : updatedAt;
            financialEvents = financialEvents == null ? List.of() : List.copyOf(financialEvents);
        }
    }
}
