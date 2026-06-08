package net.austizz.ultimate_auction_system.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.austizz.ultimate_auction_system.UltimateAuctionSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight UAS localization helper.
 *
 * <p>Like UBS, UAS uses the original English text as the translation key.
 * Missing keys naturally render as the English fallback text.</p>
 */
public final class UasTranslations {
    private static final int MAX_RESOLVE_DEPTH = 6;
    private static final String FALLBACK_LOCALE = "en_us";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?\\$?\\d[\\d,]*(?:\\.\\d+)?%?$");
    private static final Pattern ISO_DATE_TIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?$");
    private static final Pattern RESOURCE_LOCATION_PATTERN = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern MINECRAFT_PLACEHOLDER_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static final Map<String, Map<String, String>> PLAIN_TRANSLATIONS = new ConcurrentHashMap<>();
    private static final String[] LEADING_PREFIX_KEYS = {
            "Auction ",
            "Bid must be at least ",
            "Auction duration cannot be longer than ",
            "A bundled auction can include up to ",
            "Invalid banned auction entry: ",
            "Auction not found: ",
            "Auction-house ban updated for ",
            "active, ",
            "highest bidder ",
            "Pending auction created. Confirm within ",
            "Your UBS primary account cannot pay the ",
            "Previous failure: ",
            "Proposed action: ",
            "Rate limited. Try again in "
    };
    private static final String[] DELIMITERS = {
            "\n",
            "  ",
            " | ",
            " - ",
            " @ ",
            " / ",
            "s: ",
            ", start ",
            ", buyout ",
            ", duration ",
            ", fee ",
            ". Required: ",
            ", available: ",
            ". UBS says: ",
            "Autosave queued for ",
            "Saved ",
            "Config loaded. ",
            "Config reloaded. ",
            "Config loaded with one or more safe defaults. ",
            "Invalid config value for ",
            "; using safe default. ",
            "Persistent auction storage schema ",
            "Persistent auction storage loaded with ",
            " loaded with ",
            " auction(s); skipped ",
            " invalid record(s), repaired "
    };

    private UasTranslations() {
    }

