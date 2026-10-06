package com.example.contracts.embedding;

import com.example.contracts.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmbeddingService {

    private static final int EXPECTED_DIMENSIONS = 768;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        log.info("EmbeddingService initialized with model: {}",
                embeddingModel.getClass().getSimpleName());
    }

    /**
     * Embed a single search query. Cached by normalized query string.
     *
     * sync = true collapses a stampede: if 200 requests for the same new
     * query arrive at once, exactly one hits Ollama and the rest wait on
     * the result. Caffeine supports synchronized loading natively.
     */
    @Cacheable(cacheNames = CacheConfig.EMBEDDING_CACHE, key = "#text.strip()", sync = true)
    public float[] embedQuery(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }
        float[] vector = embeddingModel.embed(text.strip());
        validateDimensions(vector);
        return vector;
    }

    /**
     * Batch embed for ingestion. NOT cached — titles are unique rows.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        List<float[]> vectors = embeddingModel.embed(texts);
        vectors.forEach(this::validateDimensions);
        return vectors;
    }

    private void validateDimensions(float[] vector) {
        if (vector.length != EXPECTED_DIMENSIONS) {
            throw new IllegalStateException(
                    "Embedding dimension mismatch: expected %d, got %d."
                            .formatted(EXPECTED_DIMENSIONS, vector.length));
        }
    }
}