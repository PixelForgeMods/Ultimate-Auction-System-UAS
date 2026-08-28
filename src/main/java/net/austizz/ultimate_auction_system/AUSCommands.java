package net.austizz.ultimate_auction_system;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UasMoneyFormatter;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.austizz.ultimate_auction_system.api.UasPermissionAction;
import net.austizz.ultimate_auction_system.api.UasPermissions;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.austizz.ultimate_auction_system.network.UasPayloads;
import net.austizz.ultimate_auction_system.display.AuctionDisplayType;
import net.austizz.ultimate_auction_system.registry.UasBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
                        .then(Commands.literal("stats")
                                .executes(AUSCommands::sendPlayerAuctionStats))
                        .then(Commands.literal("leaderboard")
                                .executes(AUSCommands::sendMarketplaceLeaderboard))
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
                                .requires(source -> UasPermissions.has(source, UasPermissionAction.ADMIN))
                                .executes(AUSCommands::sendStatus)
                        )
                        .then(Commands.literal("admin")
                                .requires(source -> UasPermissions.has(source, UasPermissionAction.ADMIN))
                                .executes(AUSCommands::openAdminAuctionGui)
                                .then(Commands.literal("gui")
                                        .executes(AUSCommands::openAdminAuctionGui))
                                .then(Commands.literal("seller")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .executes(AUSCommands::sendSellerStats)))
                                .then(Commands.literal("report")
                                        .executes(context -> sendEconomyReport(context, "all"))
                                        .then(Commands.literal("day")
                                                .executes(context -> sendEconomyReport(context, "day")))
                                        .then(Commands.literal("week")
                                                .executes(context -> sendEconomyReport(context, "week")))
                                        .then(Commands.literal("all")
                                                .executes(context -> sendEconomyReport(context, "all"))))
                                .then(Commands.literal("export")
                                        .then(Commands.argument("format", StringArgumentType.word())
                                                .executes(context -> exportAuctions(context, false))
                                                .then(Commands.argument("filename", StringArgumentType.greedyString())
                                                        .executes(context -> exportAuctions(context, true)))))
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
                                .then(Commands.literal("forcecancel")
                                        .then(Commands.argument("auctionId", StringArgumentType.string())
                                                .then(Commands.literal("return")
                                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                                .executes(context -> forceCancelAuction(context, false))
                                                        )
                                                )
                                                .then(Commands.literal("recover")
                                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                                .executes(context -> forceCancelAuction(context, true))
                                                        )
                                                )
                                        )
                                )
                        )
                                .then(Commands.literal("display")
                                .then(Commands.literal("edit").executes(AUSCommands::startDisplayEditMode))
                                .then(Commands.literal("give")
                                        .then(Commands.literal("highest_bid").then(displaySizeArguments(false)))
                                        .then(Commands.literal("most_watched").then(displaySizeArguments(false)))
                                        .then(Commands.literal("ending_soon").then(displaySizeArguments(false)))
                                        .then(Commands.literal("random").then(displaySizeArguments(false)))
                                        .then(Commands.literal("manual").then(displaySizeArguments(true))))
                        )
        );
    }

    private static int giveAuctionDisplay(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(UasTranslations.tr("Only players can receive auction displays."));
            return 0;
        }
        AuctionDisplayType type = context.getNodes().stream()
                .map(node -> AuctionDisplayType.fromToken(node.getNode().getName()))
                .filter(candidate -> candidate.name().equals(context.getNodes().getLast().getNode().getName())
                        || context.getNodes().stream().anyMatch(n -> n.getNode().getName().equals(candidate.name().toLowerCase(java.util.Locale.ROOT))))
                .findFirst().orElse(AuctionDisplayType.HIGHEST_BID);
        int x = IntegerArgumentType.getInteger(context, "sizeX");
        int y = IntegerArgumentType.getInteger(context, "sizeY");
        int z = IntegerArgumentType.getInteger(context, "sizeZ");
        CompoundTag display = new CompoundTag();
        display.putString("id", UltimateAuctionSystem.MODID + ":auction_display");
        display.putString("Type", type.name()); display.putInt("SizeX", x); display.putInt("SizeY", y); display.putInt("SizeZ", z);
        if (type == AuctionDisplayType.MANUAL) {
            boolean hasAuctionId = context.getNodes().stream()
                    .anyMatch(node -> node.getNode().getName().equals("auctionId"));
            if (!hasAuctionId) {
                context.getSource().sendFailure(UasTranslations.tr("Manual displays require an auction ID."));
                return 0;
            }
            String raw = StringArgumentType.getString(context, "auctionId");
            try { display.putUUID("ManualAuction", UUID.fromString(raw)); }
            catch (IllegalArgumentException exception) {
                context.getSource().sendFailure(UasTranslations.tr("The manual auction ID is invalid."));
                return 0;
            }
        }
        ItemStack stack = new ItemStack(UasBlocks.AUCTION_DISPLAY.get());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(display));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        player.sendSystemMessage(UasTranslations.tr("Auction display added to your inventory.").withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> displaySizeArguments(boolean manual) {
        var sizeZ = Commands.argument("sizeZ", IntegerArgumentType.integer(1, 16));
        if (manual) {
            sizeZ.then(Commands.argument("auctionId", StringArgumentType.word())
                    .executes(AUSCommands::giveAuctionDisplay));
        } else {
            sizeZ.executes(AUSCommands::giveAuctionDisplay);
        }
        return Commands.argument("sizeX", IntegerArgumentType.integer(1, 16))
                .then(Commands.argument("sizeY", IntegerArgumentType.integer(1, 16)).then(sizeZ));
    }

    private static int startDisplayEditMode(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(UasTranslations.tr("Only players can edit auction displays."));
            return 0;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new net.austizz.ultimate_auction_system.network.DisplayEditModePayload(true));
        player.sendSystemMessage(UasTranslations.tr("Display edit mode enabled. Right-click an auction display to edit it. Hold Shift twice to exit.")
                .withStyle(ChatFormatting.GOLD));
        return Command.SINGLE_SUCCESS;
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
            context.getSource().sendFailure(UasTranslations.literal("Only players can open the UAS admin GUI."));
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
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.LIST);
        if (!permission.success()) {
            sendResult(context.getSource(), permission);
            return 0;
        }
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.CREATE);
        if (!rateLimit.success()) {
            sendResult(context.getSource(), rateLimit);
            return 0;
        }
        BigDecimal startingBid = parseMoney(StringArgumentType.getString(context, "Starting Price"));
        BigDecimal buyout = hasBuyout ? parseMoney(StringArgumentType.getString(context, "Buyout Price")) : BigDecimal.ZERO;
        if (startingBid == null || buyout == null) {
            context.getSource().sendFailure(UasTranslations.literal("Incorrect number format. Use whole dollars only, for example 50."));
            return 0;
        }
        int durationHours = IntegerArgumentType.getInteger(context, "Duration Hours");
        String description = StringArgumentType.getString(context, "Description");
        AuctionActionResult result = house.prepareAuctionFromMainHand(player, startingBid, buyout, durationHours, description);
        sendResult(context.getSource(), result);
        if (result.success()) {
            context.getSource().sendSystemMessage(UasTranslations.literal("[Confirm]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah confirm")))
                    .append(UasTranslations.literal(" "))
                    .append(UasTranslations.literal("[Discard]").withStyle(style -> style.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ah discard")))));
        }
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int confirmPendingListing(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can create auctions."));
            return 0;
        }
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.LIST);
        if (!permission.success()) {
            sendResult(context.getSource(), permission);
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
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.CANCEL_OWN);
        if (!permission.success()) {
            auctionHouse.sendActionAlert(player, permission);
            sendResult(context.getSource(), permission);
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.CANCEL);
        if (!rateLimit.success()) {
            auctionHouse.sendActionAlert(player, rateLimit);
            sendResult(context.getSource(), rateLimit);
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
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.CLAIM);
        if (!permission.success()) {
            auctionHouse.sendActionAlert(player, permission);
            sendResult(context.getSource(), permission);
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
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.BID);
        if (!permission.success()) {
            auctionHouse.sendActionAlert(player, permission);
            sendResult(context.getSource(), permission);
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        BigDecimal amount = parseMoney(StringArgumentType.getString(context, "amount"));
        if (auctionId == null) {
            return 0;
        }
        if (amount == null) {
            context.getSource().sendFailure(UasTranslations.literal("Incorrect number format. Use whole dollars only, for example 50."));
            return 0;
        }
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.BID);
        if (!rateLimit.success()) {
            auctionHouse.sendActionAlert(player, rateLimit);
            sendResult(context.getSource(), rateLimit);
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
        ServerPlayer player = source.getPlayer();
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.SEARCH);
        if (!rateLimit.success()) {
            sendResult(source, rateLimit);
            return 0;
        }
        UUID auctionId = parseAuctionId(source, StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionItem item = auctionHouse.getAuctionItem(auctionId);
        if (item == null) {
            source.sendFailure(UasTranslations.literal("Auction not found."));
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
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.BUYOUT);
        if (!permission.success()) {
            auctionHouse.sendActionAlert(player, permission);
            sendResult(context.getSource(), permission);
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        AuctionItem item = auctionHouse.getAuctionItem(auctionId);
        if (item == null) {
            context.getSource().sendFailure(UasTranslations.literal("Auction not found."));
            return 0;
        }
        if (!buyoutAvailable(item)) {
            context.getSource().sendFailure(UasTranslations.literal("This auction cannot be bought out right now."));
            return 0;
        }
        PENDING_BUYOUTS.put(player.getUUID(), new PendingBuyout(auctionId, System.currentTimeMillis() + BUYOUT_CONFIRM_WINDOW_MILLIS));
        context.getSource().sendSystemMessage(UasTranslations.literal("Buyout preview: ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(item.getDisplayTitle()).withStyle(ChatFormatting.AQUA))
                .append(UasTranslations.literal(" for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(UasMoneyFormatter.display(item.getBuyoutPrice().orElse(BigDecimal.ZERO))).withStyle(ChatFormatting.GREEN))
                .append(UasTranslations.literal(". Auction ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(auctionId)).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(". ").withStyle(ChatFormatting.GRAY))
                .append(chatAction("[Confirm Buyout]", "/ah buyout confirm " + auctionId, "Confirm this buyout and spend from your UBS primary account.", ChatFormatting.GREEN, ClickEvent.Action.RUN_COMMAND))
                .append(UasTranslations.literal(" "))
                .append(chatAction("[Cancel]", "/ah", "Do not buy out; open the Auction House instead.", ChatFormatting.RED, ClickEvent.Action.RUN_COMMAND)));
        return Command.SINGLE_SUCCESS;
    }

    private static int confirmBuyoutAuction(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null || auctionHouse == null) {
            context.getSource().sendFailure(UasTranslations.literal("Only players can buy out auctions."));
            return 0;
        }
        AuctionActionResult permission = UasPermissions.check(player, UasPermissionAction.BUYOUT);
        if (!permission.success()) {
            auctionHouse.sendActionAlert(player, permission);
            sendResult(context.getSource(), permission);
            return 0;
        }
        UUID auctionId = parseAuctionId(context.getSource(), StringArgumentType.getString(context, "auctionId"));
        if (auctionId == null) {
            return 0;
        }
        PendingBuyout pending = PENDING_BUYOUTS.get(player.getUUID());
        if (pending == null || !pending.matches(auctionId)) {
            context.getSource().sendFailure(UasTranslations.literal("Run /ah buyout ")
                    .append(Component.literal(String.valueOf(auctionId)))
                    .append(UasTranslations.literal(" first to preview and confirm this buyout.")));
            return 0;
        }
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.BUYOUT);
        if (!rateLimit.success()) {
            auctionHouse.sendActionAlert(player, rateLimit);
            sendResult(context.getSource(), rateLimit);
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
                UasPermissions.has(source, UasPermissionAction.ADMIN),
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

    private static int forceCancelAuction(CommandContext<CommandSourceStack> context, boolean recoverItems) {
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
        String reason = StringArgumentType.getString(context, "reason");
        ServerPlayer admin = source.getPlayer();
        UUID adminId = admin == null ? null : admin.getUUID();
        String adminName = admin == null ? "Console" : admin.getGameProfile().getName();
        AuctionAdminSavedData adminData = AuctionAdminSavedData.get(source.getServer());
        AuctionActionResult result = house.adminForceCancel(
                adminId,
                adminName,
                UasPermissions.has(source, UasPermissionAction.ADMIN),
                auctionId,
                AuctionDeliverySavedData.get(source.getServer()),
                adminData,
                recoverItems,
                reason
        );
        adminData.addAudit(
                "ADMIN_FORCE_CANCEL",
                adminId,
                adminName,
                String.valueOf(auctionId),
                reason,
                result.success(),
                result.message()
        );
        sendResult(source, result);
        return result.success() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int sendAuctionList(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.SEARCH);
        if (!rateLimit.success()) {
            sendResult(context.getSource(), rateLimit);
            return 0;
        }
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
        MutableComponent title = UasTranslations.literal("=== Auction ")
                .append(Component.literal(String.valueOf(auctionId)))
                .append(UasTranslations.literal(" ==="))
                .withStyle(ChatFormatting.GOLD);
        source.sendSystemMessage(title);
        source.sendSystemMessage(Component.literal(stack.getCount() + "x " + item.getDisplayTitle())
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(stack)))));
        source.sendSystemMessage(UasTranslations.literal("Seller: ").append(Component.literal(playerName(source, item.getPlayerId()))).withStyle(ChatFormatting.GRAY));
        source.sendSystemMessage(UasTranslations.literal("Current bid: ").append(Component.literal(UasMoneyFormatter.display(item.getHighestBid()))).withStyle(ChatFormatting.GOLD));
        source.sendSystemMessage(UasTranslations.literal("Buyout: ").append(UasTranslations.literal(item.getBuyoutPrice().map(UasMoneyFormatter::display).orElse("none"))).withStyle(ChatFormatting.GREEN));
        source.sendSystemMessage(UasTranslations.literal("State: ")
                .append(UasTranslations.literal(item.getState().name()))
                .append(UasTranslations.literal(" | "))
                .append(timeLeftComponent(item))
                .withStyle(ChatFormatting.GRAY));
        if (item.getDescription() != null && !item.getDescription().isBlank()) {
            source.sendSystemMessage(UasTranslations.literal("Description: ").append(Component.literal(item.getDescription())).withStyle(ChatFormatting.WHITE));
        }

        MutableComponent actions = UasTranslations.literal("Actions: ").withStyle(ChatFormatting.GRAY)
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
            context.getSource().sendFailure(UasTranslations.literal("Only players can view their auction listings."));
            return 0;
        }
        AuctionActionResult rateLimit = AuctionRateLimiter.checkAndMark(player, AuctionRateLimiter.Action.SEARCH);
        if (!rateLimit.success()) {
            sendResult(context.getSource(), rateLimit);
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

        context.getSource().sendSystemMessage(UasTranslations.literal("=== My Auctions: ")
                .append(Component.literal(safeFilter.name().toLowerCase()))
                .append(Component.literal(" (" + safePage + "/" + maxPage + ") ==="))
                .withStyle(ChatFormatting.GOLD));
        context.getSource().sendSystemMessage(UasTranslations.literal("Active ")
                .append(Component.literal(stats.active() + "/" + stats.activeLimit()))
                .append(UasTranslations.literal(" | Sold "))
                .append(Component.literal(String.valueOf(stats.sold())))
                .append(UasTranslations.literal(" | Cancelled "))
                .append(Component.literal(String.valueOf(stats.cancelled())))
                .append(UasTranslations.literal(" | Expired "))
                .append(Component.literal(String.valueOf(stats.expired())))
                .withStyle(ChatFormatting.GRAY));
        if (listings.isEmpty()) {
            context.getSource().sendSystemMessage(UasTranslations.literal("No matching auction listings.").withStyle(ChatFormatting.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        for (int index = start; index < end; index++) {
            sendMineRow(context.getSource(), listings.get(index));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void sendMineRow(CommandSourceStack source, AuctionItem item) {
        ItemStack stack = item.getItem();
        MutableComponent status = sellerStatusLabel(source, item);
        MutableComponent row = Component.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(stack.getCount() + "x " + stack.getHoverName().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | " + UasMoneyFormatter.display(item.getCurrentPrice())).withStyle(ChatFormatting.GOLD))
                .append(UasTranslations.literal(" | "))
                .append(status.withStyle(ChatFormatting.GRAY));

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
        return UasTranslations.literal(label).withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, UasTranslations.literal(hover))));
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

    private static MutableComponent sellerStatusLabel(CommandSourceStack source, AuctionItem item) {
        if (item.getState() == AuctionState.CANCELLED) {
            return UasTranslations.literal("cancelled");
        }
        if (item.getHighestBidderId() != null) {
            return UasTranslations.literal("highest bidder ").append(Component.literal(playerName(source, item.getHighestBidderId())));
        }
        if (item.getState() == AuctionState.ACTIVE && !item.isExpired()) {
            return UasTranslations.literal("active, ").append(timeLeftComponent(item));
        }
        return UasTranslations.literal("expired without bids");
    }

    private static int sendPlayerAuctionStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(UasTranslations.tr("Only players can view auction stats."));
            return 0;
        }
        AuctionPlayerStatsSavedData statsData;
        try {
            statsData = AuctionPlayerStatsSavedData.get(source.getServer());
        } catch (RuntimeException exception) {
            source.sendFailure(UasTranslations.tr("Auction stats are unavailable right now."));
            return 0;
        }

        AuctionPlayerStats stats = statsData.statsFor(player.getUUID(), player.getGameProfile().getName());
        source.sendSystemMessage(UasTranslations.tr("=== Your Auction Stats ===").withStyle(ChatFormatting.GOLD));
        sendAuctionStatsLine(source, "Auctions listed", String.valueOf(stats.auctionsListed()), ChatFormatting.GREEN);
        sendAuctionStatsLine(source, "Auctions won", String.valueOf(stats.auctionsWon()), ChatFormatting.GREEN);
        sendAuctionStatsLine(source, "Gross sold value", UasMoneyFormatter.display(stats.grossSoldValue()), ChatFormatting.GOLD);
        sendAuctionStatsLine(source, "Gross spent value", UasMoneyFormatter.display(stats.grossSpentValue()), ChatFormatting.YELLOW);
        sendAuctionStatsLine(source, "Marketplace rank", rankLabel(statsData.marketplaceRank(player.getUUID())), ChatFormatting.AQUA);
        sendAuctionStatsLine(source, "Seller rank", rankLabel(statsData.sellerRank(player.getUUID())), ChatFormatting.AQUA);
        sendAuctionStatsLine(source, "Buyer rank", rankLabel(statsData.buyerRank(player.getUUID())), ChatFormatting.AQUA);
        return Command.SINGLE_SUCCESS;
    }

    private static int sendMarketplaceLeaderboard(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!Config.marketplaceLeaderboardsEnabled) {
            source.sendFailure(UasTranslations.tr("Auction leaderboards are disabled on this server."));
            return 0;
        }
        AuctionPlayerStatsSavedData statsData;
        try {
            statsData = AuctionPlayerStatsSavedData.get(source.getServer());
        } catch (RuntimeException exception) {
            source.sendFailure(UasTranslations.tr("Auction stats are unavailable right now."));
            return 0;
        }

        source.sendSystemMessage(UasTranslations.tr("=== Auction Leaderboard ===").withStyle(ChatFormatting.GOLD));
        sendLeaderboardRows(source, "Top sellers", statsData.topSellers(5), true);
        sendLeaderboardRows(source, "Top buyers", statsData.topBuyers(5), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendAuctionStatsLine(CommandSourceStack source, String label, Object value, ChatFormatting valueColor) {
        source.sendSystemMessage(UasTranslations.tr("{0}: {1}", UasTranslations.tr(label), value).withStyle(valueColor));
    }

    private static MutableComponent rankLabel(int rank) {
        return rank <= 0 ? UasTranslations.tr("Unranked") : Component.literal("#" + rank);
    }

    private static void sendLeaderboardRows(CommandSourceStack source, String title, List<AuctionPlayerStats> rows, boolean sellers) {
        source.sendSystemMessage(UasTranslations.tr(title).withStyle(ChatFormatting.GOLD));
        if (rows == null || rows.isEmpty()) {
            source.sendSystemMessage(UasTranslations.tr("- No stats yet").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int index = 0; index < rows.size(); index++) {
            AuctionPlayerStats stats = rows.get(index);
            MutableComponent row = sellers
                    ? UasTranslations.tr("#{0} {1}: {2} sold, {3} listed", index + 1, stats.playerName(), UasMoneyFormatter.display(stats.grossSoldValue()), stats.auctionsListed())
                    : UasTranslations.tr("#{0} {1}: {2} spent, {3} won", index + 1, stats.playerName(), UasMoneyFormatter.display(stats.grossSpentValue()), stats.auctionsWon());
            source.sendSystemMessage(row.withStyle(ChatFormatting.GRAY));
        }
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
        context.getSource().sendSystemMessage(UasTranslations.literal("=== UAS Seller Auctions ===").withStyle(ChatFormatting.GOLD));
        context.getSource().sendSystemMessage(UasTranslations.literal("Seller: ").append(Component.literal(String.valueOf(sellerId))).withStyle(ChatFormatting.GRAY));
        context.getSource().sendSystemMessage(UasTranslations.literal("Active: ").append(Component.literal(stats.active() + "/" + stats.activeLimit())).withStyle(ChatFormatting.GREEN));
        context.getSource().sendSystemMessage(UasTranslations.literal("Sold: ").append(Component.literal(String.valueOf(stats.sold()))).withStyle(ChatFormatting.GOLD));
        context.getSource().sendSystemMessage(UasTranslations.literal("Cancelled: ").append(Component.literal(String.valueOf(stats.cancelled()))).withStyle(ChatFormatting.RED));
        context.getSource().sendSystemMessage(UasTranslations.literal("Expired: ").append(Component.literal(String.valueOf(stats.expired()))).withStyle(ChatFormatting.YELLOW));
        context.getSource().sendSystemMessage(UasTranslations.literal("Total: ").append(Component.literal(String.valueOf(stats.total()))).withStyle(ChatFormatting.AQUA));
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
        source.sendSystemMessage(UasTranslations.literal("=== UAS Auction Inspect ===").withStyle(ChatFormatting.GOLD));
        sendInspectLine(source, "Auction ID", auction.getAuctionId().toString(), ChatFormatting.AQUA);
        sendInspectLine(source, "Item", stack.getCount() + "x " + stack.getHoverName().getString(), ChatFormatting.WHITE);
        sendInspectLine(source, "Description", blankFallback(auction.getDescription(), "(empty)"), ChatFormatting.GRAY);
        sendInspectLine(source, "State", auction.getState().name(), colorForState(auction.getState()));
        sendInspectLine(source, "Format", auction.getFormat().serializedName(), auction.isSealedBid() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
        sendInspectLine(source, "Seller player", String.valueOf(auction.getPlayerId()), ChatFormatting.GRAY);
        sendInspectLine(source, "Seller account", String.valueOf(auction.getSellerAccountId()), ChatFormatting.GRAY);
        sendInspectLine(source, "Starting price", UasMoneyFormatter.display(auction.getStartingBidPrice()), ChatFormatting.GOLD);
        sendInspectLine(source, "Current price", UasMoneyFormatter.display(auction.getCurrentPrice()), ChatFormatting.GOLD);
        sendInspectLine(source, "Buyout price", auction.getBuyoutPrice().map(UasMoneyFormatter::display).orElse("(none)"), ChatFormatting.GOLD);
        sendInspectLine(source, "Reserve price", auction.getReservePrice().map(UasMoneyFormatter::display).orElse("(none)"), ChatFormatting.GOLD);
        sendInspectLine(source, "Reserve met", auction.hasReservePrice() ? (auction.isReserveMet() ? "yes" : "no") : "(none)", auction.isReserveMet() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
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
        source.sendSystemMessage(UasTranslations.literal("Bid history (")
                .append(Component.literal(String.valueOf(bidRecords.size())))
                .append(UasTranslations.literal(")"))
                .withStyle(ChatFormatting.GOLD));
        if (bidRecords.isEmpty()) {
            source.sendSystemMessage(UasTranslations.literal("- No bid records.").withStyle(ChatFormatting.GRAY));
        } else {
            for (AuctionBidRecord record : bidRecords) {
                source.sendSystemMessage(formatBidRecord(record));
            }
        }

        List<AuctionSuspicionSignal> suspicionSignals = house.getSuspicionSignals(auctionId);
        source.sendSystemMessage(UasTranslations.tr("Suspicion signals ({0})", suspicionSignals.size()).withStyle(ChatFormatting.GOLD));
        if (suspicionSignals.isEmpty()) {
            source.sendSystemMessage(UasTranslations.literal("- ")
                    .append(UasTranslations.tr("No suspicion signals."))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            for (AuctionSuspicionSignal signal : suspicionSignals) {
                source.sendSystemMessage(formatSuspicionSignal(signal));
            }
        }

        List<AuctionFinancialEvent> financialEvents = auction.getFinancialEvents();
        source.sendSystemMessage(UasTranslations.literal("Financial events (")
                .append(Component.literal(String.valueOf(financialEvents.size())))
                .append(UasTranslations.literal(")"))
                .withStyle(ChatFormatting.GOLD));
        if (financialEvents.isEmpty()) {
            source.sendSystemMessage(UasTranslations.literal("- No financial events.").withStyle(ChatFormatting.GRAY));
        } else {
            for (AuctionFinancialEvent event : financialEvents) {
                source.sendSystemMessage(formatFinancialEvent(event));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int sendEconomyReport(CommandContext<CommandSourceStack> context, String windowToken) {
        CommandSourceStack source = context.getSource();
        AuctionHouse house = auctionHouse;
        if (house == null) {
            source.sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }

        AuctionEconomyReport report = house.buildEconomyReport(windowToken);
        source.sendSystemMessage(UasTranslations.tr("=== UAS Economy Report: {0} ===", Component.translatable(report.label())).withStyle(ChatFormatting.GOLD));
        sendReportLine(source, "Active listings", String.valueOf(report.activeListings()), ChatFormatting.GREEN);
        sendReportLine(source, "Completed sales", String.valueOf(report.completedSales()), ChatFormatting.GREEN);
        sendReportLine(source, "Gross volume", report.grossVolume(), ChatFormatting.GOLD);
        sendReportLine(source, "Fees", report.fees(), ChatFormatting.YELLOW);
        sendReportLine(source, "Taxes", report.taxes(), ChatFormatting.RED);
        sendReportLine(source, "Failed settlements", String.valueOf(report.failedSettlements()), report.failedSettlements() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
        sendReportRows(source, "Top sellers", report.topSellers());
        sendReportRows(source, "Top categories", report.topCategories());
        sendReportRows(source, "Top items", report.topItems());
        return Command.SINGLE_SUCCESS;
    }

    private static int exportAuctions(CommandContext<CommandSourceStack> context, boolean hasFilename) {
        CommandSourceStack source = context.getSource();
        AuctionHouse house = auctionHouse;
        if (house == null) {
            source.sendFailure(UasTranslations.literal("Auction house is not initialized."));
            return 0;
        }

        Optional<AuctionDataExporter.Format> format = AuctionDataExporter.Format.fromToken(StringArgumentType.getString(context, "format"));
        if (format.isEmpty()) {
            source.sendFailure(UasTranslations.tr("Export format must be csv or json."));
            return 0;
        }

        String filename = hasFilename ? StringArgumentType.getString(context, "filename") : "";
        Path serverRoot = source.getServer().getWorldPath(LevelResource.ROOT);
        List<AuctionItem> auctionSnapshot = List.copyOf(house.getAuctionItems().values());
        source.sendSystemMessage(UasTranslations.tr("Auction export started for {0} auction(s).", auctionSnapshot.size()).withStyle(ChatFormatting.YELLOW));
        CompletableFuture
                .supplyAsync(() -> AuctionDataExporter.export(serverRoot, auctionSnapshot, format.get(), filename))
                .whenComplete((result, throwable) -> source.getServer().execute(() -> finishAuctionExport(source, format.get(), result, throwable)));
        return Command.SINGLE_SUCCESS;
    }

    private static void finishAuctionExport(CommandSourceStack source,
                                            AuctionDataExporter.Format format,
                                            AuctionDataExporter.ExportResult result,
                                            Throwable throwable) {
        AuctionDataExporter.ExportResult safeResult = throwable == null
                ? result
                : AuctionDataExporter.ExportResult.fail(exportFailureMessage(throwable));
        auditExport(source, format, safeResult);
        if (safeResult == null || !safeResult.success()) {
            String message = safeResult == null ? "Auction export returned no result." : safeResult.message();
            source.sendFailure(UasTranslations.tr("Auction export failed: {0}", message));
            return;
        }

        source.sendSystemMessage(UasTranslations.tr(
                "Auction export completed: {0} auction(s) written to {1}",
                safeResult.auctionCount(),
                safeResult.path().toString()
        ).withStyle(ChatFormatting.GREEN));
    }

    private static String exportFailureMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static void auditExport(CommandSourceStack source,
                                    AuctionDataExporter.Format format,
                                    AuctionDataExporter.ExportResult result) {
        try {
            ServerPlayer admin = source.getEntity() instanceof ServerPlayer player ? player : null;
            UUID adminId = admin == null ? null : admin.getUUID();
            String adminName = admin == null ? source.getTextName() : admin.getGameProfile().getName();
            String target = result != null && result.path() != null ? result.path().toString() : "uas_exports";
            String message = result == null ? "Auction export returned no result." : result.message();
            if (result != null && result.success()) {
                message = message + " Exported " + result.auctionCount() + " auction(s).";
            }
            AuctionAdminSavedData.get(source.getServer()).addAudit(
                    "AUCTION_EXPORT",
                    adminId,
                    adminName,
                    target,
                    "format=" + (format == null ? "unknown" : format.extension()),
                    result != null && result.success(),
                    message
            );
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Could not audit auction export: {}", exception.getMessage());
        }
    }

    private static void sendReportLine(CommandSourceStack source, String label, String value, ChatFormatting valueColor) {
        source.sendSystemMessage(UasTranslations.tr("{0}: {1}", UasTranslations.tr(label), value).withStyle(valueColor));
    }

    private static void sendReportRows(CommandSourceStack source, String title, List<AuctionEconomyReport.Row> rows) {
        source.sendSystemMessage(UasTranslations.tr(title).withStyle(ChatFormatting.GOLD));
        if (rows.isEmpty()) {
            source.sendSystemMessage(UasTranslations.tr("- No data").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (AuctionEconomyReport.Row row : rows) {
            source.sendSystemMessage(UasTranslations.tr("- {0}: {1} sale(s), {2}", row.label(), row.count(), row.amount()).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendResult(CommandSourceStack source, AuctionActionResult result) {
        if (source == null || result == null || result.message().isBlank()) {
            return;
        }
        source.sendSystemMessage(UasTranslations.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
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
            source.sendFailure(UasTranslations.literal("Invalid auction ID. Use the UUID shown in /ah mine or admin inspect."));
            return null;
        }
    }

    private static UUID resolvePlayerId(CommandSourceStack source, String raw) {
        if (raw == null || raw.isBlank()) {
            source.sendFailure(UasTranslations.literal("Player name or UUID is required."));
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
        source.sendFailure(UasTranslations.literal("Could not resolve player. Use an online player name or UUID."));
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
        return timeLeftComponent(item).getString();
    }

    private static MutableComponent timeLeftComponent(AuctionItem item) {
        java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), item.getDateOfEnd());
        if (duration.isNegative() || duration.isZero()) {
            return UasTranslations.literal("ended");
        }
        long days = duration.toDays();
        if (days > 0) {
            return Component.translatable("{0}d {1}h left", days, duration.toHoursPart());
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return Component.translatable("{0}h {1}m left", hours, duration.toMinutesPart());
        }
        return Component.translatable("{0}m left", Math.max(1, duration.toMinutes()));
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
        source.sendSystemMessage(UasTranslations.literal(label)
                .withStyle(ChatFormatting.GRAY)
                .append(UasTranslations.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(value).withStyle(valueColor)));
    }

    private static MutableComponent formatBidRecord(AuctionBidRecord record) {
        ChatFormatting resultColor = record.isAccepted() ? ChatFormatting.GREEN : ChatFormatting.RED;
        return UasTranslations.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(record.getResult().name()).withStyle(resultColor))
                .append(Component.literal(" " + UasMoneyFormatter.display(record.getAmount())).withStyle(ChatFormatting.GOLD))
                .append(UasTranslations.literal(" bidder=").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(record.getBidderId())).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" account=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(record.getBidderAccountId().map(UUID::toString).orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" at=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(formatDate(record.getTimestamp())).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" reason=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(blankFallback(record.getReason(), "(none)")).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" settlement=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(record.getSettlementReference().orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" txn=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(record.getSettlementTransactionId().map(UUID::toString).orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" settlementResult=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(record.getSettlementResult().orElse("(none)")).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent formatSuspicionSignal(AuctionSuspicionSignal signal) {
        return UasTranslations.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(suspicionTypeLabel(signal.type()).withStyle(ChatFormatting.YELLOW))
                .append(UasTranslations.literal(" "))
                .append(suspicionDetail(signal).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" at=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(formatDate(signal.observedAt())).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent suspicionTypeLabel(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case AuctionSuspicionSignal.RAPID_BID_ESCALATION -> UasTranslations.tr("Rapid bid escalation");
            case AuctionSuspicionSignal.REPEATED_BIDDER_PAIR -> UasTranslations.tr("Repeated bidder pair");
            case AuctionSuspicionSignal.SELLER_SELF_BID -> UasTranslations.tr("Seller self-bid signal");
            case AuctionSuspicionSignal.REPEATED_CANCELLED_LISTINGS -> UasTranslations.tr("Repeated cancelled listings");
            default -> UasTranslations.literal(normalized.isBlank() ? "Unknown suspicion signal" : normalized);
        };
    }

    private static MutableComponent suspicionDetail(AuctionSuspicionSignal signal) {
        String type = signal.type();
        if (AuctionSuspicionSignal.RAPID_BID_ESCALATION.equals(type)) {
            return UasTranslations.tr(
                    "Rapid price movement: {0} bids in {1} minutes, {2} to {3}",
                    signal.evidenceCount(),
                    Math.max(1, signal.windowSeconds() / 60),
                    UasMoneyFormatter.display(signal.startAmount()),
                    UasMoneyFormatter.display(signal.endAmount())
            );
        }
        if (AuctionSuspicionSignal.REPEATED_BIDDER_PAIR.equals(type)) {
            return UasTranslations.tr(
                    "Repeated outbid pair: {0} and {1}, {2} turns",
                    blankFallback(signal.primaryPlayerName(), "(unknown)"),
                    blankFallback(signal.secondaryPlayerName(), "(unknown)"),
                    signal.evidenceCount()
            );
        }
        if (AuctionSuspicionSignal.SELLER_SELF_BID.equals(type)) {
            return UasTranslations.tr(
                    "Seller self-bid evidence: {0} event(s) for {1}",
                    signal.evidenceCount(),
                    blankFallback(signal.primaryPlayerName(), "(unknown)")
            );
        }
        if (AuctionSuspicionSignal.REPEATED_CANCELLED_LISTINGS.equals(type)) {
            return UasTranslations.tr(
                    "Repeated cancellations: {0} listings in {1} hours by {2}",
                    signal.evidenceCount(),
                    Math.max(1, signal.windowSeconds() / 3600),
                    blankFallback(signal.primaryPlayerName(), "(unknown)")
            );
        }
        return UasTranslations.tr("Evidence count: {0}", signal.evidenceCount());
    }

    private static MutableComponent formatFinancialEvent(AuctionFinancialEvent event) {
        ChatFormatting resultColor = event.success() ? ChatFormatting.GREEN : ChatFormatting.RED;
        return UasTranslations.literal("- ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(event.type()).withStyle(resultColor))
                .append(Component.literal(" " + UasMoneyFormatter.display(event.amount())).withStyle(ChatFormatting.GOLD))
                .append(UasTranslations.literal(" auction=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(String.valueOf(event.auctionId())).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" ref=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(event.reference()).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" txn=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(String.valueOf(event.transactionId() == null ? "(none)" : event.transactionId())).withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(" result=").withStyle(ChatFormatting.GRAY))
                .append(UasTranslations.literal(blankFallback(event.result(), "(none)")).withStyle(ChatFormatting.GRAY));
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
