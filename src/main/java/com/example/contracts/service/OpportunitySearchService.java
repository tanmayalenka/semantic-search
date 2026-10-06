package com.example.contracts.service;

import com.example.contracts.config.SearchProperties;
import com.example.contracts.embedding.EmbeddingService;
import com.example.contracts.embedding.VectorCodec;
import com.example.contracts.dto.SearchResponse;
import com.example.contracts.dto.SearchResultItem;
import com.example.contracts.repository.ContractOpportunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunitySearchService {

    private static final int MAX_QUERY_LENGTH = 512;

    private final EmbeddingService embeddingService;
    private final ContractOpportunityRepository repository;
    private final SearchProperties searchProperties;

    public SearchResponse search(String rawQuery, Integer requestedLimit, Double requestedMinScore) {
        // --- 1. Validate ---
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        String query = rawQuery.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query exceeds maximum length of " + MAX_QUERY_LENGTH);
        }

        // --- 2. Resolve parameters, clamping to configured bounds ---
        int limit = searchProperties.clamp(requestedLimit == null ? 0 : requestedLimit);
        double minScore = requestedMinScore == null
                ? searchProperties.minScore()
                : clampScore(requestedMinScore);

        // --- 3. Embed the query ---
        long start = System.nanoTime();
        float[] vector = embeddingService.embedQuery(query);
        String vectorLiteral = VectorCodec.toLiteral(vector);

        // --- 4. Query the vector index ---
        List<SearchResultItem> results =
                repository.searchByTitleEmbedding(vectorLiteral, minScore, limit);

        long tookMs = (System.nanoTime() - start) / 1_000_000;

        log.debug("Search query='{}' limit={} minScore={} -> {} results in {}ms",
                query, limit, minScore, results.size(), tookMs);

        return SearchResponse.builder()
                .query(query)
                .limit(limit)
                .minScore(minScore)
                .returned(results.size())
                .tookMs(tookMs)
                .results(results)
                .build();
    }

    private static double clampScore(double score) {
        // Cosine similarity is bounded [-1, 1]; for text embeddings it's
        // effectively [0, 1]. Clamp defensively rather than reject, so a
        // client passing -0.5 or 2.0 still gets a sensible response.
        if (Double.isNaN(score)) return 0.0;
        return Math.max(0.0, Math.min(1.0, score));
    }
}
