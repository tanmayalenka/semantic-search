package com.example.contracts.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmbeddingService {

    /** Expected dims for nomic-embed-text. Guard against silent model swaps. */
    private static final int EXPECTED_DIMENSIONS = 768;

    /**
     * Bounded query cache. Search queries repeat often ("cloud", "cyber",
     * "janitorial") and embedding is the dominant latency cost — ~10-30ms
     * per call locally. Caching the last N query strings turns repeat
     * searches into pure vector-DB round trips.
     *
     * Deliberately NOT a Caffeine cache: we want a hard cap with no
     * eviction policy beyond "throw away when full", because eviction
     * cost would exceed the embed cost for a working set this small.
     * Swap in Caffeine if the working set grows past a few thousand.
     */
    private static final int QUERY_CACHE_MAX = 512;
    private final Map<String, float[]> queryCache = new ConcurrentHashMap<>(QUERY_CACHE_MAX);

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("EmbeddingService initialized with model: {}",
                embeddingModel.getClass().getSimpleName());
    }

    /**
     * Embed a single search query. Cached.
     */
    public float[] embedQuery(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }
        String key = text.strip();
        return queryCache.computeIfAbsent(key, this::doEmbed);
    }

    /**
     * Embed a batch of titles for ingestion. NOT cached — titles are
     * unique rows, caching them would just waste heap.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<float[]> vectors = embeddingModel.embed(texts);
        vectors.forEach(this::validateDimensions);
        return vectors;
    }

    private float[] doEmbed(String text) {
        if (queryCache.size() >= QUERY_CACHE_MAX) {
            // Cheap bulk eviction: clear when full. For a query cache this is
            // fine — the next N queries repopulate it in ~ms each.
            log.debug("Query cache full ({}), clearing", queryCache.size());
            queryCache.clear();
        }
        float[] vector = embeddingModel.embed(text);
        validateDimensions(vector);
        return vector;
    }

    private void validateDimensions(float[] vector) {
        if (vector.length != EXPECTED_DIMENSIONS) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: expected %d, got %d. "
                            + "Check spring.ai.ollama.embedding.model — a different model "
                            + "will produce a different dimension and break the pgvector index."
                            .formatted(EXPECTED_DIMENSIONS, vector.length));
        }
    }
}