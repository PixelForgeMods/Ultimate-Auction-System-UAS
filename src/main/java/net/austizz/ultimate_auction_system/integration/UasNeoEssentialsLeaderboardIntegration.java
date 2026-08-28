package net.austizz.ultimate_auction_system.integration;

import net.austizz.ultimate_auction_system.AuctionPlayerStats;
import net.austizz.ultimate_auction_system.AuctionPlayerStatsSavedData;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Optional live NeoEssentials leaderboard registrations backed by UAS auction statistics. */
public final class UasNeoEssentialsLeaderboardIntegration {
    private record Board(String id, String title, boolean higherIsBetter,
                         Function<MinecraftServer, Map<UUID, Number>> values,
                         Function<Number, String> formatter) {
    }

    private UasNeoEssentialsLeaderboardIntegration() {
    }

    public static void install() {
        try {
            Class<?> api = Class.forName("com.zerog.neoessentials.leaderboard.LeaderboardAPI");
            Class<?> definitionType = Class.forName("com.zerog.neoessentials.leaderboard.LeaderboardDefinition");
            Class<?> providerType = Class.forName("com.zerog.neoessentials.leaderboard.StatProvider");
            Constructor<?> definitionConstructor = definitionType.getConstructor(
                    String.class, String.class, String.class, boolean.class);
            Method register = api.getMethod("registerBoard", definitionType, providerType);

            List<Board> boards = boards();
            UltimateAuctionSystem.LOGGER.info("NeoEssentials detected; registering UAS auction leaderboard boards");
            for (Board board : boards) {
                Object definition = definitionConstructor.newInstance(
                        board.id(), board.title(), null, board.higherIsBetter());
                Object provider = Proxy.newProxyInstance(
                        providerType.getClassLoader(), new Class<?>[]{providerType},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getAllValues" -> board.values().apply(
                                    args != null && args.length > 0 && args[0] instanceof MinecraftServer server
                                            ? server : null);
                            case "formatValue" -> board.formatter().apply(
                                    args != null && args.length > 0 && args[0] instanceof Number number
                                            ? number : 0L);
                            default -> defaultValue(method.getReturnType());
                        });
                register.invoke(null, definition, provider);
            }
            UltimateAuctionSystem.LOGGER.info("Registered {} UAS auction leaderboards with NeoEssentials", boards.size());
        } catch (ClassNotFoundException ignored) {
            // NeoEssentials is an optional server-side integration.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.warn("Could not register UAS auction leaderboards with NeoEssentials", exception);
        }
    }

    private static List<Board> boards() {
        return List.of(
                new Board("uas_seller_revenue", UasTranslations.plain("en_us", "UAS Top Sellers by Revenue"), true,
                        UasNeoEssentialsLeaderboardIntegration::sellerRevenue,
                        UasNeoEssentialsLeaderboardIntegration::formatCents),
                new Board("uas_buyer_spending", UasTranslations.plain("en_us", "UAS Top Buyers by Spending"), true,
                        UasNeoEssentialsLeaderboardIntegration::buyerSpending,
                        UasNeoEssentialsLeaderboardIntegration::formatCents),
                new Board("uas_auctions_listed", UasTranslations.plain("en_us", "UAS Most Auctions Listed"), true,
                        UasNeoEssentialsLeaderboardIntegration::auctionsListed,
                        UasNeoEssentialsLeaderboardIntegration::formatCount),
                new Board("uas_auctions_won", UasTranslations.plain("en_us", "UAS Most Auctions Won"), true,
                        UasNeoEssentialsLeaderboardIntegration::auctionsWon,
                        UasNeoEssentialsLeaderboardIntegration::formatCount)
        );
    }

    private static Map<UUID, Number> sellerRevenue(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (AuctionPlayerStats stats : stats(server)) {
            if (stats.playerId() != null && stats.grossSoldValue().signum() > 0) {
                result.put(stats.playerId(), cents(stats.grossSoldValue()));
            }
        }
        return result;
    }

    private static Map<UUID, Number> buyerSpending(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (AuctionPlayerStats stats : stats(server)) {
            if (stats.playerId() != null && stats.grossSpentValue().signum() > 0) {
                result.put(stats.playerId(), cents(stats.grossSpentValue()));
            }
        }
        return result;
    }

    private static Map<UUID, Number> auctionsListed(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (AuctionPlayerStats stats : stats(server)) {
            if (stats.playerId() != null && stats.auctionsListed() > 0) {
                result.put(stats.playerId(), (long) stats.auctionsListed());
            }
        }
        return result;
    }

    private static Map<UUID, Number> auctionsWon(MinecraftServer server) {
        Map<UUID, Number> result = new LinkedHashMap<>();
        for (AuctionPlayerStats stats : stats(server)) {
            if (stats.playerId() != null && stats.auctionsWon() > 0) {
                result.put(stats.playerId(), (long) stats.auctionsWon());
            }
        }
        return result;
    }

    private static List<AuctionPlayerStats> stats(MinecraftServer server) {
        if (server == null) {
            return List.of();
        }
        try {
            return AuctionPlayerStatsSavedData.get(server).allStats();
        } catch (RuntimeException exception) {
            UltimateAuctionSystem.LOGGER.debug("UAS auction leaderboard data is not available yet", exception);
            return List.of();
        }
    }

    private static long cents(BigDecimal amount) {
        return amount.max(BigDecimal.ZERO).movePointRight(2).longValue();
    }

    private static String formatCents(Number value) {
        long cents = value.longValue();
        return String.format("$%,d.%02d", cents / 100L, Math.abs(cents % 100L));
    }

    private static String formatCount(Number value) {
        return String.format("%,d", value.longValue());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
