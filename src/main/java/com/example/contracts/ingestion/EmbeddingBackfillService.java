package com.example.contracts.ingestion;

import com.example.contracts.config.CacheConfig;
import com.example.contracts.embedding.EmbeddingService;
import com.example.contracts.embedding.VectorCodec;
import com.example.contracts.repository.ContractOpportunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingBackfillService {

    private final ContractOpportunityRepository repository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Phase 2: backfill title_embedding for all rows where it is NULL.
     *
     * Resumable: safe to kill and re-run. Progress is durable because each
     * batch is committed before the next begins.
     *
     * @param batchSize      how many titles to embed per Ollama request
     * @param maxBatches     stop after this many batches (0 = unlimited, for testing)
     * @return total rows embedded in this run
     */
    @CacheEvict(cacheNames = CacheConfig.SEARCH_CACHE, allEntries = true)
    public long backfill(int batchSize, int maxBatches) {
        log.info("Phase 2: backfilling embeddings, batchSize={}", batchSize);

        AtomicLong totalEmbedded = new AtomicLong(0);
        int batchCount = 0;

        // Prepared UPDATE — reused across every batch via JdbcTemplate batchUpdate.
        String updateSql = """
                UPDATE contract_opportunities
                SET title_embedding = CAST(? AS vector),
                    updated_at = now()
                WHERE notice_id = ?
                """;

        while (maxBatches == 0 || batchCount < maxBatches) {
            // Fetch the next batch of un-embedded rows.
            // findMissingEmbeddings uses the partial index — fast even at 2M rows.
            List<Object[]> batch = repository.findMissingEmbeddingRows(batchSize);

            if (batch.isEmpty()) {
                log.info("Phase 2 complete: no more rows need embeddings");
                break;
            }

            // Extract noticeIds and titles from the Object[] rows.
            List<String> noticeIds = batch.stream().map(r -> (String) r[0]).toList();
            List<String> titles    = batch.stream().map(r -> (String) r[1]).toList();

            long start = System.currentTimeMillis();

            // One Ollama call for the whole batch. Spring AI returns positionally
            // aligned vectors, same order as the input list.
            List<float[]> vectors = embeddingService.embedBatch(titles);

            // Convert float[] -> pgvector literal strings, paired with noticeId.
            List<Object[]> updateArgs = new java.util.ArrayList<>(batchSize);
            for (int i = 0; i < noticeIds.size(); i++) {
                updateArgs.add(new Object[]{
                        VectorCodec.toLiteral(vectors.get(i)),
                        noticeIds.get(i)
                });
            }

            // Execute the batch as one JDBC round trip.
            // This is where rewriteBatchedStatements matters — see the pom note.
            int[] affected = jdbcTemplate.batchUpdate(updateSql, updateArgs);

            long elapsed = System.currentTimeMillis() - start;
            long done = totalEmbedded.addAndGet(batch.size());
            batchCount++;

            log.info("Batch {}: {} rows in {}ms ({}/s) — total: {}",
                    batchCount, batch.size(), elapsed,
                    String.format("%.1f", batch.size() * 1000.0 / elapsed), done);
        }

        return totalEmbedded.get();
    }
}