    public static MutableComponent literal(String text) {
        return resolve(text == null ? "" : text, 0);
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key == null ? "" : key, args);
    }

    public static String plain(ServerPlayer player, String key) {
        return plain(clientLanguage(player), key);
    }

    public static String plain(String locale, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        return resolvePlain(normalizeLocale(locale), key, 0);
    }

    public static String formatPlain(ServerPlayer player, String key, Object... args) {
        return formatPlain(clientLanguage(player), key, args);
    }

    public static String formatPlain(String locale, String key, Object... args) {
        String translated = plain(locale, key);
        if (args == null || args.length == 0) {
            return translated;
        }
        String formatted = translated;
        for (int index = 0; index < args.length; index++) {
            formatted = formatted.replace("{" + index + "}", String.valueOf(args[index]));
        }
        return formatMinecraftPlaceholders(formatted, args);
    }

    private static String formatMinecraftPlaceholders(String text, Object... args) {
        Matcher matcher = MINECRAFT_PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();
        int sequentialIndex = 0;
        while (matcher.find()) {
            int argumentIndex = matcher.group(1) == null
                    ? sequentialIndex++
                    : Integer.parseInt(matcher.group(1)) - 1;
            String replacement = argumentIndex >= 0 && argumentIndex < args.length
                    ? String.valueOf(args[argumentIndex])
                    : matcher.group();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString().replace("%%", "%");
    }

    private static MutableComponent resolve(String text, int depth) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        if (depth > MAX_RESOLVE_DEPTH || isRuntimeValue(text)) {
            return Component.literal(text);
        }

        MutableComponent byLabel = splitLabelValue(text, depth);
        if (byLabel != null) {
            return byLabel;
        }

        MutableComponent byLeadingNumber = splitLeadingNumber(text, depth);
        if (byLeadingNumber != null) {
            return byLeadingNumber;
        }

        MutableComponent byLeadingPrefix = splitLeadingPrefix(text, depth);
        if (byLeadingPrefix != null) {
            return byLeadingPrefix;
        }

        for (String delimiter : DELIMITERS) {
            MutableComponent byDelimiter = splitByDelimiter(text, delimiter, depth);
            if (byDelimiter != null) {
                return byDelimiter;
            }
        }

        return Component.translatable(text);
    }

    private static String resolvePlain(String locale, String text, int depth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (depth > MAX_RESOLVE_DEPTH || isRuntimeValue(text)) {
            return text;
        }

        String direct = lookup(locale, text);
        if (!direct.equals(text)) {
            return direct;
        }

        String byLabel = splitLabelValuePlain(locale, text, depth);
        if (byLabel != null) {
            return byLabel;
        }

        String byLeadingNumber = splitLeadingNumberPlain(locale, text, depth);
        if (byLeadingNumber != null) {
            return byLeadingNumber;
        }

        String byLeadingPrefix = splitLeadingPrefixPlain(locale, text, depth);
        if (byLeadingPrefix != null) {
            return byLeadingPrefix;
        }

        String byTrailingSentence = splitTrailingSentencePlain(locale, text, depth);
        if (byTrailingSentence != null) {
            return byTrailingSentence;
        }

        for (String delimiter : DELIMITERS) {
            String byDelimiter = splitByDelimiterPlain(locale, text, delimiter, depth);
            if (byDelimiter != null) {
                return byDelimiter;
            }
        }

        return text;
    }

    private static MutableComponent splitLabelValue(String text, int depth) {
        int colon = text.indexOf(": ");
        if (colon <= 0 || colon >= text.length() - 2) {
            return null;
        }

        String prefix = text.substring(0, colon + 2);
        String value = text.substring(colon + 2);
        return Component.translatable(prefix).append(resolve(value, depth + 1));
    }

    private static String splitLabelValuePlain(String locale, String text, int depth) {
        int colon = text.indexOf(": ");
        if (colon <= 0 || colon >= text.length() - 2) {
            return null;
        }

        String prefix = text.substring(0, colon + 2);
        String value = text.substring(colon + 2);
        return lookup(locale, prefix) + resolvePlain(locale, value, depth + 1);
    }

    private static MutableComponent splitLeadingNumber(String text, int depth) {
        int end = 0;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (!Character.isDigit(c) && c != ',' && c != '.') {
                break;
            }
            end++;
        }
        if (end <= 0 || end >= text.length()) {
            return null;
        }

        char next = text.charAt(end);
        if (!Character.isWhitespace(next)) {
            return null;
        }
        return Component.literal(text.substring(0, end)).append(resolve(text.substring(end), depth + 1));
    }

    private static String splitLeadingNumberPlain(String locale, String text, int depth) {
        int end = 0;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (!Character.isDigit(c) && c != ',' && c != '.') {
                break;
            }
            end++;
        }
        if (end <= 0 || end >= text.length()) {
            return null;
        }

        char next = text.charAt(end);
        if (!Character.isWhitespace(next)) {
            return null;
        }
        return text.substring(0, end) + resolvePlain(locale, text.substring(end), depth + 1);
    }

    private static MutableComponent splitLeadingPrefix(String text, int depth) {
        for (String prefix : LEADING_PREFIX_KEYS) {
            if (text.startsWith(prefix) && text.length() > prefix.length()) {
                return Component.translatable(prefix).append(resolve(text.substring(prefix.length()), depth + 1));
            }
        }
        return null;
    }

    private static String splitLeadingPrefixPlain(String locale, String text, int depth) {
        for (String prefix : LEADING_PREFIX_KEYS) {
            if (text.startsWith(prefix) && text.length() > prefix.length()) {
                return lookup(locale, prefix) + resolvePlain(locale, text.substring(prefix.length()), depth + 1);
            }
        }
        return null;
    }

    private static String splitTrailingSentencePlain(String locale, String text, int depth) {
        if (!text.endsWith(".") || text.length() < 3) {
            return null;
        }
        String withoutPeriod = text.substring(0, text.length() - 1);
        if (isRuntimeValue(withoutPeriod)) {
            return withoutPeriod + lookup(locale, ".");
        }
        if (!withoutPeriod.equals(text) && withoutPeriod.length() < text.length()) {
            return resolvePlain(locale, withoutPeriod, depth + 1) + lookup(locale, ".");
        }
        return null;
    }

    private static MutableComponent splitByDelimiter(String text, String delimiter, int depth) {
        int first = text.indexOf(delimiter);
        if (first < 0) {
            return null;
        }

        MutableComponent result = Component.empty();
        int start = 0;
        while (first >= 0) {
            if (first > start) {
                result.append(resolve(text.substring(start, first), depth + 1));
            }
            result.append(Component.translatable(delimiter));
            start = first + delimiter.length();
            first = text.indexOf(delimiter, start);
        }
        if (start < text.length()) {
            result.append(resolve(text.substring(start), depth + 1));
        }
        return result;
    }

    private static String splitByDelimiterPlain(String locale, String text, String delimiter, int depth) {
        int first = text.indexOf(delimiter);
        if (first < 0) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        int start = 0;
        while (first >= 0) {
            if (first > start) {
                result.append(resolvePlain(locale, text.substring(start, first), depth + 1));
            }
            result.append(lookup(locale, delimiter));
            start = first + delimiter.length();
            first = text.indexOf(delimiter, start);
        }
        if (start < text.length()) {
            result.append(resolvePlain(locale, text.substring(start), depth + 1));
        }
        return result.toString();
    }

    private static boolean isRuntimeValue(String text) {
        String core = text.trim();
        if (core.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(core).matches()
                || NUMBER_PATTERN.matcher(core).matches()
                || ISO_DATE_TIME_PATTERN.matcher(core).matches()
                || RESOURCE_LOCATION_PATTERN.matcher(core).matches();
    }

    private static String lookup(String locale, String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String normalized = normalizeLocale(locale);
        Map<String, String> translations = loadPlainTranslations(normalized);
        String value = translations.get(key);
        if (value != null) {
            return value;
        }
        if (!FALLBACK_LOCALE.equals(normalized)) {
            value = loadPlainTranslations(FALLBACK_LOCALE).get(key);
            if (value != null) {
                return value;
            }
        }
        return key;
    }

    private static Map<String, String> loadPlainTranslations(String locale) {
        return PLAIN_TRANSLATIONS.computeIfAbsent(normalizeLocale(locale), UasTranslations::readLanguageFile);
    }

    private static Map<String, String> readLanguageFile(String locale) {
        String path = "assets/" + UltimateAuctionSystem.MODID + "/lang/" + locale + ".json";
        try (InputStream stream = UasTranslations.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) {
                    return Map.of();
                }
                Map<String, String> values = new LinkedHashMap<>();
                JsonObject object = root.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    JsonElement value = entry.getValue();
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                        values.put(entry.getKey(), value.getAsString());
                    }
                }
                return Map.copyOf(values);
            }
        } catch (RuntimeException | java.io.IOException exception) {
            UltimateAuctionSystem.LOGGER.warn("[UAS] Could not load language file {}", path, exception);
            return Map.of();
        }
    }

    private static String clientLanguage(ServerPlayer player) {
        if (player == null) {
            return FALLBACK_LOCALE;
        }
        try {
            Method clientInformation = player.getClass().getMethod("clientInformation");
            Object information = clientInformation.invoke(player);
            if (information == null) {
                return FALLBACK_LOCALE;
            }
            Method language = information.getClass().getMethod("language");
            Object value = language.invoke(information);
            if (value instanceof String languageCode && !languageCode.isBlank()) {
                return normalizeLocale(languageCode);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // NeoForge mappings can move this method between MC versions; English fallback is safe.
        }
        return FALLBACK_LOCALE;
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return FALLBACK_LOCALE;
        }
        String normalized = locale.trim().replace('-', '_').toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? FALLBACK_LOCALE : normalized;
    }
}
