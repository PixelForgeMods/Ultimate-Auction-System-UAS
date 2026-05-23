package net.austizz.ultimate_auction_system;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.math.BigDecimal;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    public static final long DEFAULT_LISTING_FEE_DOLLARS = 0L;
    public static final double DEFAULT_SALES_TAX_RATE = 0.05D;
    public static final long DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS = 1L;
    public static final int DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER = 25;
    public static final int DEFAULT_MAX_AUCTION_DURATION_HOURS = 168;
    public static final int DEFAULT_SETTLEMENT_RETRY_ATTEMPTS = 3;
    public static final int DEFAULT_SETTLEMENT_RETRY_DELAY_SECONDS = 60;
    public static final boolean DEFAULT_REQUIRE_UBS_FOR_LISTING = true;
    public static final boolean DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS = true;
    public static final int DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL = 2;

    public static final String ADMIN_RELOAD_FLOW = "Use the standard NeoForge config reload flow after editing UAS common config. "
            + "Economy fees, tax, bid increments, listing limits, and settlement retry settings are re-read on reload. "
            + "Existing auctions keep their original end time; max duration changes apply to newly-created listings.";

    private static final long MAX_MONEY_DOLLARS = 1_000_000_000L;
    private static final int MAX_LISTINGS_PER_PLAYER = 10_000;
    private static final int MAX_DURATION_HOURS = 24 * 365;
    private static final int MAX_SETTLEMENT_RETRY_ATTEMPTS = 20;
    private static final int MAX_SETTLEMENT_RETRY_DELAY_SECONDS = 86_400;
    private static final int MAX_PERMISSION_LEVEL = 4;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.LongValue LISTING_FEE_DOLLARS = BUILDER
            .comment(
                    "Whole UBS dollars charged when a player creates an auction listing.",
                    "0 disables listing fees. Runtime reload: applies to new listings only."
            )
            .defineInRange("economy.listingFeeDollars", DEFAULT_LISTING_FEE_DOLLARS, 0L, MAX_MONEY_DOLLARS);

    private static final ModConfigSpec.DoubleValue SALES_TAX_RATE = BUILDER
            .comment(
                    "Fraction of the final sale paid as sales tax. Example: 0.05 means 5%.",
                    "Use 0.0 to disable tax. Runtime reload: applies to future settlements."
            )
            .defineInRange("economy.salesTaxRate", DEFAULT_SALES_TAX_RATE, 0.0D, 1.0D);

    private static final ModConfigSpec.LongValue MINIMUM_BID_INCREMENT_DOLLARS = BUILDER
            .comment(
                    "Minimum whole UBS dollars that a new bid must exceed the current highest bid by.",
                    "Runtime reload: applies to newly accepted bids after reload."
            )
            .defineInRange("economy.minimumBidIncrementDollars", DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS, 1L, MAX_MONEY_DOLLARS);

    private static final ModConfigSpec.IntValue MAX_ACTIVE_LISTINGS_PER_PLAYER = BUILDER
            .comment(
                    "Maximum number of active auction listings one player may have at the same time.",
                    "Runtime reload: applies when players create new listings."
            )
            .defineInRange("limits.maxActiveListingsPerPlayer", DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER, 1, MAX_LISTINGS_PER_PLAYER);

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

    private static final ModConfigSpec.IntValue ADMIN_STATUS_PERMISSION_LEVEL = BUILDER
            .comment(
                    "Minecraft permission level required to run /uas status.",
                    "0 allows everyone; 2 matches normal operator command access; 4 restricts to server owner level."
            )
            .defineInRange("admin.statusPermissionLevel", DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static long listingFeeDollars;
    public static double salesTaxRate;
    public static long minimumBidIncrementDollars;
    public static int maxActiveListingsPerPlayer;
    public static int maxAuctionDurationHours;
    public static int settlementRetryAttempts;
    public static int settlementRetryDelaySeconds;
    public static boolean requireUbsForListing;
    public static boolean autoSettleExpiredAuctions;
    public static int adminStatusPermissionLevel;
    public static boolean lastConfigLoadHealthy = true;
    public static String lastConfigLoadMessage = "Config has not loaded yet.";

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        lastConfigLoadHealthy = true;
        lastConfigLoadMessage = "";

        listingFeeDollars = readLong("economy.listingFeeDollars", LISTING_FEE_DOLLARS, DEFAULT_LISTING_FEE_DOLLARS, 0L, MAX_MONEY_DOLLARS);
        salesTaxRate = readDouble("economy.salesTaxRate", SALES_TAX_RATE, DEFAULT_SALES_TAX_RATE, 0.0D, 1.0D);
        minimumBidIncrementDollars = readLong("economy.minimumBidIncrementDollars", MINIMUM_BID_INCREMENT_DOLLARS, DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS, 1L, MAX_MONEY_DOLLARS);
        maxActiveListingsPerPlayer = readInt("limits.maxActiveListingsPerPlayer", MAX_ACTIVE_LISTINGS_PER_PLAYER, DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER, 1, MAX_LISTINGS_PER_PLAYER);
        maxAuctionDurationHours = readInt("limits.maxAuctionDurationHours", MAX_AUCTION_DURATION_HOURS, DEFAULT_MAX_AUCTION_DURATION_HOURS, 1, MAX_DURATION_HOURS);
        settlementRetryAttempts = readInt("settlement.retryAttempts", SETTLEMENT_RETRY_ATTEMPTS, DEFAULT_SETTLEMENT_RETRY_ATTEMPTS, 0, MAX_SETTLEMENT_RETRY_ATTEMPTS);
        settlementRetryDelaySeconds = readInt("settlement.retryDelaySeconds", SETTLEMENT_RETRY_DELAY_SECONDS, DEFAULT_SETTLEMENT_RETRY_DELAY_SECONDS, 1, MAX_SETTLEMENT_RETRY_DELAY_SECONDS);
        requireUbsForListing = readBoolean("settlement.requireUbsForListing", REQUIRE_UBS_FOR_LISTING, DEFAULT_REQUIRE_UBS_FOR_LISTING);
        autoSettleExpiredAuctions = readBoolean("settlement.autoSettleExpiredAuctions", AUTO_SETTLE_EXPIRED_AUCTIONS, DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS);
        adminStatusPermissionLevel = readInt("admin.statusPermissionLevel", ADMIN_STATUS_PERMISSION_LEVEL, DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);

        if (lastConfigLoadHealthy) {
            lastConfigLoadMessage = event instanceof ModConfigEvent.Reloading
                    ? "Config reloaded. " + ADMIN_RELOAD_FLOW
                    : "Config loaded. " + ADMIN_RELOAD_FLOW;
        } else {
            lastConfigLoadMessage = "Config loaded with one or more safe defaults. " + lastConfigLoadMessage + " " + ADMIN_RELOAD_FLOW;
        }
    }

    public static BigDecimal listingFeeAmount() {
        return BigDecimal.valueOf(listingFeeDollars);
    }

    public static BigDecimal minimumBidIncrementAmount() {
        return BigDecimal.valueOf(minimumBidIncrementDollars);
    }

    public static BigDecimal calculateSalesTax(BigDecimal saleAmount) {
        BigDecimal amount = saleAmount == null ? BigDecimal.ZERO : saleAmount;
        return amount.multiply(BigDecimal.valueOf(salesTaxRate));
    }

    public static String getAdminReloadFlow() {
        return ADMIN_RELOAD_FLOW;
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
}
