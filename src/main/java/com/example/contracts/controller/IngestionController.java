package com.example.contracts.controller;

import com.example.contracts.ingestion.EmbeddingBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/admin/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final EmbeddingBackfillService backfillService;

    /**
     * Phase 1 only — load metadata, leave embeddings NULL.
     * Phase 2 — backfill embeddings. Call repeatedly if you want to
     * process in chunks (e.g. maxBatches=100 per call to avoid timeouts).
     */
    @PostMapping("/backfill-embeddings")
    public ResponseEntity<IngestionResult> backfillEmbeddings(
            @RequestParam(defaultValue = "64") int batchSize,
            @RequestParam(defaultValue = "0") int maxBatches) {

        long embedded = backfillService.backfill(batchSize, maxBatches);
        return ResponseEntity.ok()
                .body(new IngestionResult("embeddings", embedded, "Run again to continue"));
    }

    public record IngestionResult(String phase, long rowsProcessed, String note) {}
}