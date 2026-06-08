package net.austizz.ultimate_auction_system;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuctionDataExporter {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final List<String> HEADERS = List.of(
            "auction_id",
            "item_ids",
            "item_names",
            "title",
            "description",
            "state",
            "starting_bid",
            "current_bid",
            "buyout_price",
            "reserve_price",
            "reserve_met",
            "date_start",
            "date_end",
            "created_at",
            "updated_at",
            "seller_uuid",
            "seller_account_uuid",
            "winner_uuid",
            "bid_count",
            "accepted_bid_count",
            "settlement_references",
            "financial_event_count"
    );

    private AuctionDataExporter() {
    }

    public enum Format {
        CSV("csv"),
        JSON("json");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        public static Optional<Format> fromToken(String token) {
            if (token == null) {
                return Optional.empty();
            }
            return switch (token.trim().toLowerCase(Locale.ROOT)) {
                case "csv" -> Optional.of(CSV);
                case "json" -> Optional.of(JSON);
                default -> Optional.empty();
            };
        }
    }

    public record ExportResult(boolean success, Path path, int auctionCount, String message) {
        static ExportResult ok(Path path, int auctionCount) {
            return new ExportResult(true, path, auctionCount, "Auction export completed.");
        }

        static ExportResult fail(String message) {
            return new ExportResult(false, null, 0, message == null || message.isBlank() ? "Auction export failed." : message);
        }
    }

    public static ExportResult export(Path serverRoot,
                                      Collection<AuctionItem> auctions,
                                      Format format,
                                      String requestedFilename) {
        if (format == null) {
            return ExportResult.fail("Export format must be csv or json.");
        }
        Path root = serverRoot == null ? Path.of(".") : serverRoot;
        Path exportDir = root.resolve("uas_exports").normalize();
        String filename = sanitizedFilename(requestedFilename, format);
        Path target = exportDir.resolve(filename).normalize();
        if (!target.startsWith(exportDir)) {
            return ExportResult.fail("Export path is outside the UAS export directory.");
        }

        List<AuctionItem> safeAuctions = auctions == null
                ? List.of()
                : auctions.stream()
                .filter(item -> item != null && item.getAuctionId() != null)
                .sorted(Comparator.comparing(AuctionItem::getCreatedAt).thenComparing(AuctionItem::getAuctionId))
                .toList();

        try {
            Files.createDirectories(exportDir);
            String body = format == Format.CSV ? toCsv(safeAuctions) : toJson(safeAuctions);
            Files.writeString(
                    target,
                    body,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            return ExportResult.ok(target.toAbsolutePath().normalize(), safeAuctions.size());
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage();
            return ExportResult.fail(message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
        }
    }

    static String sanitizedFilename(String requestedFilename, Format format) {
        String extension = format.extension();
        String raw = requestedFilename == null || requestedFilename.isBlank()
                ? "uas-auctions-" + LocalDateTime.now().format(FILE_STAMP)
                : requestedFilename.trim();
        raw = raw.replace('\\', '_').replace('/', '_').replace(':', '_');
        String sanitized = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.isBlank()) {
            sanitized = "uas-auctions-" + LocalDateTime.now().format(FILE_STAMP);
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (!lower.endsWith("." + extension)) {
            sanitized = sanitized + "." + extension;
        }
        return sanitized;
    }

    private static String toCsv(List<AuctionItem> auctions) {
        return toCsvRows(auctions.stream().map(AuctionDataExporter::row).toList());
    }

    static String toCsvRows(List<Map<String, String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", HEADERS)).append(System.lineSeparator());
        for (Map<String, String> row : rows == null ? List.<Map<String, String>>of() : rows) {
            for (int i = 0; i < HEADERS.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(csv(row.get(HEADERS.get(i))));
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private static String toJson(List<AuctionItem> auctions) {
        return toJsonRows(auctions.stream().map(AuctionDataExporter::row).toList());
    }

    static String toJsonRows(List<Map<String, String>> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        List<Map<String, String>> safeRows = rows == null ? List.of() : rows;
        for (int i = 0; i < safeRows.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(System.lineSeparator()).append("  {");
            Map<String, String> row = safeRows.get(i);
            int field = 0;
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (field++ > 0) {
                    builder.append(',');
                }
                builder.append(System.lineSeparator())
                        .append("    \"")
                        .append(json(entry.getKey()))
                        .append("\": \"")
                        .append(json(entry.getValue()))
                        .append("\"");
            }
            builder.append(System.lineSeparator()).append("  }");
        }
        if (!safeRows.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append("]").append(System.lineSeparator());
        return builder.toString();
    }

    private static Map<String, String> row(AuctionItem item) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        List<ItemStack> contents = item.getContents();
        List<AuctionBidRecord> bids = item.getBidRecords();
        List<AuctionFinancialEvent> financialEvents = item.getFinancialEvents();
        row.put("auction_id", string(item.getAuctionId()));
        row.put("item_ids", join(contents.stream().map(AuctionDataExporter::itemId).toList()));
        row.put("item_names", join(contents.stream().map(AuctionDataExporter::itemName).toList()));
        row.put("title", item.getDisplayTitle());
        row.put("description", item.getDescription());
        row.put("state", item.getState().name());
        row.put("starting_bid", money(item.getStartingBidPrice()));
        row.put("current_bid", money(item.getHighestBid()));
        row.put("buyout_price", item.getBuyoutPrice().map(AuctionDataExporter::money).orElse(""));
        row.put("reserve_price", item.getReservePrice().map(AuctionDataExporter::money).orElse(""));
        row.put("reserve_met", item.hasReservePrice() ? String.valueOf(item.isReserveMet()) : "");
        row.put("date_start", time(item.getDateOfStart()));
        row.put("date_end", time(item.getDateOfEnd()));
        row.put("created_at", time(item.getCreatedAt()));
        row.put("updated_at", time(item.getUpdatedAt()));
        row.put("seller_uuid", string(item.getPlayerId()));
        row.put("seller_account_uuid", string(item.getSellerAccountId()));
        row.put("winner_uuid", string(item.getHighestBidderId()));
        row.put("bid_count", String.valueOf(bids.size()));
        row.put("accepted_bid_count", String.valueOf(bids.stream().filter(AuctionBidRecord::isAccepted).count()));
        row.put("settlement_references", settlementReferences(bids, financialEvents));
        row.put("financial_event_count", String.valueOf(financialEvents.size()));
        return row;
    }

    private static String settlementReferences(List<AuctionBidRecord> bids, List<AuctionFinancialEvent> financialEvents) {
        Set<String> refs = new LinkedHashSet<>();
        for (AuctionBidRecord bid : bids) {
            bid.getSettlementReference().ifPresent(ref -> addReference(refs, ref));
        }
        for (AuctionFinancialEvent event : financialEvents) {
            addReference(refs, event.reference());
        }
        return join(new ArrayList<>(refs));
    }

    private static void addReference(Set<String> refs, String reference) {
        if (reference != null && !reference.isBlank()) {
            refs.add(reference.trim());
        }
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.getHoverName().getString();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(" | ", values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private static String string(UUID value) {
        return value == null ? "" : value.toString();
    }

    private static String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains("\"") || safe.contains(",") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String json(String value) {
        String safe = value == null ? "" : value;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }
}
