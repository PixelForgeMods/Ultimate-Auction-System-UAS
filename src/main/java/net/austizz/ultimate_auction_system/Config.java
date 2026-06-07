package net.austizz.ultimate_auction_system;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    public static final double DEFAULT_LISTING_FEE_RATE = 0.05D;
    public static final double DEFAULT_CANCELLATION_FEE_RATE = 0.0D;
    public static final double DEFAULT_SALES_TAX_RATE = 0.05D;
    public static final long DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS = 1L;
    public static final int DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER = 25;
    public static final int DEFAULT_MIN_AUCTION_DURATION_MINUTES = 5;
    public static final int DEFAULT_MAX_AUCTION_DURATION_HOURS = 168;
    public static final int DEFAULT_SETTLEMENT_RETRY_ATTEMPTS = 3;
    public static final int DEFAULT_SETTLEMENT_RETRY_DELAY_SECONDS = 60;
    public static final boolean DEFAULT_REQUIRE_UBS_FOR_LISTING = true;
    public static final boolean DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS = true;
    public static final boolean DEFAULT_AUDIT_REJECTED_BIDS = true;
    public static final boolean DEFAULT_AUDIT_STATE_TRANSITIONS = true;
    public static final boolean DEFAULT_ALLOW_SELLER_SELF_BID = false;
    public static final int DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL = 2;
    public static final int DEFAULT_AUTOSAVE_INTERVAL_TICKS = 6000;
    public static final int DEFAULT_PENDING_LISTING_CONFIRMATION_SECONDS = 60;
    public static final List<String> DEFAULT_BANNED_AUCTION_ENTRIES = List.of();

    public static final String ADMIN_RELOAD_FLOW = "Use the standard NeoForge config reload flow after editing UAS common config. "
            + "Economy fees, tax, bid increments, listing limits, duration bounds, banned item restrictions, and settlement retry settings are re-read on reload. "
            + "Existing auctions keep their original end time and item; duration and restriction changes apply to newly-created listings.";

    private static final long MAX_MONEY_DOLLARS = 1_000_000_000L;
    private static final int MAX_LISTINGS_PER_PLAYER = 10_000;
    private static final int MAX_DURATION_HOURS = 24 * 365;
    private static final int MAX_DURATION_MINUTES = MAX_DURATION_HOURS * 60;
    private static final int MAX_SETTLEMENT_RETRY_ATTEMPTS = 20;
    private static final int MAX_SETTLEMENT_RETRY_DELAY_SECONDS = 86_400;
    private static final int MAX_PERMISSION_LEVEL = 4;
    private static final int MIN_AUTOSAVE_INTERVAL_TICKS = 20;
    private static final int MAX_AUTOSAVE_INTERVAL_TICKS = 72_000;
    private static final int MIN_PENDING_CONFIRMATION_SECONDS = 10;
    private static final int MAX_PENDING_CONFIRMATION_SECONDS = 600;
    private static final Pattern ITEM_OR_TAG_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern MOD_ID_ENTRY = Pattern.compile("@[a-z0-9_.-]+");

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue LISTING_FEE_RATE = BUILDER
            .comment(
                    "Fraction of the starting bid charged when a player creates an auction listing. Example: 0.05 means 5%.",
                    "0 disables listing fees. Runtime reload: applies to new listings only."
            )
            .defineInRange("economy.listingFeeRate", DEFAULT_LISTING_FEE_RATE, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue SALES_TAX_RATE = BUILDER
            .comment(
                    "Fraction of the final sale paid as sales tax. Example: 0.05 means 5%.",
                    "Use 0.0 to disable tax. Runtime reload: applies to future settlements."
            )
            .defineInRange("economy.salesTaxRate", DEFAULT_SALES_TAX_RATE, 0.0D, 1.0D);

    private static final ModConfigSpec.DoubleValue CANCELLATION_FEE_RATE = BUILDER
            .comment(
                    "Fraction of the starting bid charged when a seller cancels their own no-bid auction.",
                    "0 disables seller cancellation fees. Admin force-cancel never charges this fee."
            )
            .defineInRange("economy.cancellationFeeRate", DEFAULT_CANCELLATION_FEE_RATE, 0.0D, 1.0D);

    private static final ModConfigSpec.LongValue MINIMUM_BID_INCREMENT_DOLLARS = BUILDER
            .comment(
                    "Minimum whole UBS dollars that a new bid must exceed the current highest bid by.",
                    "Runtime reload: applies to newly accepted bids after reload."
            )
            .defineInRange("economy.minimumBidIncrementDollars", DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS, 1L, MAX_MONEY_DOLLARS);

    private static final ModConfigSpec.BooleanValue ALLOW_SELLER_SELF_BID = BUILDER
            .comment(
                    "When true, sellers may bid on their own auctions.",
                    "Default false protects normal auction-house fairness. Runtime reload: applies to future bids."
            )
            .define("bidding.allowSellerSelfBid", DEFAULT_ALLOW_SELLER_SELF_BID);

    private static final ModConfigSpec.IntValue MAX_ACTIVE_LISTINGS_PER_PLAYER = BUILDER
            .comment(
                    "Maximum number of active auction listings one player may have at the same time.",
                    "Runtime reload: applies when players create new listings."
            )
            .defineInRange("limits.maxActiveListingsPerPlayer", DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER, 1, MAX_LISTINGS_PER_PLAYER);

    private static final ModConfigSpec.IntValue MIN_AUCTION_DURATION_MINUTES = BUILDER
            .comment(
                    "Minimum auction duration, in real-world minutes, allowed for new listings.",
                    "Runtime reload: applies to newly-created listings."
            )
            .defineInRange("limits.minAuctionDurationMinutes", DEFAULT_MIN_AUCTION_DURATION_MINUTES, 5, MAX_DURATION_MINUTES);

    private static final ModConfigSpec.IntValue MAX_AUCTION_DURATION_HOURS = BUILDER
            .comment(
                    "Maximum auction duration, in real-world hours, allowed for new listings.",
                    "Runtime reload: existing auctions keep their previously calculated end time."
            )
            .defineInRange("limits.maxAuctionDurationHours", DEFAULT_MAX_AUCTION_DURATION_HOURS, 1, MAX_DURATION_HOURS);

    private static final ModConfigSpec.IntValue SETTLEMENT_RETRY_ATTEMPTS = BUILDER
            .comment(
                    "Number of automatic settlement retries after a temporary UBS/payment failure.",
                    "0 disables retries. Runtime reload: applies to future retry scheduling."
            )
            .defineInRange("settlement.retryAttempts", DEFAULT_SETTLEMENT_RETRY_ATTEMPTS, 0, MAX_SETTLEMENT_RETRY_ATTEMPTS);

    private static final ModConfigSpec.IntValue SETTLEMENT_RETRY_DELAY_SECONDS = BUILDER
            .comment(
                    "Delay between settlement retry attempts, in seconds.",
                    "Runtime reload: applies to retry attempts scheduled after reload."
            )
            .defineInRange("settlement.retryDelaySeconds", DEFAULT_SETTLEMENT_RETRY_DELAY_SECONDS, 1, MAX_SETTLEMENT_RETRY_DELAY_SECONDS);

    private static final ModConfigSpec.BooleanValue REQUIRE_UBS_FOR_LISTING = BUILDER
            .comment(
                    "When true, sellers must have a usable UBS primary account before creating listings.",
                    "Runtime reload: applies to future listing attempts."
            )
            .define("settlement.requireUbsForListing", DEFAULT_REQUIRE_UBS_FOR_LISTING);

    private static final ModConfigSpec.BooleanValue AUTO_SETTLE_EXPIRED_AUCTIONS = BUILDER
            .comment(
                    "When true, expired auctions should be settled automatically by UAS settlement logic.",
                    "Runtime reload: applies to future settlement scans."
            )
            .define("settlement.autoSettleExpiredAuctions", DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS);

    private static final ModConfigSpec.BooleanValue AUDIT_REJECTED_BIDS = BUILDER
            .comment(
                    "When true, rejected bid attempts are persisted with reason codes for admin audit flows.",
                    "Accepted bids are always persisted."
            )
            .define("audit.rejectedBids", DEFAULT_AUDIT_REJECTED_BIDS);

    private static final ModConfigSpec.BooleanValue AUDIT_STATE_TRANSITIONS = BUILDER
            .comment(
                    "When true, auction state transitions and rejected transitions are logged for admin audits.",
                    "State values are always persisted with each auction record."
            )
            .define("audit.stateTransitions", DEFAULT_AUDIT_STATE_TRANSITIONS);

    private static final ModConfigSpec.IntValue ADMIN_STATUS_PERMISSION_LEVEL = BUILDER
            .comment(
                    "Minecraft permission level required to run /uas status.",
                    "0 allows everyone; 2 matches normal operator command access; 4 restricts to server owner level."
            )
            .defineInRange("admin.statusPermissionLevel", DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);

    private static final ModConfigSpec.IntValue AUTOSAVE_INTERVAL_TICKS = BUILDER
            .comment(
                    "Tick interval for UAS auction SavedData autosaves.",
                    "20 ticks is roughly one second. Default 6000 is roughly five minutes."
            )
            .defineInRange("storage.autosaveIntervalTicks", DEFAULT_AUTOSAVE_INTERVAL_TICKS, MIN_AUTOSAVE_INTERVAL_TICKS, MAX_AUTOSAVE_INTERVAL_TICKS);

    private static final ModConfigSpec.IntValue PENDING_LISTING_CONFIRMATION_SECONDS = BUILDER
            .comment(
                    "Seconds before a pending auction listing confirmation expires.",
                    "Pending confirmations are per-player and are not saved across restarts."
            )
            .defineInRange("limits.pendingListingConfirmationSeconds", DEFAULT_PENDING_LISTING_CONFIRMATION_SECONDS, MIN_PENDING_CONFIRMATION_SECONDS, MAX_PENDING_CONFIRMATION_SECONDS);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BANNED_AUCTION_ENTRIES = BUILDER
            .comment(
                    "Item ids or item tags that cannot be listed in auctions.",
                    "Use minecraft:bedrock for a single item or #minecraft:shulker_boxes for a tag.",
                    "Use @modid to ban every item from a mod, for example @minecraft.",
                    "Runtime reload: applies to newly-created listings."
            )
            .defineListAllowEmpty("limits.bannedAuctionEntries", DEFAULT_BANNED_AUCTION_ENTRIES, () -> "", Config::validateAuctionRestriction);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double listingFeeRate;
    public static double cancellationFeeRate = DEFAULT_CANCELLATION_FEE_RATE;
    public static double salesTaxRate;
    public static long minimumBidIncrementDollars;
    public static int maxActiveListingsPerPlayer;
    public static int minAuctionDurationMinutes;
    public static int maxAuctionDurationHours;
    public static int settlementRetryAttempts;
    public static int settlementRetryDelaySeconds;
    public static boolean requireUbsForListing;
    public static boolean autoSettleExpiredAuctions;
    public static boolean auditRejectedBids;
    public static boolean auditStateTransitions;
    public static boolean allowSellerSelfBid;
    public static int adminStatusPermissionLevel;
    public static int autosaveIntervalTicks;
    public static int pendingListingConfirmationSeconds = DEFAULT_PENDING_LISTING_CONFIRMATION_SECONDS;
    public static List<String> bannedAuctionEntries = new ArrayList<>();
    public static boolean lastConfigLoadHealthy = true;
    public static String lastConfigLoadMessage = "Config has not loaded yet.";

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        lastConfigLoadHealthy = true;
        lastConfigLoadMessage = "";

        listingFeeRate = readDouble("economy.listingFeeRate", LISTING_FEE_RATE, DEFAULT_LISTING_FEE_RATE, 0.0D, 1.0D);
        cancellationFeeRate = readDouble("economy.cancellationFeeRate", CANCELLATION_FEE_RATE, DEFAULT_CANCELLATION_FEE_RATE, 0.0D, 1.0D);
        salesTaxRate = readDouble("economy.salesTaxRate", SALES_TAX_RATE, DEFAULT_SALES_TAX_RATE, 0.0D, 1.0D);
        minimumBidIncrementDollars = readLong("economy.minimumBidIncrementDollars", MINIMUM_BID_INCREMENT_DOLLARS, DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS, 1L, MAX_MONEY_DOLLARS);
        maxActiveListingsPerPlayer = readInt("limits.maxActiveListingsPerPlayer", MAX_ACTIVE_LISTINGS_PER_PLAYER, DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER, 1, MAX_LISTINGS_PER_PLAYER);
        minAuctionDurationMinutes = readInt("limits.minAuctionDurationMinutes", MIN_AUCTION_DURATION_MINUTES, DEFAULT_MIN_AUCTION_DURATION_MINUTES, 5, MAX_DURATION_MINUTES);
        int configuredMaxHours = readInt("limits.maxAuctionDurationHours", MAX_AUCTION_DURATION_HOURS, DEFAULT_MAX_AUCTION_DURATION_HOURS, 1, MAX_DURATION_HOURS);
        maxAuctionDurationHours = Math.max((int) Math.ceil(minAuctionDurationMinutes / 60.0D), configuredMaxHours);
        settlementRetryAttempts = readInt("settlement.retryAttempts", SETTLEMENT_RETRY_ATTEMPTS, DEFAULT_SETTLEMENT_RETRY_ATTEMPTS, 0, MAX_SETTLEMENT_RETRY_ATTEMPTS);
        settlementRetryDelaySeconds = readInt("settlement.retryDelaySeconds", SETTLEMENT_RETRY_DELAY_SECONDS, DEFAULT_SETTLEMENT_RETRY_DELAY_SECONDS, 1, MAX_SETTLEMENT_RETRY_DELAY_SECONDS);
        requireUbsForListing = readBoolean("settlement.requireUbsForListing", REQUIRE_UBS_FOR_LISTING, DEFAULT_REQUIRE_UBS_FOR_LISTING);
        autoSettleExpiredAuctions = readBoolean("settlement.autoSettleExpiredAuctions", AUTO_SETTLE_EXPIRED_AUCTIONS, DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS);
        auditRejectedBids = readBoolean("audit.rejectedBids", AUDIT_REJECTED_BIDS, DEFAULT_AUDIT_REJECTED_BIDS);
        auditStateTransitions = readBoolean("audit.stateTransitions", AUDIT_STATE_TRANSITIONS, DEFAULT_AUDIT_STATE_TRANSITIONS);
        allowSellerSelfBid = readBoolean("bidding.allowSellerSelfBid", ALLOW_SELLER_SELF_BID, DEFAULT_ALLOW_SELLER_SELF_BID);
        adminStatusPermissionLevel = readInt("admin.statusPermissionLevel", ADMIN_STATUS_PERMISSION_LEVEL, DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);
        autosaveIntervalTicks = readInt("storage.autosaveIntervalTicks", AUTOSAVE_INTERVAL_TICKS, DEFAULT_AUTOSAVE_INTERVAL_TICKS, MIN_AUTOSAVE_INTERVAL_TICKS, MAX_AUTOSAVE_INTERVAL_TICKS);
        pendingListingConfirmationSeconds = readInt("limits.pendingListingConfirmationSeconds", PENDING_LISTING_CONFIRMATION_SECONDS, DEFAULT_PENDING_LISTING_CONFIRMATION_SECONDS, MIN_PENDING_CONFIRMATION_SECONDS, MAX_PENDING_CONFIRMATION_SECONDS);
        bannedAuctionEntries = new ArrayList<>(BANNED_AUCTION_ENTRIES.get());

        if (lastConfigLoadHealthy) {
            lastConfigLoadMessage = event instanceof ModConfigEvent.Reloading
                    ? "Config reloaded. " + ADMIN_RELOAD_FLOW
                    : "Config loaded. " + ADMIN_RELOAD_FLOW;
        } else {
            lastConfigLoadMessage = "Config loaded with one or more safe defaults. " + lastConfigLoadMessage + " " + ADMIN_RELOAD_FLOW;
        }
    }

    public static BigDecimal calculateListingFee(BigDecimal startingBid) {
        BigDecimal amount = startingBid == null ? BigDecimal.ZERO : startingBid.max(BigDecimal.ZERO);
        return amount.multiply(BigDecimal.valueOf(listingFeeRate)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateCancellationFee(BigDecimal startingBid) {
        BigDecimal amount = startingBid == null ? BigDecimal.ZERO : startingBid.max(BigDecimal.ZERO);
        return amount.multiply(BigDecimal.valueOf(cancellationFeeRate)).setScale(2, RoundingMode.HALF_UP);
    }

    public static String listingFeePercentLabel() {
        return BigDecimal.valueOf(listingFeeRate)
                .multiply(BigDecimal.valueOf(100L))
                .stripTrailingZeros()
                .toPlainString();
    }

    public static BigDecimal minimumBidIncrementAmount() {
        return BigDecimal.valueOf(minimumBidIncrementDollars);
    }

    public static Duration minimumAuctionDuration() {
        return Duration.ofMinutes(minAuctionDurationMinutes);
    }

    public static BigDecimal calculateSalesTax(BigDecimal saleAmount) {
        BigDecimal amount = saleAmount == null ? BigDecimal.ZERO : saleAmount;
        return amount.multiply(BigDecimal.valueOf(salesTaxRate)).setScale(2, RoundingMode.HALF_UP);
    }

    public static String getAdminReloadFlow() {
        return ADMIN_RELOAD_FLOW;
    }

    public static synchronized AuctionActionResult replaceBannedAuctionEntries(List<String> rawEntries) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rawEntries != null) {
            for (String raw : rawEntries) {
                String entry = normalizeAuctionRestriction(raw);
                if (entry.isBlank()) {
                    continue;
                }
                if (!isValidAuctionRestriction(entry)) {
                    return AuctionActionResult.fail("Invalid banned auction entry: " + raw);
                }
                normalized.add(entry);
            }
        }

        List<String> next = List.copyOf(normalized);
        try {
            BANNED_AUCTION_ENTRIES.set(next);
            bannedAuctionEntries = new ArrayList<>(next);
            SPEC.save();
            lastConfigLoadHealthy = true;
            lastConfigLoadMessage = "Banned auction entries updated from the admin dashboard.";
            return AuctionActionResult.ok("Banned auction entries updated.");
        } catch (RuntimeException exception) {
            lastConfigLoadHealthy = false;
            lastConfigLoadMessage = "Could not save banned auction entries: " + exception.getMessage();
            return AuctionActionResult.fail(lastConfigLoadMessage);
        }
    }

    public static String normalizeAuctionRestriction(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    public static boolean isValidAuctionRestriction(String raw) {
        String entry = normalizeAuctionRestriction(raw);
        if (entry.isBlank()) {
            return false;
        }
        if (entry.startsWith("@")) {
            return MOD_ID_ENTRY.matcher(entry).matches();
        }
        String location = entry.startsWith("#") ? entry.substring(1) : entry;
        return ITEM_OR_TAG_ID.matcher(location).matches();
    }

    public static String auctionRestrictionType(String raw) {
        String entry = normalizeAuctionRestriction(raw);
        if (entry.startsWith("@")) {
            return "Mod";
        }
        if (entry.startsWith("#")) {
            return "Tag";
        }
        return "Item";
    }

    private static long readLong(String path, ModConfigSpec.LongValue value, long fallback, long min, long max) {
        try {
            long resolved = value.get();
            if (resolved < min || resolved > max) {
                return fallbackLong(path, value, fallback);
            }
            return resolved;
        } catch (RuntimeException exception) {
            return fallbackLong(path, value, fallback);
        }
    }

    private static int readInt(String path, ModConfigSpec.IntValue value, int fallback, int min, int max) {
        try {
            int resolved = value.get();
            if (resolved < min || resolved > max) {
                return fallbackInt(path, value, fallback);
            }
            return resolved;
        } catch (RuntimeException exception) {
            return fallbackInt(path, value, fallback);
        }
    }

    private static double readDouble(String path, ModConfigSpec.DoubleValue value, double fallback, double min, double max) {
        try {
            double resolved = value.get();
            if (Double.isNaN(resolved) || resolved < min || resolved > max) {
                return fallbackDouble(path, value, fallback);
            }
            return resolved;
        } catch (RuntimeException exception) {
            return fallbackDouble(path, value, fallback);
        }
    }

    private static boolean readBoolean(String path, ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (RuntimeException exception) {
            lastConfigLoadHealthy = false;
            lastConfigLoadMessage = "Invalid config value for " + path + "; using safe default.";
            value.set(fallback);
            return fallback;
        }
    }

    private static long fallbackLong(String path, ModConfigSpec.LongValue value, long fallback) {
        lastConfigLoadHealthy = false;
        lastConfigLoadMessage = "Invalid config value for " + path + "; using safe default.";
        value.set(fallback);
        return fallback;
    }

    private static int fallbackInt(String path, ModConfigSpec.IntValue value, int fallback) {
        lastConfigLoadHealthy = false;
        lastConfigLoadMessage = "Invalid config value for " + path + "; using safe default.";
        value.set(fallback);
        return fallback;
    }

    private static double fallbackDouble(String path, ModConfigSpec.DoubleValue value, double fallback) {
        lastConfigLoadHealthy = false;
        lastConfigLoadMessage = "Invalid config value for " + path + "; using safe default.";
        value.set(fallback);
        return fallback;
    }

    private static boolean validateAuctionRestriction(Object value) {
        if (!(value instanceof String raw)) {
            return false;
        }
        return isValidAuctionRestriction(raw);
    }
}
