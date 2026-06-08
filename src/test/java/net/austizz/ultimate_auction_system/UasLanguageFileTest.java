package net.austizz.ultimate_auction_system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.austizz.ultimate_auction_system.i18n.UasTranslations;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UasLanguageFileTest {
    private static final Path LANG_DIR = Path.of(
            "src",
            "main",
            "resources",
            "assets",
            UltimateAuctionSystem.MODID,
            "lang"
    );
    private static final List<String> SUPPORTED_LANGUAGES = List.of(
            "en_us.json",
            "nl_nl.json",
            "de_de.json",
            "fr_fr.json"
    );
    private static final List<String> CRITICAL_USER_FACING_KEYS = List.of(
            "UAS ADMIN DASHBOARD",
            "Auction {0}: You won {1} for {2}. Claim is available.",
            "Bundle - {0} stacks / {1} items",
            "Select Time",
            "Sunday initial",
            "Account refreshed. Confirm to submit.",
            "No UBS account",
            "Failed Settlements",
            "{0}h {1}m left",
            "Claimable",
            "History",
            "Contents",
            "CLAIMED",
            "{0} Primary Account: {1}",
            "Player banned",
            "Rate limited. Try again in ",
            " seconds."
    );
    private static final List<String> CRITICAL_TRANSLATED_AWAY_KEYS = List.of(
            "UAS ADMIN DASHBOARD",
            "Auction {0}: You won {1} for {2}. Claim is available.",
            "Bundle - {0} stacks / {1} items",
            "Select Time",
            "Account refreshed. Confirm to submit.",
            "No UBS account",
            "Failed Settlements",
            "{0}h {1}m left",
            "Claimable",
            "History",
            "Contents",
            "CLAIMED",
            "{0} Primary Account: {1}",
            "Player banned",
            "Rate limited. Try again in ",
            " seconds."
    );

    @Test
    void supportedLanguageFilesExistAndShareKeys() throws IOException {
        Map<String, JsonObject> languageFiles = new LinkedHashMap<>();
        for (String fileName : SUPPORTED_LANGUAGES) {
            Path file = LANG_DIR.resolve(fileName);
            assertTrue(Files.isRegularFile(file), fileName + " is missing");

            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            assertTrue(root.isJsonObject(), fileName + " must contain a JSON object");

            JsonObject translations = root.getAsJsonObject();
            assertFalse(translations.keySet().isEmpty(), fileName + " must not be empty");
            translations.entrySet().forEach(entry -> assertTrue(
                    entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString(),
                    fileName + " value for " + entry.getKey() + " must be a string"
            ));
            languageFiles.put(fileName, translations);
        }

        TreeSet<String> englishKeys = new TreeSet<>(languageFiles.get("en_us.json").keySet());
        languageFiles.forEach((fileName, translations) -> assertEquals(
                englishKeys,
                new TreeSet<>(translations.keySet()),
                fileName + " must have the same keys as en_us.json"
        ));
    }

    @Test
    void criticalUserFacingKeysAreTranslatedInEverySupportedLanguage() throws IOException {
        JsonObject english = readLanguageFile("en_us.json");
        for (String key : CRITICAL_USER_FACING_KEYS) {
            assertTrue(english.has(key), "en_us.json is missing " + key);
        }

        for (String fileName : SUPPORTED_LANGUAGES.subList(1, SUPPORTED_LANGUAGES.size())) {
            JsonObject translations = readLanguageFile(fileName);
            for (String key : CRITICAL_USER_FACING_KEYS) {
                assertTrue(translations.has(key), fileName + " is missing " + key);
                assertFalse(
                        translations.get(key).getAsString().isBlank(),
                        fileName + " translation for " + key + " must not be blank"
                );
            }
            for (String key : CRITICAL_TRANSLATED_AWAY_KEYS) {
                assertFalse(
                        english.get(key).getAsString().equals(translations.get(key).getAsString()),
                        fileName + " translation for " + key + " must not fall back to English"
                );
            }
        }
    }

    @Test
    void plainTranslationsFormatRuntimeValues() {
        String wonAuction = UasTranslations.formatPlain(
                "nl_nl",
                "Auction {0}: You won {1} for {2}. Claim is available.",
                "abc123",
                "Diamond Sword",
                "$5"
        );

        assertTrue(wonAuction.contains("abc123"));
        assertTrue(wonAuction.contains("Diamond Sword"));
        assertTrue(wonAuction.contains("$5"));
        assertFalse(wonAuction.equals("Auction abc123: You won Diamond Sword for $5. Claim is available."));

        assertEquals("2Std 30Min uebrig", UasTranslations.formatPlain("de_de", "{0}h {1}m left", 2, 30));
        assertEquals("2h 30m restantes", UasTranslations.formatPlain("fr_fr", "{0}h {1}m left", 2, 30));
        assertEquals("Compte principal de Dev : $10", UasTranslations.formatPlain("fr_fr", "{0} Primary Account: {1}", "Dev", "$10"));
        assertEquals("Te snel. Probeer opnieuw over 2 seconden.", UasTranslations.formatPlain("nl_nl", "Rate limited. Try again in 2 seconds."));
    }

    @Test
    void translationValuesUseMinecraftPlaceholders() throws IOException {
        for (String fileName : SUPPORTED_LANGUAGES) {
            JsonObject translations = readLanguageFile(fileName);
            translations.entrySet().forEach(entry -> assertFalse(
                    entry.getValue().getAsString().matches(".*\\{\\d+}.*"),
                    fileName + " value for " + entry.getKey() + " must use Minecraft %s placeholders"
            ));
        }
    }

    private static JsonObject readLanguageFile(String fileName) throws IOException {
        Path file = LANG_DIR.resolve(fileName);
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        assertTrue(root.isJsonObject(), fileName + " must contain a JSON object");
        return root.getAsJsonObject();
    }
}
