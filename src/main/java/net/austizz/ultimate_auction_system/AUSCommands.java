package net.austizz.ultimate_auction_system;

import com.mojang.brigadier.Command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
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

import static net.austizz.ultimate_auction_system.UltimateAuctionSystem.auctionHouse;

@EventBusSubscriber(modid = UltimateAuctionSystem.MODID, bus = EventBusSubscriber.Bus.GAME)
public class AUSCommands {
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

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ah")
                        .executes(AUSCommands::openAuctionGui)
                        .then(Commands.literal("list")
                                .executes(AUSCommands::sendAuctionList))
                        .then(Commands.literal("create")
                                .then(
                                        Commands.argument("Starting Price", StringArgumentType.string())
                                                .then(Commands.argument('"' + "Description" + '"', StringArgumentType.string())
                                                        .then(Commands.argument("Ending Date (dd-MM)", StringArgumentType.greedyString())
                                                                .executes(context -> {
                                                                        String rawStartingPrice = StringArgumentType.getString(context, "Starting Price");
                                                                        String rawEndingDate = StringArgumentType.getString(context, "Ending Date (dd-MM)");
                                                                        String description = StringArgumentType.getString(context, '"' + "Description" + '"');

                                                                        try{
                                                                            LocalDateTime endingDate = LocalDateTime.parse(rawEndingDate, FORMATTER);
                                                                            BigDecimal startingBidPrice = new BigDecimal(rawStartingPrice);
                                                                            ItemStack itemInHand = Objects.requireNonNull(context.getSource().getPlayer()).getMainHandItem();

                                                                            if(itemInHand.isEmpty()){
                                                                              throw INVALID_ITEMSTACK.create();
                                                                            }

                                                                            ServerPlayer player = context.getSource().getPlayer();
                                                                            long durationHours = Math.max(1L, java.time.Duration.between(LocalDateTime.now(), endingDate).toHours());
                                                                            AuctionActionResult result = UltimateAuctionSystem.auctionHouse.createAuctionFromMainHand(player, startingBidPrice, null, durationHours, description);
                                                                            context.getSource().sendSystemMessage(UasTranslations.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
                                                                            return Command.SINGLE_SUCCESS;
                                                                        } catch(DateTimeParseException e){
                                                                           throw INVALID_DATE.create();
                                                                        } catch(NumberFormatException e){
                                                                            throw INVALID_NUMBER.create();
                                                                        }
                                                                        catch (NullPointerException e){
                                                                            throw INVALID_ITEMSTACK.create();
                                                                        }
                                                                    }
                                                                )
                                                        )
                                                )
                                )
                        )
        );

        event.getDispatcher().register(
                Commands.literal("uas")
                        .then(Commands.literal("status")
                                .requires(source -> source.hasPermission(Config.adminStatusPermissionLevel))
                                .executes(AUSCommands::sendStatus)
                        )
                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(Config.adminStatusPermissionLevel))
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("auctionId", StringArgumentType.string())
                                                .executes(AUSCommands::inspectAuction)
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
                    .append(Component.literal("$" + item.getHighestBid()).withStyle(ChatFormatting.GOLD))
                    .append(UasTranslations.literal(" | Time left: ").withStyle(ChatFormatting.GRAY))
                    .append(timeLeft);

            line.withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(itemStack))
            ));

            line.withStyle(style -> style.withClickEvent(
                    new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ah")
            ));

            context.getSource().sendSystemMessage(line);
        }

        Component footer = Component.literal("=======================").withStyle(ChatFormatting.GOLD);
        context.getSource().sendSystemMessage(footer);

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
        sendInspectLine(source, "Starting price", "$" + auction.getStartingBidPrice().toPlainString(), ChatFormatting.GOLD);
        sendInspectLine(source, "Current price", "$" + auction.getCurrentPrice().toPlainString(), ChatFormatting.GOLD);
        sendInspectLine(source, "Buyout price", auction.getBuyoutPrice().map(price -> "$" + price.toPlainString()).orElse("(none)"), ChatFormatting.GOLD);
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
            return Command.SINGLE_SUCCESS;
        }
        for (AuctionBidRecord record : bidRecords) {
            source.sendSystemMessage(formatBidRecord(record));
        }
        return Command.SINGLE_SUCCESS;
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
                .append(Component.literal(" $" + record.getAmount().toPlainString()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" bidder=" + record.getBidderId()).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" account=" + record.getBidderAccountId().map(UUID::toString).orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" at=" + formatDate(record.getTimestamp())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" reason=" + blankFallback(record.getReason(), "(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" settlement=" + record.getSettlementReference().orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" txn=" + record.getSettlementTransactionId().map(UUID::toString).orElse("(none)")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" settlementResult=" + record.getSettlementResult().orElse("(none)")).withStyle(ChatFormatting.GRAY));
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
