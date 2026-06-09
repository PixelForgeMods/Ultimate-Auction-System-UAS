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
            "Auction Teller",
            "Auction Teller placed.",
            "Auction Teller removed and spawn egg returned.",
            "Auction tellers are disabled on this server.",
            "entity.ultimate_auction_system.auction_teller",
            "item.ultimate_auction_system.auction_teller_spawn_egg",
            "Not enough space to place an Auction Teller here.",
            "Only the owner or an operator can remove this auction teller.",
            "Warning: shift-right-click again within 5 seconds to remove this auction teller.",
            "You do not have permission to use auction tellers.",
            "{0} Primary Account: {1}",
            "{0} - {1}",
            "Help: Bundle Title",
            "Help: Bundle Title Body",
            "Help: Starting Bid",
            "Help: Starting Bid Body",
            "Help: Buyout",
            "Help: Buyout Body",
            "Help: Reserve",
            "Help: Reserve Body",
            "Help: Auction Format",
            "Help: Auction Format Body",
            "Help: End Date",
            "Help: End Date Body",
            "Help: Description",
            "Help: Description Body",
            "Help: Listing Fee",
            "Help: Listing Fee Body",
            "Player banned",
            "Rate limited. Try again in ",
            " seconds.",
            "Auction {0}: {1} payout: gross {2}, tax {3}, fees {4}, net {5}.",
            "Auction {0} sales tax transfer failed: {1}",
            "Force Cancel Auction",
            "Admin Recovery Storage",
            "Recovery",
            "Auction force-cancelled, bidder refunded, and item moved to admin recovery.",
            "=== UAS Economy Report: {0} ===",
            "Economy Report: {0}",
            "Top sellers",
            "Top categories",
            "Top items",
            "Gross volume",
            "Failed settlements",
            "Reserve (dollars)",
            "Optional hidden reserve",
            "Reserve: {0}",
            "Reserve: {0} ({1})",
            "Met",
            "Not met",
            "Reserve Not Met",
            "Reserve-price auctions are disabled on this server.",
            "Reserve price must be at least the starting bid.",
            "Buyout price must be at least the reserve price.",
            "Auction {0}: Your bid of {2} on {1} was refunded because reserve was not met.",
            "Auction Format",
            "Sealed Bid",
            "Sealed Bids: On",
            "Place Sealed Bid",
            "Raise Sealed Bid",
            "Sealed bids are hidden until this auction ends.",
            "Your sealed bid: {0}",
            "Sealed-bid auctions are disabled on this server.",
            "Auction {0}: Your sealed bid on {1} is {2}.",
            "Auction {0}: A sealed bid was placed on {1}. Amount hidden until close.",
            "Auction {0}: Your sealed bid on {1} was refunded because reserve was not met."
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
            "Auction Teller",
            "Auction Teller placed.",
            "Auction Teller removed and spawn egg returned.",
            "Auction tellers are disabled on this server.",
            "entity.ultimate_auction_system.auction_teller",
            "item.ultimate_auction_system.auction_teller_spawn_egg",
            "Not enough space to place an Auction Teller here.",
            "Only the owner or an operator can remove this auction teller.",
            "Warning: shift-right-click again within 5 seconds to remove this auction teller.",
            "You do not have permission to use auction tellers.",
            "{0} Primary Account: {1}",
            "Help: Bundle Title",
            "Help: Bundle Title Body",
            "Help: Starting Bid",
            "Help: Starting Bid Body",
            "Help: Buyout",
            "Help: Buyout Body",
            "Help: Reserve",
            "Help: Reserve Body",
            "Help: Auction Format",
            "Help: Auction Format Body",
            "Help: End Date",
            "Help: End Date Body",
            "Help: Description Body",
            "Help: Listing Fee",
            "Help: Listing Fee Body",
            "Player banned",
            "Rate limited. Try again in ",
            " seconds.",
            "Auction {0}: {1} payout: gross {2}, tax {3}, fees {4}, net {5}.",
            "Auction {0} sales tax transfer failed: {1}",
            "Force Cancel Auction",
            "Admin Recovery Storage",
            "Recovery",
            "Auction force-cancelled, bidder refunded, and item moved to admin recovery.",
            "=== UAS Economy Report: {0} ===",
            "Economy Report: {0}",
            "Top sellers",
            "Top categories",
            "Top items",
            "Gross volume",
            "Failed settlements",
            "Reserve (dollars)",
            "Optional hidden reserve",
            "Reserve: {0}",
            "Reserve: {0} ({1})",
            "Met",
            "Not met",
            "Reserve Not Met",
            "Reserve-price auctions are disabled on this server.",
            "Reserve price must be at least the starting bid.",
            "Buyout price must be at least the reserve price.",
            "Auction {0}: Your bid of {2} on {1} was refunded because reserve was not met.",
            "Auction Format",
            "Sealed Bid",
            "Sealed Bids: On",
            "Place Sealed Bid",
            "Raise Sealed Bid",
            "Sealed bids are hidden until this auction ends.",
            "Your sealed bid: {0}",
            "Sealed-bid auctions are disabled on this server.",
            "Auction {0}: Your sealed bid on {1} is {2}.",
            "Auction {0}: A sealed bid was placed on {1}. Amount hidden until close.",
            "Auction {0}: Your sealed bid on {1} was refunded because reserve was not met."
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
        assertTrue(UasTranslations.formatPlain("fr_fr", "Auction {0}: {1} payout: gross {2}, tax {3}, fees {4}, net {5}.", "abc", "Sword", "$10", "$1", "$0", "$9").contains("frais $0"));
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
