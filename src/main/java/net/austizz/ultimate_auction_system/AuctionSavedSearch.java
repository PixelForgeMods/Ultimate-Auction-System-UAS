package net.austizz.ultimate_auction_system;

import net.minecraft.nbt.CompoundTag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

public record AuctionSavedSearch(
        UUID searchId,
        UUID playerId,
        String name,
        String search,
        String category,
        String sort,
        String minimumPrice,
        String maximumPrice,
        long maximumHoursLeft,
        String modId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final String SEARCH_ID_TAG = "searchId";
    private static final String PLAYER_ID_TAG = "playerId";
    private static final String NAME_TAG = "name";
    private static final String SEARCH_TAG = "search";
    private static final String CATEGORY_TAG = "category";
    private static final String SORT_TAG = "sort";
    private static final String MINIMUM_PRICE_TAG = "minimumPrice";
    private static final String MAXIMUM_PRICE_TAG = "maximumPrice";
    private static final String MAXIMUM_HOURS_LEFT_TAG = "maximumHoursLeft";
    private static final String MOD_ID_TAG = "modId";
    private static final String CREATED_AT_TAG = "createdAt";
    private static final String UPDATED_AT_TAG = "updatedAt";

    public AuctionSavedSearch {
        searchId = searchId == null ? UUID.randomUUID() : searchId;
        name = sanitizeName(name);
        search = search == null ? "" : search.trim();
        category = normalizeCategory(category);
        sort = normalizeSort(sort);
        minimumPrice = sanitizePrice(minimumPrice);
        maximumPrice = sanitizePrice(maximumPrice);
        maximumHoursLeft = Math.max(0L, maximumHoursLeft);
        modId = modId == null ? "" : modId.trim().toLowerCase(Locale.ROOT);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static AuctionSavedSearch create(UUID playerId, String name, AuctionUiQuery query) {
        LocalDateTime now = LocalDateTime.now();
        AuctionUiQuery safeQuery = query == null ? AuctionUiQuery.defaults() : query;
        return new AuctionSavedSearch(
                UUID.randomUUID(),
                playerId,
                name,
                safeQuery.safeSearch(),
                safeQuery.safeCategory().name(),
                safeQuery.safeSort().name(),
                priceString(safeQuery.minimumPrice()),
                priceString(safeQuery.maximumPrice()),
                Math.max(0L, safeQuery.maximumHoursLeft()),
                safeQuery.safeModId(),
                now,
                now
        );
    }

    public AuctionSavedSearch withQuery(String newName, AuctionUiQuery query) {
        AuctionUiQuery safeQuery = query == null ? AuctionUiQuery.defaults() : query;
        return new AuctionSavedSearch(
                searchId,
                playerId,
                newName,
                safeQuery.safeSearch(),
                safeQuery.safeCategory().name(),
                safeQuery.safeSort().name(),
                priceString(safeQuery.minimumPrice()),
                priceString(safeQuery.maximumPrice()),
                Math.max(0L, safeQuery.maximumHoursLeft()),
                safeQuery.safeModId(),
                createdAt,
                LocalDateTime.now()
        );
    }

    public AuctionSavedSearch withName(String newName) {
        return new AuctionSavedSearch(
                searchId,
                playerId,
                newName,
                search,
                category,
                sort,
                minimumPrice,
                maximumPrice,
                maximumHoursLeft,
                modId,
                createdAt,
                LocalDateTime.now()
        );
    }

    public AuctionUiQuery toQuery() {
        return new AuctionUiQuery(
                search,
                AuctionCategory.fromToken(category),
                parsePrice(minimumPrice),
                parsePrice(maximumPrice),
                maximumHoursLeft,
                AuctionSort.fromToken(sort),
                modId
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(SEARCH_ID_TAG, searchId);
        tag.putUUID(PLAYER_ID_TAG, playerId);
        tag.putString(NAME_TAG, name);
        tag.putString(SEARCH_TAG, search);
        tag.putString(CATEGORY_TAG, category);
        tag.putString(SORT_TAG, sort);
        tag.putString(MINIMUM_PRICE_TAG, minimumPrice);
        tag.putString(MAXIMUM_PRICE_TAG, maximumPrice);
        tag.putLong(MAXIMUM_HOURS_LEFT_TAG, maximumHoursLeft);
        tag.putString(MOD_ID_TAG, modId);
        tag.putString(CREATED_AT_TAG, createdAt.toString());
        tag.putString(UPDATED_AT_TAG, updatedAt.toString());
        return tag;
    }

    public static AuctionSavedSearch load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID(SEARCH_ID_TAG) || !tag.hasUUID(PLAYER_ID_TAG)) {
            return null;
        }
        String name = sanitizeName(tag.getString(NAME_TAG));
        if (name.isBlank()) {
            return null;
        }
        return new AuctionSavedSearch(
                tag.getUUID(SEARCH_ID_TAG),
                tag.getUUID(PLAYER_ID_TAG),
                name,
                tag.getString(SEARCH_TAG),
                tag.getString(CATEGORY_TAG),
                tag.getString(SORT_TAG),
                tag.getString(MINIMUM_PRICE_TAG),
                tag.getString(MAXIMUM_PRICE_TAG),
                tag.getLong(MAXIMUM_HOURS_LEFT_TAG),
                tag.getString(MOD_ID_TAG),
                parseDateTime(tag.getString(CREATED_AT_TAG)),
                parseDateTime(tag.getString(UPDATED_AT_TAG))
        );
    }

    public static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (collapsed.length() <= 32) {
            return collapsed;
        }
        return collapsed.substring(0, 32).trim();
    }

    private static String normalizeCategory(String raw) {
        return AuctionCategory.fromToken(raw).name();
    }

    private static String normalizeSort(String raw) {
        return AuctionSort.fromToken(raw).name();
    }

    private static String sanitizePrice(String raw) {
        return priceString(parsePrice(raw));
    }

    private static BigDecimal parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim().replace("$", "").replace(",", ""));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String priceString(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return amount.stripTrailingZeros().toPlainString();
    }

    private static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.now();
        }
    }
}
