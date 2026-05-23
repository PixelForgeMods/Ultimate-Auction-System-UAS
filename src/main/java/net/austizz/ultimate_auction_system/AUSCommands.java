package net.austizz.ultimate_auction_system;

import com.mojang.brigadier.Command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.austizz.ultimate_auction_system.banking.UasBankingService;
import net.austizz.ultimate_auction_system.banking.UbsBankingService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
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
import java.util.Objects;

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
            Component.literal("❌ Incorrect date format, use format dd-MM (day (24) - month (12)")
    );
    private static final SimpleCommandExceptionType INVALID_NUMBER = new SimpleCommandExceptionType(
            Component.literal("❌ Incorrect number format, do not use letters, symbols etc. To indicate a decimal number, use '.' example (50.42)")
    );
    private static final SimpleCommandExceptionType INVALID_ITEMSTACK = new SimpleCommandExceptionType(
            Component.literal("❌ Incorrect Item, This means that either main hand is empty, or some kind of bug appeared. If issue persists, contact mod developers.")
    );

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ah")
                        .executes(context -> {
                            // 1. Create a clean header
                            Component header = Component.literal("\n=== [ ")
                                    .withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("ACTIVE AUCTIONS").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                    .append(Component.literal(" ] ===").withStyle(ChatFormatting.GOLD));

                            context.getSource().sendSystemMessage(header);

// 2. Loop through and build high-utility, rich text rows
                            for (AuctionItem item : auctionHouse.getAuctionItems().values()) {
                                ItemStack itemStack = item.getItem();
                                String displayName = itemStack.getHoverName().getString();
                                int count = itemStack.getCount();

                                // Calculate remaining duration
                                java.time.Duration duration = java.time.Duration.between(java.time.LocalDateTime.now(), item.getDateOfEnd());
                                String timeLeftStr;
                                ChatFormatting timeColor;

                                if (duration.isNegative() || duration.isZero()) {
                                    timeLeftStr = "Ended";
                                    timeColor = ChatFormatting.DARK_RED;
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
                                        timeColor = ChatFormatting.RED; // Red alert for less than a minute!
                                    }
                                }

                                // Assemble the row components
                                MutableComponent line = Component.literal("• ")
                                        .withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(count + "x " + displayName).withStyle(ChatFormatting.AQUA))
                                        .append(Component.literal(" | Bid: ").withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal("$" + item.getHighestBid()).withStyle(ChatFormatting.GOLD)) // Changed to highest bid
                                        .append(Component.literal(" | Time left: ").withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal(timeLeftStr).withStyle(timeColor));

                                // Add hover profile matching vanilla item system
                                line.withStyle(style -> style.withHoverEvent(
                                        new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(itemStack))
                                ));

                                // Add optional interactive click-to-bid mechanism
                                line.withStyle(style -> style.withClickEvent(
                                        new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/bid " + item.getAuctionId() + " ")
                                ));

                                context.getSource().sendSystemMessage(line);
                            }

                            Component footer = Component.literal("=======================").withStyle(ChatFormatting.GOLD);
                            context.getSource().sendSystemMessage(footer);

                            return 1;
                        })
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

                                                                            UltimateAuctionSystem.auctionHouse.addAuctionItem(new AuctionItem(itemInHand, description, endingDate, LocalDateTime.now(), startingBidPrice, context.getSource().getPlayer().getUUID()));
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
        );
    }

    private static int sendStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UasBankingService bankingService = new UbsBankingService();
        boolean ubsLoaded = ModList.get().isLoaded("ultimatebankingsystem");
        boolean ubsServerAvailable = bankingService.isAvailable();
        AuctionHouse house = auctionHouse;
        int auctionCount = house == null ? 0 : house.getAuctionItems().size();
        AuctionStorageHealth storageHealth = house == null
                ? new AuctionStorageHealth(UasHealthLevel.ERROR, "Auction house is not initialized.", -1L)
                : house.getStorageHealth();

        source.sendSystemMessage(Component.literal("=== UAS Status ===").withStyle(ChatFormatting.GOLD));
        sendStatusLine(source, "UBS loaded", ubsLoaded ? "yes" : "no", ubsLoaded ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendStatusLine(source, "UBS API version", bankingService.getApiVersion(), ubsLoaded ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        sendStatusLine(source, "UBS server available", ubsServerAvailable ? "yes" : "no", ubsServerAvailable ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendStatusLine(source, "Active auctions", String.valueOf(auctionCount), ChatFormatting.AQUA);
        sendStatusLine(source, "Storage", storageHealth.message(), colorFor(storageHealth.level()));
        sendStatusLine(source, "Last save", formatLastSave(storageHealth.lastSaveEpochMillis()), colorFor(storageHealth.level()));
        sendStatusLine(source, "Config", Config.lastConfigLoadMessage, Config.lastConfigLoadHealthy ? ChatFormatting.GREEN : ChatFormatting.YELLOW);

        return Command.SINGLE_SUCCESS;
    }

    private static void sendStatusLine(CommandSourceStack source, String label, String value, ChatFormatting valueColor) {
        source.sendSystemMessage(Component.literal(label + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColor)));
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
}
