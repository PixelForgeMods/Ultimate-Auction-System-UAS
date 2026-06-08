package net.austizz.ultimate_auction_system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionDataExporterTest {
    @TempDir
    Path tempDir;

    @Test
    void exportSanitizesCustomNamesIntoTheExportDirectory() {
        AuctionDataExporter.ExportResult result = AuctionDataExporter.export(
                tempDir,
                List.of(),
                AuctionDataExporter.Format.CSV,
                "../bad:name"
        );

        assertTrue(result.success());
        assertTrue(result.path().startsWith(tempDir.resolve("uas_exports").toAbsolutePath().normalize()));
        assertTrue(result.path().getFileName().toString().endsWith(".csv"));
        assertFalse(result.path().getFileName().toString().contains(".."));
        assertTrue(Files.isRegularFile(result.path()));
    }

    @Test
    void csvExportWritesStableHeadersForEmptyHistory() throws IOException {
        AuctionDataExporter.ExportResult result = AuctionDataExporter.export(
                tempDir,
                List.of(),
                AuctionDataExporter.Format.CSV,
                "history"
        );

        String body = Files.readString(result.path());
        assertTrue(body.startsWith("auction_id,item_ids,item_names,title,description,state,starting_bid,current_bid,buyout_price"));
        assertTrue(body.contains("reserve_price,reserve_met"));
        assertEquals(0, result.auctionCount());
    }

    @Test
    void jsonExportWritesAnEmptyArrayForEmptyHistory() throws IOException {
        AuctionDataExporter.ExportResult result = AuctionDataExporter.export(
                tempDir,
                List.of(),
                AuctionDataExporter.Format.JSON,
                ""
        );

        assertTrue(result.success());
        assertEquals("[]", Files.readString(result.path()).trim());
        assertTrue(result.path().getFileName().toString().endsWith(".json"));
    }

    @Test
    void jsonRowsIncludeAuctionHistoryFieldsAndSettlementReferences() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID sellerAccountId = UUID.randomUUID();
        UUID bidderId = UUID.randomUUID();
        Map<String, String> row = new LinkedHashMap<>();
        row.put("auction_id", auctionId.toString());
        row.put("item_ids", "minecraft:diamond");
        row.put("item_names", "Export Diamond");
        row.put("title", "Export Diamond");
        row.put("description", "external analysis row");
        row.put("state", "ACTIVE");
        row.put("starting_bid", "10");
        row.put("current_bid", "12");
        row.put("buyout_price", "25");
        row.put("reserve_price", "20");
        row.put("reserve_met", "false");
        row.put("date_start", "2026-06-08T12:00");
        row.put("date_end", "2026-06-09T12:30");
        row.put("created_at", "2026-06-08T12:00");
        row.put("updated_at", "2026-06-08T12:01");
        row.put("seller_uuid", sellerId.toString());
        row.put("seller_account_uuid", sellerAccountId.toString());
        row.put("winner_uuid", bidderId.toString());
        row.put("bid_count", "1");
        row.put("accepted_bid_count", "1");
        row.put("settlement_references", "SETTLEMENT-REF | FEE-REF");
        row.put("financial_event_count", "1");

        String body = AuctionDataExporter.toJsonRows(List.of(row));
        assertTrue(body.contains("\"auction_id\": \"" + auctionId + "\""));
        assertTrue(body.contains("\"item_ids\": \"minecraft:diamond\""));
        assertTrue(body.contains("\"item_names\": \"Export Diamond\""));
        assertTrue(body.contains("\"description\": \"external analysis row\""));
        assertTrue(body.contains("\"state\": \"ACTIVE\""));
        assertTrue(body.contains("\"starting_bid\": \"10\""));
        assertTrue(body.contains("\"current_bid\": \"12\""));
        assertTrue(body.contains("\"buyout_price\": \"25\""));
        assertTrue(body.contains("\"reserve_price\": \"20\""));
        assertTrue(body.contains("\"reserve_met\": \"false\""));
        assertTrue(body.contains("\"date_start\": \"2026-06-08T12:00\""));
        assertTrue(body.contains("\"date_end\": \"2026-06-09T12:30\""));
        assertTrue(body.contains("\"seller_uuid\": \"" + sellerId + "\""));
        assertTrue(body.contains("\"seller_account_uuid\": \"" + sellerAccountId + "\""));
        assertTrue(body.contains("\"winner_uuid\": \"" + bidderId + "\""));
        assertTrue(body.contains("\"bid_count\": \"1\""));
        assertTrue(body.contains("\"accepted_bid_count\": \"1\""));
        assertTrue(body.contains("\"settlement_references\": \"SETTLEMENT-REF | FEE-REF\""));
        assertTrue(body.contains("\"financial_event_count\": \"1\""));
    }
}
