package net.austizz.ultimate_auction_system;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
}
