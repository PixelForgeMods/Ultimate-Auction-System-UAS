package net.austizz.ultimate_auction_system.i18n;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.regex.Pattern;

/**
 * Lightweight UAS localization helper.
 *
 * <p>Like UBS, UAS uses the original English text as the translation key.
 * Missing keys naturally render as the English fallback text.</p>
 */
public final class UasTranslations {
    private static final int MAX_RESOLVE_DEPTH = 6;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?\\$?\\d[\\d,]*(?:\\.\\d+)?%?$");
    private static final String[] DELIMITERS = {
            "\n",
            " | ",
            " - ",
            " / ",
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

        for (String delimiter : DELIMITERS) {
            MutableComponent byDelimiter = splitByDelimiter(text, delimiter, depth);
            if (byDelimiter != null) {
                return byDelimiter;
            }
        }

        return Component.translatable(text);
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

    private static boolean isRuntimeValue(String text) {
        String core = text.trim();
        if (core.isEmpty()) {
            return false;
        }
        return UUID_PATTERN.matcher(core).matches() || NUMBER_PATTERN.matcher(core).matches();
    }
}
