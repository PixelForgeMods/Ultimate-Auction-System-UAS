package net.austizz.ultimate_auction_system;

import com.mojang.brigadier.Command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.network.UasPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.austizz.ultimate_auction_system.UltimateAuctionSystem.auctionHouse;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, bus = EventBusSubscriber.Bus.GAME)
public class AUSCommands {
    private static final long BUYOUT_CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final ConcurrentHashMap<UUID, PendingBuyout> PENDING_BUYOUTS = new ConcurrentHashMap<>();
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("dd-MM")
            .parseDefaulting(ChronoField.YEAR, 2026) // Vult automatisch het jaar in (bijv. 2026)
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0) // Vult uren in (00)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0) // Vult minuten in (00)
            .toFormatter();
    private static final SimpleCommandExceptionType INVALID_DATE = new SimpleCommandExceptionType(
            UasTranslations.literal("Incorrect date format. Use dd-MM, for example 24-12.")
    );
    private static final SimpleCommandExceptionType INVALID_NUMBER = new SimpleCommandExceptionType(
            UasTranslations.literal("Incorrect number format. Use numbers only, for example 50.42.")
    );
    private static final SimpleCommandExceptionType INVALID_ITEMSTACK = new SimpleCommandExceptionType(
            UasTranslations.literal("Hold the item you want to auction in your main hand.")
    );

    private record PendingBuyout(UUID auctionId, long expiresAtMillis) {
        boolean matches(UUID expectedAuctionId) {
            return auctionId != null
                    && auctionId.equals(expectedAuctionId)
                    && expiresAtMillis >= System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ah")
                        .executes(AUSCommands::openAuctionGui)
                        .then(Commands.literal("list")
                                .executes(AUSCommands::sendAuctionList))
                        .then(Commands.literal("view")
                                .then(Commands.argument("auctionId", StringArgumentType.word())
                                        .executes(AUSCommands::viewAuction)))
                        .then(Commands.literal("create")
                                .then(Commands.argument("Starting Price", StringArgumentType.word())
                                        .then(Commands.argument("Duration Hours", IntegerArgumentType.integer(1))
                                                .then(Commands.literal("buyout")
                                                        .then(Commands.argument("Buyout Price", StringArgumentType.word())
                                                                .then(Commands.argument("Description", StringArgumentType.greedyString())
                                                                        .executes(context -> prepareCreateFromCommand(context, true)))))
                                                .then(Commands.argument("Description", StringArgumentType.greedyString())
                                                        .executes(context -> prepareCreateFromCommand(context, false)))))
                        )
                        .then(Commands.literal("confirm")
                                .executes(AUSCommands::confirmPendingListing))
                        .then(Commands.literal("discard")
                                .executes(AUSCommands::discardPendingListing))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("auctionId", StringArgumentType.word())
                                        .executes(AUSCommands::cancelOwnAuction)))
                        .then(Commands.literal("bid")
                                .then(Commands.argument("auctionId", StringArgumentType.word())
                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                .executes(AUSCommands::bidAuction))))
                        .then(Commands.literal("buyout")
                                .then(Commands.literal("confirm")
                                        .then(Commands.argument("auctionId", StringArgumentType.word())
                                                .executes(AUSCommands::confirmBuyoutAuction)))
                                .then(Commands.argument("auctionId", StringArgumentType.word())
                                        .executes(AUSCommands::previewBuyoutAuction)))
                        .then(Commands.literal("claim")
                                .then(Commands.argument("auctionId", StringArgumentType.word())
                                        .executes(AUSCommands::claimAuction)))
                        .then(Commands.literal("mine")
                                .executes(context -> sendMine(context, AuctionSellerFilter.ALL, 1))
                                .then(Commands.argument("status", StringArgumentType.word())
                                        .executes(context -> sendMine(context, AuctionSellerFilter.fromToken(StringArgumentType.getString(context, "status")), 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(context -> sendMine(
                                                        context,
                                                        AuctionSellerFilter.fromToken(StringArgumentType.getString(context, "status")),
                                                        IntegerArgumentType.getInteger(context, "page"))))))
        );

        event.getDispatcher().register(
                Commands.literal("uas")
                        .then(Commands.literal("status")
                                .requires(source -> source.hasPermission(Config.adminStatusPermissionLevel))
                                .executes(AUSCommands::sendStatus)
                        )
                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(Config.adminStatusPermissionLevel))
                                .executes(AUSCommands::openAdminAuctionGui)
                                .then(Commands.literal("gui")
                                        .executes(AUSCommands::openAdminAuctionGui))
                                .then(Commands.literal("seller")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(AUSCommands::sendSellerStats)))
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("auctionId", StringArgumentType.string())
                                                .executes(AUSCommands::inspectAuction)
                                        )
                                )
                                .then(Commands.literal("settlement")
                                        .then(Commands.literal("retry")
                                                .then(Commands.argument("auctionId", StringArgumentType.string())
                                                        .executes(AUSCommands::retrySettlement)
                                                )
                                        )
                                )
                        )
        );
    }

    private static int openAuctionGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendSystemMessage(UasTranslations.literal("Only players can open the auction house GUI.").withStyle(ChatFormatting.RED));
            return 0;
        }
        UasPayloads.openAuctionHouse(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int openAdminAuctionGui(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can open the UAS admin GUI."));
            return 0;
        }
        UasPayloads.openAdminAuctionHouse(player);
        return Command.SINGLE_SUCCESS;
    }

    private static int prepareCreateFromCommand(CommandContext<CommandSourceStack> context, boolean hasBuyout) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can create auctions."));
            return 0;
        }
        AuctionHouse house = auctionHouse;
        if (house == null) {
            context.getSource().sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }
        BigDecimal startingBid = parseMoney(StringArgumentType.getString(context, "Starting Price"));
        BigDecimal buyout = hasBuyout ? parseMoney(StringArgumentType.getString(context, "Buyout Price")) : BigDecimal.ZERO;
        if (startingBid == null || buyout == null) {
            context.getSource().sendFailure(Component.literal("Incorrect number format. Use whole dollars only, for example 50."));
            return 0;
        }
        int durationHours = IntegerArgumentType.getInteger(context, "Duration Hours");
        String description = StringArgumentType.getString(context, "Description");
        AuctionActionResult result = house.prepareAuctionFromMainHand(player, startingBid, buyout, durationHours, description);
        sendResult(context.getSource(), result);
        if (result.success()) {
            context.getSource().sendSystemMessage(Component.literal("[Confirm]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah confirm")))
                    .append(Component.literal(" "))
                    .append(Component.literal("[Discard]").withStyle(style -> style.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah discard")))));
        }
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int confirmPendingListing(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can create auctions."));
            return 0;
        }
        AuctionActionResult result = auctionHouse.confirmPendingAuction(player);
        sendResult(context.getSource(), result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int discardPendingListing(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can create auctions."));
            return 0;
        }
        AuctionActionResult result = auctionHouse.discardPendingAuction(player);
        sendResult(context.getSource(), result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int cancelOwnAuction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can cancel auctions."));
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionActionResult result = auctionHouse.cancelOwnAuction(player, auctionId, AuctionDeliverySavedData.get(player.getServer()));
        auctionHouse.sendActionAlert(player, result);
        sendResult(context.getSource(), result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int claimAuction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can claim auction items."));
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionActionResult result = auctionHouse.claimAuction(player, auctionId, AuctionDeliverySavedData.get(player.getServer()));
        auctionHouse.sendActionAlert(player, result);
        sendResult(context.getSource(), result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int bidAuction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can bid on auctions."));
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        BigDecimal amount = parseMoney(StringArgumentType.getString(context, "amount"));
        if (auctionId == null) {
            return 0;
        }
        if (amount == null) {
            context.getSource().sendFailure(Component.literal("Incorrect number format. Use whole dollars only, for example 50."));
            return 0;
        }
        AuctionActionResult result = auctionHouse.placeBidWithEscrow(player, auctionId, amount);
        auctionHouse.sendActionAlert(player, result);
        sendResult(context.getSource(), result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int viewAuction(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (auctionHouse == null) {
            source.sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }
        UUID auctionId = parseAuctionId(source, StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionItem item = auctionHouse.getAuctionItem(auctionId);
        if (item == null) {
            source.sendFailure(Component.literal("Auction not found."));
            return 0;
        }
        sendAuctionDetail(source, item);
        return Command.SINGLE_SUCCESS;
    }

    private static int previewBuyoutAuction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can buy out auctions."));
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionItem item = auctionHouse.getAuctionItem(auctionId);
        if (item == null) {
            context.getSource().sendFailure(Component.literal("Auction not found."));
            return 0;
        }
        if (!buyoutAvailable(item)) {
            context.getSource().sendFailure(Component.literal("This auction cannot be bought out right now."));
            return 0;
        }
        PENDING_BUYOUTS.put(player.getUUID(), new PendingBuyout(auctionId, System.currentTimeMillis() + BUYOUT_CONFIRM_WINDOW_MILLIS));
        context.getSource().sendSystemMessage(Component.literal("Buyout preview: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(item.getDisplayTitle()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(UasMoneyFormatter.display(item.getBuyoutPrice().orElse(BigDecimal.ZERO))).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(". Auction " + auctionId + ". ").withStyle(ChatFormatting.GRAY))
                .append(chatAction("[Confirm Buyout]", "/ah buyout confirm " + auctionId, "Confirm this buyout and spend from your UBS primary account.", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND))
                .append(Component.literal(" "))
                .append(chatAction("[Cancel]", "/ah", "Do not buy out; open the Auction House instead.", ChatFormatting.RED, ClickEvent.Action.RUN_COMMAND)));
        return Command.SINGLE_SUCCESS;
    }

    private static int confirmBuyoutAuction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can buy out auctions."));
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        PendingBuyout pending = PENDING_BUYOUTS.get(player.getUUID());
        if (pending == null || !pending.matches(auctionId)) {
            context.getSource().sendFailure(Component.literal("Run /ah buyout " + auctionId + " first to preview and confirm this buyout."));
            return 0;
        }
        PENDING_BUYOUTS.remove(player.getUUID());
        AuctionActionResult result = auctionHouse.buyout(player, auctionId);
        auctionHouse.sendActionAlert(player, result);
        sendResult(context.getSource(), result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int retrySettlement(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AuctionHouse house = auctionHouse;
        if (house == null) {
            source.sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }
        UUID auctionId = parseAuctionId(source, StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }

        AuctionActionResult preview = house.settlementRetryPreview(auctionId);
        sendResult(source, preview);
        if (!preview.success()) {
            return 0;
        }

        ServerPlayer admin = source.getPlayer();
        UUID adminId = admin == null ? null : admin.getUUID();
        String adminName = admin == null ? "Console" : admin.getGameProfile().getName();
        AuctionActionResult result = house.adminRetrySettlement(
                adminId,
                adminName,
                source.hasPermission(Config.adminStatusPermissionLevel),
                auctionId,
                AuctionDeliverySavedData.get(source.getServer())
        );
        AuctionAdminSavedData.get(source.getServer()).addAudit(
                "RETRY_SETTLEMENT",
                adminId,
                adminName,
                String.valueOf(auctionId),
                preview.message(),
                result.success(),
                result.message()
        );
        sendResult(source, result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int sendAuctionList(CommandContext<CommandSourceStack> context) {
        Component header = Component.literal("\n=== [ ")
                .withStyle(ChatFormatting.GOLD)
                .append(UasTranslations.literal("ACTIVE AUCTIONS").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" ] ===").withStyle(ChatFormatting.GOLD));

        context.getSource().sendSystemMessage(header);

        for (AuctionItem item : auctionHouse.getAuctionItems().values()) {
            if (item.getState() != AuctionState.ACTIVE) {
                continue;
            }
            ItemStack itemStack = item.getItem();
            String displayName = itemStack.getHoverName().getString();
            int count = itemStack.getCount();

            java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), item.getDateOfEnd());
            String timeLeftStr;
            MutableComponent timeLeft;
            ChatFormatting timeColor;

            if (duration.isNegative() || duration.isZero()) {
                timeLeftStr = "Ended";
                timeColor = ChatFormatting.DARK_RED;
                timeLeft = UasTranslations.literal(timeLeftStr).withStyle(timeColor);
            } else {
                long days = duration.toDays();
                long hours = duration.toHoursPart();
                long minutes = duration.toMinutesPart();
                long seconds = duration.toSecondsPart();

                if (days > 0) {
                    timeLeftStr = String.format("%dd %dh", days, hours);
                    timeColor = ChatFormatting.GREEN;
                } else if (hours > 0) {
                    timeLeftStr = String.format("%dh %dm", hours, minutes);
                    timeColor = ChatFormatting.GREEN;
                } else if (minutes > 0) {
                    timeLeftStr = String.format("%dm %ds", minutes, seconds);
                    timeColor = ChatFormatting.YELLOW;
                } else {
                    timeLeftStr = String.format("%ds", seconds);
                    timeColor = ChatFormatting.RED;
                }
                timeLeft = Component.literal(timeLeftStr).withStyle(timeColor);
            }

            MutableComponent line = Component.literal("* ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(count + "x " + displayName).withStyle(ChatFormatting.AQUA))
                    .append(UasTranslations.literal(" | Bid: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(UasMoneyFormatter.display(item.getHighestBid())).withStyle(ChatFormatting.GOLD))
                    .append(UasTranslations.literal(" | Time left: ").withStyle(ChatFormatting.GRAY))
                    .append(timeLeft)
                    .append(Component.literal(" "))
                    .append(chatAction("[View]", "/ah view " + item.getAuctionId(), "View auction details for " + item.getAuctionId() + ".", ChatFormatting.AQUA, ClickEvent.Action.RUN_COMMAND))
                    .append(Component.literal(" "))
                    .append(chatAction("[Bid]", "/ah bid " + item.getAuctionId() + " ", "Suggest a bid command for this auction.", ChatFormatting.GREEN, ClickEvent.Action.SUGGEST_COMMAND));
            if (buyoutAvailable(item)) {
                line.append(Component.literal(" "))
                        .append(chatAction("[Buyout]", "/ah buyout " + item.getAuctionId(), "Preview buyout confirmation before spending money.", ChatFormatting.YELLOW, ClickEvent.Action.RUN_COMMAND));
            }

            line.withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(itemStack))
            ));

            context.getSource().sendSystemMessage(line);
        }

        Component footer = Component.literal("=======================").withStyle(ChatFormatting.GOLD);
        context.getSource().sendSystemMessage(footer);

        return Command.SINGLE_SUCCESS;
    }

    private static void sendAuctionDetail(CommandSourceStack source, AuctionItem item) {
        ItemStack stack = item.getItem();
        UUID auctionId = item.getAuctionId();
        MutableComponent title = Component.literal("=== Auction " + auctionId + " ===").withStyle(ChatFormatting.GOLD);
        source.sendSystemMessage(title);
        source.sendSystemMessage(Component.literal(stack.getCount() + "x " + item.getDisplayTitle())
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack)))));
        source.sendSystemMessage(Component.literal("Seller: " + playerName(source, item.getPlayerId())).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(Component.literal("Current bid: " + UasMoneyFormatter.display(item.getHighestBid())).withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(Component.literal("Buyout: " + item.getBuyoutPrice().map(UasMoneyFormatter::display).orElse("none")).withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(Component.literal("State: " + item.getState() + " | " + timeLeftText(item)).withStyle(ChatFormatting.GRAY));
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            source.sendSystemMessage(Component.literal("Description: " + item.getDescription()).withStyle(ChatFormatting.WHITE));
        }

        MutableComponent actions = Component.literal("Actions: ").withStyle(ChatFormatting.GRAY)
                .append(chatAction("[Bid]", "/ah bid " + auctionId + " ", "Suggest a bid command for this auction.", ChatFormatting.GREEN, ClickEvent.Action.SUGGEST_COMMAND));
        if (buyoutAvailable(item)) {
            actions.append(Component.literal(" "))
                    .append(chatAction("[Buyout]", "/ah buyout " + auctionId, "Preview buyout confirmation before spending money.", ChatFormatting.YELLOW, ClickEvent.Action.RUN_COMMAND));
        }
        ServerPlayer player = source.getPlayer();
        if (player != null && canClaimFromChat(player, item)) {
            actions.append(Component.literal(" "))
                    .append(chatAction("[Claim]", "/ah claim " + auctionId, "Claim this auction if it is available.", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND));
        }
        actions.append(Component.literal(" "))
                .append(chatAction("[Open /ah]", "/ah", "Open the Auction House GUI.", ChatFormatting.GOLD, ClickEvent.Action.RUN_COMMAND));
        source.sendSystemMessage(actions);
    }

    private static int sendMine(CommandContext<CommandSourceStack> context, AuctionSellerFilter filter, int page) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can view their auction listings."));
            return 0;
        }
        AuctionHouse house = auctionHouse;
        if (house == null) {
            context.getSource().sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }
        AuctionSellerFilter safeFilter = filter == null ? AuctionSellerFilter.ALL : filter;
        List<AuctionItem> listings = house.getSellerListings(player.getUUID(), safeFilter);
        SellerAuctionStats stats = house.getSellerStats(player.getUUID());
        int pageSize = 6;
        int maxPage = Math.max(1, (int) Math.ceil(listings.size() / (double) pageSize));
        int safePage = Math.max(1, Math.min(page, maxPage));
        int start = (safePage - 1) * pageSize;
        int end = Math.min(listings.size(), start + pageSize);

        context.getSource().sendSystemMessage(Component.literal("=== My Auctions: " + safeFilter.name().toLowerCase() + " (" + safePage + "/" + maxPage + ") ===").withStyle(ChatFormatting.GOLD));
        context.getSource().sendSystemMessage(Component.literal("Active " + stats.active() + "/" + stats.activeLimit()
                + " | Sold " + stats.sold()
                + " | Cancelled " + stats.cancelled()
                + " | Expired " + stats.expired()).withStyle(ChatFormatting.GRAY));
        if (listings.isEmpty()) {
            context.getSource().sendSystemMessage(Component.literal("No matching auction listings.").withStyle(ChatFormatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        for (int index = start; index < end; index++) {
            sendMineRow(context.getSource(), listings.get(index));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void sendMineRow(CommandSourceStack source, AuctionItem item) {
        ItemStack stack = item.getItem();
        String status = sellerStatusLabel(source, item);
        MutableComponent row = Component.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stack.getCount() + "x " + stack.getHoverName().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | " + UasMoneyFormatter.display(item.getCurrentPrice())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | " + status).withStyle(ChatFormatting.GRAY));

        if (item.getState() == AuctionState.ACTIVE && item.getHighestBidderId() == null && !item.isExpired()) {
            row.append(Component.literal(" "))
                    .append(chatAction("[Cancel]", "/ah cancel " + item.getAuctionId(), "Cancel this auction if it still has no bids.", ChatFormatting.RED, ClickEvent.Action.RUN_COMMAND));
        }
        if ((item.getState() == AuctionState.ENDED || item.isExpired() || item.getState() == AuctionState.FAILED_SETTLEMENT)
                && item.getHighestBidderId() == null) {
            row.append(Component.literal(" "))
                    .append(chatAction("[Claim]", "/ah claim " + item.getAuctionId(), "Claim this unsold auction return.", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND));
        }
        row.append(Component.literal(" "))
                .append(chatAction("[View]", "/ah view " + item.getAuctionId(), "View auction details for " + item.getAuctionId() + ".", ChatFormatting.AQUA, ClickEvent.Action.RUN_COMMAND));
        row.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack))));
        source.sendSystemMessage(row);
    }

    private static MutableComponent chatAction(String label,
                                               String command,
                                               String hover,
                                               ChatFormatting color,
                                               ClickEvent.Action action) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static boolean buyoutAvailable(AuctionItem item) {
        return item != null
                && item.getState() == AuctionState.ACTIVE
                && !item.isExpired()
                && item.getBuyoutPrice().isPresent()
                && (item.getHighestBidderId() == null || item.getHighestBid().compareTo(item.getBuyoutPrice().get()) < 0);
    }

    private static boolean canClaimFromChat(ServerPlayer player, AuctionItem item) {
        if (player == null || item == null) {
            return false;
        }
        boolean ended = item.getState() == AuctionState.ENDED || item.isExpired();
        if (!ended || item.getState() == AuctionState.CLAIMED || item.getState() == AuctionState.CANCELLED) {
            return false;
        }
        UUID winnerId = item.getHighestBidderId();
        return winnerId == null
                ? player.getUUID().equals(item.getPlayerId())
                : player.getUUID().equals(winnerId);
    }

    private static String sellerStatusLabel(CommandSourceStack source, AuctionItem item) {
        if (item.getState() == AuctionState.CANCELLED) {
            return "cancelled";
        }
        if (item.getHighestBidderId() != null) {
            return "highest bidder " + playerName(source, item.getHighestBidderId());
        }
        if (item.getState() == AuctionState.ACTIVE && !item.isExpired()) {
            return "active, " + timeLeftText(item);
        }
        return "expired without bids";
    }

    private static int sendSellerStats(CommandContext<CommandSourceStack> context) {
        AuctionHouse house = auctionHouse;
        if (house == null) {
            context.getSource().sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }
        UUID sellerId = resolvePlayerId(context.getSource(), StringArgumentType.getString(context, "player"));
        if (sellerId == null) {
            return 0;
        }
        SellerAuctionStats stats = house.getSellerStats(sellerId);
        context.getSource().sendSystemMessage(Component.literal("=== UAS Seller Auctions ===").withStyle(ChatFormatting.GOLD));
        context.getSource().sendSystemMessage(Component.literal("Seller: " + sellerId).withStyle(ChatFormatting.GRAY));
        context.getSource().sendSystemMessage(Component.literal("Active: " + stats.active() + "/" + stats.activeLimit()).withStyle(ChatFormatting.GREEN));
        context.getSource().sendSystemMessage(Component.literal("Sold: " + stats.sold()).withStyle(ChatFormatting.GOLD));
        context.getSource().sendSystemMessage(Component.literal("Cancelled: " + stats.cancelled()).withStyle(ChatFormatting.RED));
        context.getSource().sendSystemMessage(Component.literal("Expired: " + stats.expired()).withStyle(ChatFormatting.YELLOW));
        context.getSource().sendSystemMessage(Component.literal("Total: " + stats.total()).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int sendStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UasBankingService bankingService = new UbsBankingService();
        UasDependencyDiagnostics.StartupDiagnostics diagnostics = UasDependencyDiagnostics.collect();
        boolean ubsLoaded = diagnostics.modLoaded();
        boolean ubsServerAvailable = bankingService.isAvailable();
        AuctionHouse house = auctionHouse;
        int auctionCount = house == null ? 0 : house.getAuctionItems().size();
        AuctionStorageHealth storageHealth = house == null
                ? new AuctionStorageHealth(UasHealthLevel.ERROR, "Auction house is not initialized.", -1L)
                : house.getStorageHealth();

        source.sendSystemMessage(UasTranslations.literal("=== UAS Status ===").withStyle(ChatFormatting.GOLD));
        sendStatusLine(source, "UBS loaded", ubsLoaded ? "yes" : "no", ubsLoaded ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendStatusLine(source, "UBS mod version", diagnostics.modVersion(), diagnostics.versionSupported() ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendStatusLine(source, "UBS required range", UasDependencyDiagnostics.REQUIRED_UBS_RANGE, ChatFormatting.GRAY);
        sendStatusLine(source, "UBS API version", bankingService.getApiVersion(), ubsLoaded ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        sendStatusLine(source, "UBS server available", ubsServerAvailable ? "yes" : "no", ubsServerAvailable ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendStatusLine(source, "Active auctions", String.valueOf(auctionCount), ChatFormatting.AQUA);
        sendStatusLine(source, "Storage", storageHealth.message(), colorFor(storageHealth.level()));
        sendStatusLine(source, "Last autosave", formatLastSave(storageHealth.lastSaveEpochMillis()), colorFor(storageHealth.level()));
        if (!storageHealth.lastFailureReason().isBlank()) {
            sendStatusLine(source, "Storage failure", storageHealth.lastFailureReason(), ChatFormatting.RED);
        }
        sendStatusLine(source, "Config", Config.lastConfigLoadMessage, Config.lastConfigLoadHealthy ? ChatFormatting.GREEN : ChatFormatting.YELLOW);

        return Command.SINGLE_SUCCESS;
    }

    private static int inspectAuction(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AuctionHouse house = auctionHouse;
        if (house == null) {
            source.sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }

        UUID auctionId;
        try {
            auctionId = UUID.fromString(StringArgumentType.getString(context, "auctionId"));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(UasTranslations.literal("Invalid auction ID. Use the UUID shown in the admin logs or auction payloads."));
            return 0;
        }

        AuctionItem auction = house.getAuctionItem(auctionId);
        if (auction == null) {
            source.sendFailure(UasTranslations.literal("Auction not found: " + auctionId));
            return 0;
        }

        ItemStack stack = auction.getItem();
        source.sendSystemMessage(Component.literal("=== UAS Auction Inspect ===").withStyle(ChatFormatting.GOLD));
        sendInspectLine(source, "Auction ID", auction.getAuctionId().toString(), ChatFormatting.AQUA);
        sendInspectLine(source, "Item", stack.getCount() + "x " + stack.getHoverName().getString(), ChatFormatting.WHITE);
        sendInspectLine(source, "Description", blankFallback(auction.getDescription(), "(empty)"), ChatFormatting.GRAY);
        sendInspectLine(source, "State", auction.getState().name(), colorForState(auction.getState()));
        sendInspectLine(source, "Seller player", String.valueOf(auction.getPlayerId()), ChatFormatting.GRAY);
        sendInspectLine(source, "Seller account", String.valueOf(auction.getSellerAccountId()), ChatFormatting.GRAY);
        sendInspectLine(source, "Starting price", UasMoneyFormatter.display(auction.getStartingBidPrice()), ChatFormatting.GOLD);
        sendInspectLine(source, "Current price", UasMoneyFormatter.display(auction.getCurrentPrice()), ChatFormatting.GOLD);
        sendInspectLine(source, "Buyout price", auction.getBuyoutPrice().map(UasMoneyFormatter::display).orElse("(none)"), ChatFormatting.GOLD);
        sendInspectLine(source, "Highest bidder", Optional.ofNullable(auction.getHighestBidderId()).map(UUID::toString).orElse("(none)"), ChatFormatting.GRAY);
        sendInspectLine(source, "Start", formatDate(auction.getDateOfStart()), ChatFormatting.GRAY);
        sendInspectLine(source, "End", formatDate(auction.getDateOfEnd()), ChatFormatting.GRAY);
        sendInspectLine(source, "Created", formatDate(auction.getCreatedAt()), ChatFormatting.GRAY);
        sendInspectLine(source, "Updated", formatDate(auction.getUpdatedAt()), ChatFormatting.GRAY);
        sendInspectLine(source, "Escrowed", auction.isEscrowed() ? "yes" : "no", auction.isEscrowed() ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendInspectLine(source, "Escrow source", blankFallback(auction.getEscrowSource(), "(none)"), ChatFormatting.GRAY);
        sendInspectLine(source, "Escrowed at", auction.getEscrowedAt().map(AUSCommands::formatDate).orElse("(none)"), ChatFormatting.GRAY);
        sendInspectLine(source, "Notification subscribers", String.valueOf(auction.getNotificationSubscribers().size()), ChatFormatting.GRAY);

        List<AuctionBidRecord> bidRecords = auction.getBidRecords();
        source.sendSystemMessage(Component.literal("Bid history (" + bidRecords.size() + ")").withStyle(ChatFormatting.GOLD));
        if (bidRecords.isEmpty()) {
            source.sendSystemMessage(Component.literal("- No bid records.").withStyle(ChatFormatting.GRAY));
        } else {
            for (AuctionBidRecord record : bidRecords) {
                source.sendSystemMessage(formatBidRecord(record));
            }
        }

        List<AuctionFinancialEvent> financialEvents = auction.getFinancialEvents();
        source.sendSystemMessage(Component.literal("Financial events (" + financialEvents.size() + ")").withStyle(ChatFormatting.GOLD));
        if (financialEvents.isEmpty()) {
            source.sendSystemMessage(Component.literal("- No financial events.").withStyle(ChatFormatting.GRAY));
        } else {
            for (AuctionFinancialEvent event : financialEvents) {
                source.sendSystemMessage(formatFinancialEvent(event));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void sendResult(CommandSourceStack source, AuctionActionResult result) {
        if (source == null || result == null || result.message().isBlank()) {
            return;
        }
        source.sendSystemMessage(Component.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
            return amount.stripTrailingZeros().scale() > 0 ? null : amount;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static UUID parseAuctionId(CommandSourceStack source, String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Invalid auction ID. Use the UUID shown in /ah mine or admin inspect."));
            return null;
        }
    }

    private static UUID resolvePlayerId(CommandSourceStack source, String raw) {
        if (raw == null || raw.isBlank()) {
            source.sendFailure(Component.literal("Player name or UUID is required."));
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
        }
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(raw);
        if (online != null) {
            return online.getUUID();
        }
        source.sendFailure(Component.literal("Could not resolve player. Use an online player name or UUID."));
        return null;
    }

    private static String playerName(CommandSourceStack source, UUID playerId) {
        if (playerId == null) {
            return "none";
        }
        ServerPlayer player = source.getServer().getPlayerList().getPlayer(playerId);
        return player == null ? playerId.toString().substring(0, 8) : player.getName().getString();
    }

    private static String timeLeftText(AuctionItem item) {
        java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), item.getDateOfEnd());
        if (duration.isNegative() || duration.isZero()) {
            return "ended";
        }
        long days = duration.toDays();
        if (days > 0) {
            return days + "d " + duration.toHoursPart() + "h left";
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + "h " + duration.toMinutesPart() + "m left";
        }
        return Math.max(1, duration.toMinutes()) + "m left";
    }

    private static void sendStatusLine(CommandSourceStack source, String label, String value, ChatFormatting valueColor) {
        source.sendSystemMessage(UasTranslations.literal(label)
                .withStyle(ChatFormatting.GRAY)
                .append(UasTranslations.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(value).withStyle(valueColor)));
    }

    private static ChatFormatting colorFor(UasHealthLevel level) {
        return switch (level) {
            case HEALTHY -> ChatFormatting.GREEN;
            case WARNING -> ChatFormatting.YELLOW;
            case ERROR -> ChatFormatting.RED;
        };
    }

    private static String formatLastSave(long lastSaveEpochMillis) {
        if (lastSaveEpochMillis < 0) {
            return "never";
        }
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(lastSaveEpochMillis));
    }

    private static void sendInspectLine(CommandSourceStack source, String label, String value, ChatFormatting valueColor) {
        source.sendSystemMessage(Component.literal(label)
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(valueColor)));
    }

    private static MutableComponent formatBidRecord(AuctionBidRecord record) {
        ChatFormatting resultColor = record.isAccepted() ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(record.getResult().name()).withStyle(resultColor))
                .append(Component.literal(" " + UasMoneyFormatter.display(record.getAmount())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" bidder=" + record.getBidderId()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" account=" + record.getBidderAccountId().map(UUID::toString).orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" at=" + formatDate(record.getTimestamp())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" reason=" + blankFallback(record.getReason(), "(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" settlement=" + record.getSettlementReference().orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" txn=" + record.getSettlementTransactionId().map(UUID::toString).orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" settlementResult=" + record.getSettlementResult().orElse("(none)")).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent formatFinancialEvent(AuctionFinancialEvent event) {
        ChatFormatting resultColor = event.success() ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(event.type()).withStyle(resultColor))
                .append(Component.literal(" " + UasMoneyFormatter.display(event.amount())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" auction=" + event.auctionId()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" ref=" + event.reference()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" txn=" + (event.transactionId() == null ? "(none)" : event.transactionId())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" result=" + blankFallback(event.result(), "(none)")).withStyle(ChatFormatting.GRAY));
    }

    private static ChatFormatting colorForState(AuctionState state) {
        return switch (state) {
            case ACTIVE -> ChatFormatting.GREEN;
            case DRAFT -> ChatFormatting.YELLOW;
            case ENDED, CLAIMED -> ChatFormatting.GOLD;
            case CANCELLED, FAILED_SETTLEMENT -> ChatFormatting.RED;
        };
    }

    private static String formatDate(LocalDateTime value) {
        return value == null ? "(none)" : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
