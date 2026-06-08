package net.austizz.ultimate_auction_system;

import net.austizz.ultimate_auction_system.banking.UasCashSettlementUse;
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
import java.util.Optional;
import java.util.UUID;
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
    public static final int DEFAULT_CREATE_COOLDOWN_SECONDS = 5;
    public static final int DEFAULT_BID_COOLDOWN_SECONDS = 2;
    public static final int DEFAULT_BUYOUT_COOLDOWN_SECONDS = 2;
    public static final int DEFAULT_CANCEL_COOLDOWN_SECONDS = 5;
    public static final int DEFAULT_SEARCH_COOLDOWN_SECONDS = 1;
    public static final int DEFAULT_MAX_WATCHED_AUCTIONS_PER_PLAYER = 64;
    public static final int DEFAULT_WATCH_ENDING_SOON_THRESHOLD_MINUTES = 60;
    public static final int DEFAULT_MAX_SAVED_SEARCHES_PER_PLAYER = 12;
    public static final boolean DEFAULT_REQUIRE_UBS_FOR_LISTING = true;
    public static final boolean DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS = true;
    public static final boolean DEFAULT_PHYSICAL_CASH_LISTING_FEES = false;
    public static final boolean DEFAULT_PHYSICAL_CASH_BUYOUTS = false;
    public static final boolean DEFAULT_CHEQUE_PAYOUTS = false;
    public static final long DEFAULT_CHEQUE_PAYOUT_MINIMUM_DOLLARS = 0L;
    public static final String DEFAULT_CHEQUE_PAYOUT_ISSUER_NAME = "Auction House";
    public static final boolean DEFAULT_AUDIT_REJECTED_BIDS = true;
    public static final boolean DEFAULT_AUDIT_STATE_TRANSITIONS = true;
    public static final boolean DEFAULT_AUDIT_SUSPICIOUS_BID_PATTERNS = true;
    public static final boolean DEFAULT_AUDIT_SELLER_SELF_BID_SIGNALS = true;
    public static final boolean DEFAULT_AUDIT_EXTERNAL_SUSPICION_SIGNAL_HOOKS = false;
    public static final int DEFAULT_SUSPICIOUS_RAPID_BID_WINDOW_SECONDS = 300;
    public static final int DEFAULT_SUSPICIOUS_RAPID_BID_COUNT = 4;
    public static final int DEFAULT_SUSPICIOUS_REPEATED_BIDDER_PAIR_COUNT = 3;
    public static final int DEFAULT_SUSPICIOUS_CANCEL_WINDOW_HOURS = 24;
    public static final int DEFAULT_SUSPICIOUS_CANCEL_COUNT = 3;
    public static final boolean DEFAULT_ALLOW_SELLER_SELF_BID = false;
    public static final int DEFAULT_LIST_PERMISSION_LEVEL = 0;
    public static final int DEFAULT_BID_PERMISSION_LEVEL = 0;
    public static final int DEFAULT_BUYOUT_PERMISSION_LEVEL = 0;
    public static final int DEFAULT_CANCEL_OWN_PERMISSION_LEVEL = 0;
    public static final int DEFAULT_CLAIM_PERMISSION_LEVEL = 0;
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
    private static final int MAX_RATE_LIMIT_SECONDS = 3_600;
    private static final int MAX_AUDIT_WINDOW_SECONDS = 86_400;
    private static final int MAX_AUDIT_WINDOW_HOURS = 24 * 30;
    private static final int MAX_AUDIT_SIGNAL_COUNT = 100;
    private static final int MAX_PERMISSION_LEVEL = 4;
    private static final int MAX_WATCHED_AUCTIONS_PER_PLAYER = 1_000;
    private static final int MAX_WATCH_ENDING_SOON_THRESHOLD_MINUTES = 24 * 60;
    private static final int MAX_SAVED_SEARCHES_PER_PLAYER = 100;
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

    private static final ModConfigSpec.ConfigValue<String> SALES_TAX_DESTINATION_ACCOUNT_UUID = BUILDER
            .comment(
                    "Optional UBS account UUID that receives sales tax.",
                    "Leave blank to keep sales tax as a money sink deducted from seller proceeds.",
                    "Runtime reload: applies to future settlements."
            )
            .define("economy.salesTaxDestinationAccountUuid", "");

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

    private static final ModConfigSpec.IntValue CREATE_COOLDOWN_SECONDS = BUILDER
            .comment(
                    "Per-player cooldown for creating auction listings, in seconds.",
                    "Admins with the UAS admin permission bypass rate limits. 0 disables this cooldown."
            )
            .defineInRange("rateLimits.createCooldownSeconds", DEFAULT_CREATE_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);

    private static final ModConfigSpec.IntValue BID_COOLDOWN_SECONDS = BUILDER
            .comment(
                    "Per-player cooldown for placing bids, in seconds.",
                    "Admins with the UAS admin permission bypass rate limits. 0 disables this cooldown."
            )
            .defineInRange("rateLimits.bidCooldownSeconds", DEFAULT_BID_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);

    private static final ModConfigSpec.IntValue BUYOUT_COOLDOWN_SECONDS = BUILDER
            .comment(
                    "Per-player cooldown for buyout actions, in seconds.",
                    "Admins with the UAS admin permission bypass rate limits. 0 disables this cooldown."
            )
            .defineInRange("rateLimits.buyoutCooldownSeconds", DEFAULT_BUYOUT_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);

    private static final ModConfigSpec.IntValue CANCEL_COOLDOWN_SECONDS = BUILDER
            .comment(
                    "Per-player cooldown for seller cancellation actions, in seconds.",
                    "Admins with the UAS admin permission bypass rate limits. 0 disables this cooldown."
            )
            .defineInRange("rateLimits.cancelCooldownSeconds", DEFAULT_CANCEL_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);

    private static final ModConfigSpec.IntValue SEARCH_COOLDOWN_SECONDS = BUILDER
            .comment(
                    "Per-player cooldown for auction search/list refresh actions, in seconds.",
                    "Admins with the UAS admin permission bypass rate limits. 0 disables this cooldown."
            )
            .defineInRange("rateLimits.searchCooldownSeconds", DEFAULT_SEARCH_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);

    private static final ModConfigSpec.IntValue MAX_WATCHED_AUCTIONS = BUILDER
            .comment(
                    "Maximum active auctions one player can watch for notifications at the same time.",
                    "Runtime reload: applies when players watch new auctions. Existing watches can still be unwatched."
            )
            .defineInRange("notifications.maxWatchedAuctionsPerPlayer", DEFAULT_MAX_WATCHED_AUCTIONS_PER_PLAYER, 1, MAX_WATCHED_AUCTIONS_PER_PLAYER);

    private static final ModConfigSpec.IntValue WATCH_ENDING_SOON_THRESHOLD_MINUTES = BUILDER
            .comment(
                    "Minutes before auction end when watched auctions send their one-time ending-soon notification.",
                    "0 disables ending-soon watch notifications. Sold, cancelled, and bid-update notifications still work."
            )
            .defineInRange("notifications.endingSoonThresholdMinutes", DEFAULT_WATCH_ENDING_SOON_THRESHOLD_MINUTES, 0, MAX_WATCH_ENDING_SOON_THRESHOLD_MINUTES);

    private static final ModConfigSpec.IntValue MAX_SAVED_SEARCHES = BUILDER
            .comment(
                    "Maximum named auction browser filter presets one player can save.",
                    "Runtime reload: applies when players create new saved searches."
            )
            .defineInRange("marketplace.maxSavedSearchesPerPlayer", DEFAULT_MAX_SAVED_SEARCHES_PER_PLAYER, 1, MAX_SAVED_SEARCHES_PER_PLAYER);

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

    private static final ModConfigSpec.BooleanValue PHYSICAL_CASH_LISTING_FEES = BUILDER
            .comment(
                    "When true, future command/API paths may pay listing fees with exact UBS bills/coins instead of account balance.",
                    "Default false keeps normal account settlement unchanged. GUI and existing /ah commands still use UBS accounts."
            )
            .define("settlement.physicalCashListingFees", DEFAULT_PHYSICAL_CASH_LISTING_FEES);

    private static final ModConfigSpec.BooleanValue PHYSICAL_CASH_BUYOUTS = BUILDER
            .comment(
                    "When true, future command/API paths may pay buyouts with exact UBS bills/coins instead of account balance.",
                    "Default false keeps normal account settlement unchanged. GUI and existing /ah commands still use UBS accounts."
            )
            .define("settlement.physicalCashBuyouts", DEFAULT_PHYSICAL_CASH_BUYOUTS);

    private static final ModConfigSpec.BooleanValue CHEQUE_PAYOUTS = BUILDER
            .comment(
                    "When true, seller payouts at or above settlement.chequePayoutMinimumDollars are issued as UBS cheques when possible.",
                    "Requires settlement.chequePayoutSourceAccountUuid. Default false keeps direct UBS account deposits as the payout path."
            )
            .define("settlement.chequePayouts", DEFAULT_CHEQUE_PAYOUTS);

    private static final ModConfigSpec.ConfigValue<String> CHEQUE_PAYOUT_SOURCE_ACCOUNT_UUID = BUILDER
            .comment(
                    "UBS account UUID debited when UAS issues seller payout cheques.",
                    "Leave blank unless settlement.chequePayouts is enabled. Runtime reload: applies to future settlements."
            )
            .define("settlement.chequePayoutSourceAccountUuid", "");

    private static final ModConfigSpec.LongValue CHEQUE_PAYOUT_MINIMUM_DOLLARS = BUILDER
            .comment(
                    "Minimum whole-dollar net seller payout that uses UBS cheque payout when settlement.chequePayouts is enabled.",
                    "Set 0 to cheque every whole-dollar seller payout. Fractional-dollar payouts continue through normal account deposit."
            )
            .defineInRange("settlement.chequePayoutMinimumDollars", DEFAULT_CHEQUE_PAYOUT_MINIMUM_DOLLARS, 0L, MAX_MONEY_DOLLARS);

    private static final ModConfigSpec.ConfigValue<String> CHEQUE_PAYOUT_ISSUER_PLAYER_UUID = BUILDER
            .comment(
                    "Optional player UUID stored as the UBS cheque writer.",
                    "Leave blank to issue cheques with only settlement.chequePayoutIssuerName as the writer identity."
            )
            .define("settlement.chequePayoutIssuerPlayerUuid", "");

    private static final ModConfigSpec.ConfigValue<String> CHEQUE_PAYOUT_ISSUER_NAME = BUILDER
            .comment(
                    "Display name stored as the UBS cheque writer when UAS issues seller payout cheques.",
                    "Runtime reload: applies to future cheque payouts."
            )
            .define("settlement.chequePayoutIssuerName", DEFAULT_CHEQUE_PAYOUT_ISSUER_NAME);

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

    private static final ModConfigSpec.BooleanValue AUDIT_SUSPICIOUS_BID_PATTERNS = BUILDER
            .comment(
                    "When true, UAS logs non-punitive suspicion signals for bid abuse investigation.",
                    "Signals are evidence for admins only; they never ban, cancel, or punish automatically."
            )
            .define("audit.suspiciousBidPatterns", DEFAULT_AUDIT_SUSPICIOUS_BID_PATTERNS);

    private static final ModConfigSpec.IntValue SUSPICIOUS_RAPID_BID_WINDOW_SECONDS = BUILDER
            .comment(
                    "Time window used for rapid bid escalation suspicion signals, in seconds.",
                    "Lower values are stricter. Runtime reload: applies to future audit checks."
            )
            .defineInRange("audit.suspiciousRapidBidWindowSeconds", DEFAULT_SUSPICIOUS_RAPID_BID_WINDOW_SECONDS, 30, MAX_AUDIT_WINDOW_SECONDS);

    private static final ModConfigSpec.IntValue SUSPICIOUS_RAPID_BID_COUNT = BUILDER
            .comment(
                    "Accepted bid count inside the rapid-bid window before a suspicion signal is logged.",
                    "Runtime reload: applies to future audit checks."
            )
            .defineInRange("audit.suspiciousRapidBidCount", DEFAULT_SUSPICIOUS_RAPID_BID_COUNT, 2, MAX_AUDIT_SIGNAL_COUNT);

    private static final ModConfigSpec.IntValue SUSPICIOUS_REPEATED_BIDDER_PAIR_COUNT = BUILDER
            .comment(
                    "Number of alternating outbid turns between the same two bidders before UAS logs a repeated-pair signal.",
                    "Runtime reload: applies to future audit checks."
            )
            .defineInRange("audit.suspiciousRepeatedBidderPairCount", DEFAULT_SUSPICIOUS_REPEATED_BIDDER_PAIR_COUNT, 2, MAX_AUDIT_SIGNAL_COUNT);

    private static final ModConfigSpec.IntValue SUSPICIOUS_CANCEL_WINDOW_HOURS = BUILDER
            .comment(
                    "Window used to flag repeated seller cancellations, in hours.",
                    "Runtime reload: applies to future audit checks."
            )
            .defineInRange("audit.suspiciousCancelWindowHours", DEFAULT_SUSPICIOUS_CANCEL_WINDOW_HOURS, 1, MAX_AUDIT_WINDOW_HOURS);

    private static final ModConfigSpec.IntValue SUSPICIOUS_CANCEL_COUNT = BUILDER
            .comment(
                    "Cancelled listing count by the same seller inside the cancel window before a suspicion signal is logged.",
                    "Runtime reload: applies to future audit checks."
            )
            .defineInRange("audit.suspiciousCancelCount", DEFAULT_SUSPICIOUS_CANCEL_COUNT, 2, MAX_AUDIT_SIGNAL_COUNT);

    private static final ModConfigSpec.BooleanValue AUDIT_SELLER_SELF_BID_SIGNALS = BUILDER
            .comment(
                    "When true, seller self-bid attempts and accepted self-bids are logged as suspicion signals.",
                    "This is independent of bidding.allowSellerSelfBid and never punishes automatically."
            )
            .define("audit.sellerSelfBidSignals", DEFAULT_AUDIT_SELLER_SELF_BID_SIGNALS);

    private static final ModConfigSpec.BooleanValue AUDIT_EXTERNAL_SUSPICION_SIGNAL_HOOKS = BUILDER
            .comment(
                    "Reserved hook toggle for future integrations that can provide same-IP or known-collaborator signals.",
                    "UAS does not collect or persist IP addresses itself. Keep false unless another trusted integration supplies privacy-reviewed signals."
            )
            .define("audit.externalSuspicionSignalHooks", DEFAULT_AUDIT_EXTERNAL_SUSPICION_SIGNAL_HOOKS);

    private static final ModConfigSpec.IntValue LIST_PERMISSION_LEVEL = permissionLevel(
            "permissions.listPermissionLevel",
            "Minecraft permission level required to create auction listings. 0 allows everyone."
    );

    private static final ModConfigSpec.IntValue BID_PERMISSION_LEVEL = permissionLevel(
            "permissions.bidPermissionLevel",
            "Minecraft permission level required to place normal bids. 0 allows everyone."
    );

    private static final ModConfigSpec.IntValue BUYOUT_PERMISSION_LEVEL = permissionLevel(
            "permissions.buyoutPermissionLevel",
            "Minecraft permission level required to buy out auctions. 0 allows everyone."
    );

    private static final ModConfigSpec.IntValue CANCEL_OWN_PERMISSION_LEVEL = permissionLevel(
            "permissions.cancelOwnPermissionLevel",
            "Minecraft permission level required for sellers to cancel their own no-bid auctions. 0 allows everyone."
    );

    private static final ModConfigSpec.IntValue CLAIM_PERMISSION_LEVEL = permissionLevel(
            "permissions.claimPermissionLevel",
            "Minecraft permission level required to claim won or unsold auction items. 0 allows everyone."
    );

    private static final ModConfigSpec.IntValue ADMIN_STATUS_PERMISSION_LEVEL = BUILDER
            .comment(
                    "Minecraft permission level required to run UAS admin commands and admin dashboard tools.",
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

    private static ModConfigSpec.IntValue permissionLevel(String path, String description) {
        return BUILDER
                .comment(
                        description,
                        "Uses standard Minecraft permission levels from 0 to 4. Future permission integrations can override these checks through the UAS permission hook."
                )
                .defineInRange(path, 0, 0, MAX_PERMISSION_LEVEL);
    }

    public static double listingFeeRate;
    public static double cancellationFeeRate = DEFAULT_CANCELLATION_FEE_RATE;
    public static double salesTaxRate;
    public static String salesTaxDestinationAccountUuid = "";
    public static long minimumBidIncrementDollars;
    public static int maxActiveListingsPerPlayer;
    public static int minAuctionDurationMinutes;
    public static int maxAuctionDurationHours;
    public static int settlementRetryAttempts;
    public static int settlementRetryDelaySeconds;
    public static int createCooldownSeconds = DEFAULT_CREATE_COOLDOWN_SECONDS;
    public static int bidCooldownSeconds = DEFAULT_BID_COOLDOWN_SECONDS;
    public static int buyoutCooldownSeconds = DEFAULT_BUYOUT_COOLDOWN_SECONDS;
    public static int cancelCooldownSeconds = DEFAULT_CANCEL_COOLDOWN_SECONDS;
    public static int searchCooldownSeconds = DEFAULT_SEARCH_COOLDOWN_SECONDS;
    public static int maxWatchedAuctionsPerPlayer = DEFAULT_MAX_WATCHED_AUCTIONS_PER_PLAYER;
    public static int watchEndingSoonThresholdMinutes = DEFAULT_WATCH_ENDING_SOON_THRESHOLD_MINUTES;
    public static int maxSavedSearchesPerPlayer = DEFAULT_MAX_SAVED_SEARCHES_PER_PLAYER;
    public static boolean requireUbsForListing;
    public static boolean autoSettleExpiredAuctions;
    public static boolean physicalCashListingFees = DEFAULT_PHYSICAL_CASH_LISTING_FEES;
    public static boolean physicalCashBuyouts = DEFAULT_PHYSICAL_CASH_BUYOUTS;
    public static boolean chequePayouts = DEFAULT_CHEQUE_PAYOUTS;
    public static String chequePayoutSourceAccountUuid = "";
    public static long chequePayoutMinimumDollars = DEFAULT_CHEQUE_PAYOUT_MINIMUM_DOLLARS;
    public static String chequePayoutIssuerPlayerUuid = "";
    public static String chequePayoutIssuerName = DEFAULT_CHEQUE_PAYOUT_ISSUER_NAME;
    public static boolean auditRejectedBids;
    public static boolean auditStateTransitions;
    public static boolean auditSuspiciousBidPatterns = DEFAULT_AUDIT_SUSPICIOUS_BID_PATTERNS;
    public static boolean auditSellerSelfBidSignals = DEFAULT_AUDIT_SELLER_SELF_BID_SIGNALS;
    public static boolean auditExternalSuspicionSignalHooks = DEFAULT_AUDIT_EXTERNAL_SUSPICION_SIGNAL_HOOKS;
    public static int suspiciousRapidBidWindowSeconds = DEFAULT_SUSPICIOUS_RAPID_BID_WINDOW_SECONDS;
    public static int suspiciousRapidBidCount = DEFAULT_SUSPICIOUS_RAPID_BID_COUNT;
    public static int suspiciousRepeatedBidderPairCount = DEFAULT_SUSPICIOUS_REPEATED_BIDDER_PAIR_COUNT;
    public static int suspiciousCancelWindowHours = DEFAULT_SUSPICIOUS_CANCEL_WINDOW_HOURS;
    public static int suspiciousCancelCount = DEFAULT_SUSPICIOUS_CANCEL_COUNT;
    public static boolean allowSellerSelfBid;
    public static int listPermissionLevel = DEFAULT_LIST_PERMISSION_LEVEL;
    public static int bidPermissionLevel = DEFAULT_BID_PERMISSION_LEVEL;
    public static int buyoutPermissionLevel = DEFAULT_BUYOUT_PERMISSION_LEVEL;
    public static int cancelOwnPermissionLevel = DEFAULT_CANCEL_OWN_PERMISSION_LEVEL;
    public static int claimPermissionLevel = DEFAULT_CLAIM_PERMISSION_LEVEL;
    public static int adminStatusPermissionLevel = DEFAULT_ADMIN_STATUS_PERMISSION_LEVEL;
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
        salesTaxDestinationAccountUuid = readSalesTaxDestination();
        minimumBidIncrementDollars = readLong("economy.minimumBidIncrementDollars", MINIMUM_BID_INCREMENT_DOLLARS, DEFAULT_MINIMUM_BID_INCREMENT_DOLLARS, 1L, MAX_MONEY_DOLLARS);
        maxActiveListingsPerPlayer = readInt("limits.maxActiveListingsPerPlayer", MAX_ACTIVE_LISTINGS_PER_PLAYER, DEFAULT_MAX_ACTIVE_LISTINGS_PER_PLAYER, 1, MAX_LISTINGS_PER_PLAYER);
        minAuctionDurationMinutes = readInt("limits.minAuctionDurationMinutes", MIN_AUCTION_DURATION_MINUTES, DEFAULT_MIN_AUCTION_DURATION_MINUTES, 5, MAX_DURATION_MINUTES);
        int configuredMaxHours = readInt("limits.maxAuctionDurationHours", MAX_AUCTION_DURATION_HOURS, DEFAULT_MAX_AUCTION_DURATION_HOURS, 1, MAX_DURATION_HOURS);
        maxAuctionDurationHours = Math.max((int) Math.ceil(minAuctionDurationMinutes / 60.0D), configuredMaxHours);
        settlementRetryAttempts = readInt("settlement.retryAttempts", SETTLEMENT_RETRY_ATTEMPTS, DEFAULT_SETTLEMENT_RETRY_ATTEMPTS, 0, MAX_SETTLEMENT_RETRY_ATTEMPTS);
        settlementRetryDelaySeconds = readInt("settlement.retryDelaySeconds", SETTLEMENT_RETRY_DELAY_SECONDS, DEFAULT_SETTLEMENT_RETRY_DELAY_SECONDS, 1, MAX_SETTLEMENT_RETRY_DELAY_SECONDS);
        createCooldownSeconds = readInt("rateLimits.createCooldownSeconds", CREATE_COOLDOWN_SECONDS, DEFAULT_CREATE_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);
        bidCooldownSeconds = readInt("rateLimits.bidCooldownSeconds", BID_COOLDOWN_SECONDS, DEFAULT_BID_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);
        buyoutCooldownSeconds = readInt("rateLimits.buyoutCooldownSeconds", BUYOUT_COOLDOWN_SECONDS, DEFAULT_BUYOUT_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);
        cancelCooldownSeconds = readInt("rateLimits.cancelCooldownSeconds", CANCEL_COOLDOWN_SECONDS, DEFAULT_CANCEL_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);
        searchCooldownSeconds = readInt("rateLimits.searchCooldownSeconds", SEARCH_COOLDOWN_SECONDS, DEFAULT_SEARCH_COOLDOWN_SECONDS, 0, MAX_RATE_LIMIT_SECONDS);
        maxWatchedAuctionsPerPlayer = readInt("notifications.maxWatchedAuctionsPerPlayer", MAX_WATCHED_AUCTIONS, DEFAULT_MAX_WATCHED_AUCTIONS_PER_PLAYER, 1, MAX_WATCHED_AUCTIONS_PER_PLAYER);
        watchEndingSoonThresholdMinutes = readInt("notifications.endingSoonThresholdMinutes", WATCH_ENDING_SOON_THRESHOLD_MINUTES, DEFAULT_WATCH_ENDING_SOON_THRESHOLD_MINUTES, 0, MAX_WATCH_ENDING_SOON_THRESHOLD_MINUTES);
        maxSavedSearchesPerPlayer = readInt("marketplace.maxSavedSearchesPerPlayer", MAX_SAVED_SEARCHES, DEFAULT_MAX_SAVED_SEARCHES_PER_PLAYER, 1, MAX_SAVED_SEARCHES_PER_PLAYER);
        requireUbsForListing = readBoolean("settlement.requireUbsForListing", REQUIRE_UBS_FOR_LISTING, DEFAULT_REQUIRE_UBS_FOR_LISTING);
        autoSettleExpiredAuctions = readBoolean("settlement.autoSettleExpiredAuctions", AUTO_SETTLE_EXPIRED_AUCTIONS, DEFAULT_AUTO_SETTLE_EXPIRED_AUCTIONS);
        physicalCashListingFees = readBoolean("settlement.physicalCashListingFees", PHYSICAL_CASH_LISTING_FEES, DEFAULT_PHYSICAL_CASH_LISTING_FEES);
        physicalCashBuyouts = readBoolean("settlement.physicalCashBuyouts", PHYSICAL_CASH_BUYOUTS, DEFAULT_PHYSICAL_CASH_BUYOUTS);
        chequePayouts = readBoolean("settlement.chequePayouts", CHEQUE_PAYOUTS, DEFAULT_CHEQUE_PAYOUTS);
        chequePayoutSourceAccountUuid = readOptionalUuid("settlement.chequePayoutSourceAccountUuid", CHEQUE_PAYOUT_SOURCE_ACCOUNT_UUID);
        chequePayoutMinimumDollars = readLong("settlement.chequePayoutMinimumDollars", CHEQUE_PAYOUT_MINIMUM_DOLLARS, DEFAULT_CHEQUE_PAYOUT_MINIMUM_DOLLARS, 0L, MAX_MONEY_DOLLARS);
        chequePayoutIssuerPlayerUuid = readOptionalUuid("settlement.chequePayoutIssuerPlayerUuid", CHEQUE_PAYOUT_ISSUER_PLAYER_UUID);
        chequePayoutIssuerName = readString("settlement.chequePayoutIssuerName", CHEQUE_PAYOUT_ISSUER_NAME, DEFAULT_CHEQUE_PAYOUT_ISSUER_NAME);
        auditRejectedBids = readBoolean("audit.rejectedBids", AUDIT_REJECTED_BIDS, DEFAULT_AUDIT_REJECTED_BIDS);
        auditStateTransitions = readBoolean("audit.stateTransitions", AUDIT_STATE_TRANSITIONS, DEFAULT_AUDIT_STATE_TRANSITIONS);
        auditSuspiciousBidPatterns = readBoolean("audit.suspiciousBidPatterns", AUDIT_SUSPICIOUS_BID_PATTERNS, DEFAULT_AUDIT_SUSPICIOUS_BID_PATTERNS);
        suspiciousRapidBidWindowSeconds = readInt("audit.suspiciousRapidBidWindowSeconds", SUSPICIOUS_RAPID_BID_WINDOW_SECONDS, DEFAULT_SUSPICIOUS_RAPID_BID_WINDOW_SECONDS, 30, MAX_AUDIT_WINDOW_SECONDS);
        suspiciousRapidBidCount = readInt("audit.suspiciousRapidBidCount", SUSPICIOUS_RAPID_BID_COUNT, DEFAULT_SUSPICIOUS_RAPID_BID_COUNT, 2, MAX_AUDIT_SIGNAL_COUNT);
        suspiciousRepeatedBidderPairCount = readInt("audit.suspiciousRepeatedBidderPairCount", SUSPICIOUS_REPEATED_BIDDER_PAIR_COUNT, DEFAULT_SUSPICIOUS_REPEATED_BIDDER_PAIR_COUNT, 2, MAX_AUDIT_SIGNAL_COUNT);
        suspiciousCancelWindowHours = readInt("audit.suspiciousCancelWindowHours", SUSPICIOUS_CANCEL_WINDOW_HOURS, DEFAULT_SUSPICIOUS_CANCEL_WINDOW_HOURS, 1, MAX_AUDIT_WINDOW_HOURS);
        suspiciousCancelCount = readInt("audit.suspiciousCancelCount", SUSPICIOUS_CANCEL_COUNT, DEFAULT_SUSPICIOUS_CANCEL_COUNT, 2, MAX_AUDIT_SIGNAL_COUNT);
        auditSellerSelfBidSignals = readBoolean("audit.sellerSelfBidSignals", AUDIT_SELLER_SELF_BID_SIGNALS, DEFAULT_AUDIT_SELLER_SELF_BID_SIGNALS);
        auditExternalSuspicionSignalHooks = readBoolean("audit.externalSuspicionSignalHooks", AUDIT_EXTERNAL_SUSPICION_SIGNAL_HOOKS, DEFAULT_AUDIT_EXTERNAL_SUSPICION_SIGNAL_HOOKS);
        allowSellerSelfBid = readBoolean("bidding.allowSellerSelfBid", ALLOW_SELLER_SELF_BID, DEFAULT_ALLOW_SELLER_SELF_BID);
        listPermissionLevel = readInt("permissions.listPermissionLevel", LIST_PERMISSION_LEVEL, DEFAULT_LIST_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);
        bidPermissionLevel = readInt("permissions.bidPermissionLevel", BID_PERMISSION_LEVEL, DEFAULT_BID_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);
        buyoutPermissionLevel = readInt("permissions.buyoutPermissionLevel", BUYOUT_PERMISSION_LEVEL, DEFAULT_BUYOUT_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);
        cancelOwnPermissionLevel = readInt("permissions.cancelOwnPermissionLevel", CANCEL_OWN_PERMISSION_LEVEL, DEFAULT_CANCEL_OWN_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);
        claimPermissionLevel = readInt("permissions.claimPermissionLevel", CLAIM_PERMISSION_LEVEL, DEFAULT_CLAIM_PERMISSION_LEVEL, 0, MAX_PERMISSION_LEVEL);
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

    public static Optional<UUID> salesTaxDestinationAccountId() {
        String value = salesTaxDestinationAccountUuid == null ? "" : salesTaxDestinationAccountUuid.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<UUID> chequePayoutSourceAccountId() {
        return parseUuid(chequePayoutSourceAccountUuid);
    }

    public static Optional<UUID> chequePayoutIssuerPlayerId() {
        return parseUuid(chequePayoutIssuerPlayerUuid);
    }

    public static boolean chequePayoutApplies(BigDecimal netPayout) {
        if (!chequePayouts || netPayout == null || netPayout.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (netPayout.compareTo(BigDecimal.valueOf(Math.max(0L, chequePayoutMinimumDollars))) < 0) {
            return false;
        }
        try {
            netPayout.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    public static boolean isPhysicalCashSettlementEnabled(UasCashSettlementUse use) {
        if (use == null) {
            return false;
        }
        return switch (use) {
            case BUYOUT -> physicalCashBuyouts;
            case LISTING_FEE -> physicalCashListingFees;
        };
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

    private static String readSalesTaxDestination() {
        try {
            String value = SALES_TAX_DESTINATION_ACCOUNT_UUID.get();
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) {
                return "";
            }
            UUID.fromString(normalized);
            return normalized;
        } catch (RuntimeException exception) {
            lastConfigLoadHealthy = false;
            lastConfigLoadMessage = "Invalid config value for economy.salesTaxDestinationAccountUuid; using blank money-sink tax destination.";
            SALES_TAX_DESTINATION_ACCOUNT_UUID.set("");
            return "";
        }
    }

    private static String readOptionalUuid(String path, ModConfigSpec.ConfigValue<String> value) {
        try {
            String raw = value.get();
            String normalized = raw == null ? "" : raw.trim();
            if (normalized.isBlank()) {
                return "";
            }
            UUID.fromString(normalized);
            return normalized;
        } catch (RuntimeException exception) {
            lastConfigLoadHealthy = false;
            lastConfigLoadMessage = "Invalid config value for " + path + "; using blank UUID.";
            value.set("");
            return "";
        }
    }

    private static String readString(String path, ModConfigSpec.ConfigValue<String> value, String fallback) {
        try {
            String resolved = value.get();
            if (resolved == null || resolved.isBlank()) {
                value.set(fallback);
                return fallback;
            }
            return resolved.trim();
        } catch (RuntimeException exception) {
            lastConfigLoadHealthy = false;
            lastConfigLoadMessage = "Invalid config value for " + path + "; using safe default.";
            value.set(fallback);
            return fallback;
        }
    }

    private static Optional<UUID> parseUuid(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
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
