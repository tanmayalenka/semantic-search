package com.example.contracts.service;

import com.example.contracts.config.CacheConfig;
import com.example.contracts.config.SearchProperties;
import com.example.contracts.dto.OpportunityHit;
import com.example.contracts.embedding.EmbeddingService;
import com.example.contracts.embedding.VectorCodec;
import com.example.contracts.dto.SearchResponse;
import com.example.contracts.dto.SearchResultItem;
import com.example.contracts.repository.ContractOpportunityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunitySearchService {

    private static final int MAX_QUERY_LENGTH = 512;

    /**
     * In hybrid mode we over-fetch from each ranker so the fusion step
     * has enough candidates to reorder. 2x is the common sweet spot —
     * more is wasted work, less starves the merger.
     */
    private static final int HYBRID_OVERFETCH = 2;

    private final EmbeddingService embeddingService;
    private final ContractOpportunityRepository repository;
    private final SearchProperties searchProperties;

    @Cacheable(
            cacheNames = CacheConfig.SEARCH_CACHE,
            key = "#mode.name() + '|' + #rawQuery.strip() + '|' + (#requestedLimit ?: 0) + '|' + (#requestedMinScore ?: -1)",
            unless = "#result == null || #result.results().isEmpty()")
    public SearchResponse search(String rawQuery, SearchMode mode,
                                 Integer requestedLimit, Double requestedMinScore) {

        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        String query = rawQuery.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException(
                    "query exceeds maximum length of " + MAX_QUERY_LENGTH);
        }

        int limit = searchProperties.clamp(requestedLimit == null ? 0 : requestedLimit);
        long start = System.nanoTime();

        List<SearchResultItem> results = switch (mode) {
            case VECTOR  -> vectorOnly(query, limit, requestedMinScore);
            case KEYWORD -> keywordOnly(query, limit);
            case HYBRID  -> hybrid(query, limit);
        };

        long tookMs = (System.nanoTime() - start) / 1_000_000;

        log.debug("Search mode={} query='{}' limit={} -> {} results in {}ms",
                mode, query, limit, results.size(), tookMs);

        return SearchResponse.builder()
                .query(query)
                .mode(mode.name().toLowerCase())
                .limit(limit)
                .returned(results.size())
                .tookMs(tookMs)
                .results(results)
                .build();
    }

    // --- Mode 1: vector only ---

    private List<SearchResultItem> vectorOnly(String query, int limit, Double minScore) {
        float[] vector = embeddingService.embedQuery(query);
        var hits = repository.searchByVector(VectorCodec.toLiteral(vector), limit);

        double floor = minScore == null ? 0.0 : clampScore(minScore);
        var out = new ArrayList<SearchResultItem>(hits.size());
        for (var h : hits) {
            if (h.score() < floor) continue;
            out.add(toItem(h, h.score(), h.score(), null, "vector"));
        }
        return out;
    }

    // --- Mode 2: keyword only (no embedding call) ---

    private List<SearchResultItem> keywordOnly(String query, int limit) {
        var hits = repository.searchByKeyword(query, limit);

        // ts_rank is unbounded and model-specific. Min-max normalize within
        // the result set so the UI's score badge thresholds still apply.
        double max = hits.stream().mapToDouble(OpportunityHit::score).max().orElse(1.0);
        if (max <= 0) max = 1.0;

        var out = new ArrayList<SearchResultItem>(hits.size());
        for (var h : hits) {
            double normalized = h.score() / max;
            out.add(toItem(h, normalized, null, h.score(), "keyword"));
        }
        return out;
    }

    // --- Mode 3: hybrid ---

    private List<SearchResultItem> hybrid(String query, int limit) {
        int fetch = limit * HYBRID_OVERFETCH;

        float[] vector = embeddingService.embedQuery(query);
        var vectorHits = repository.searchByVector(VectorCodec.toLiteral(vector), fetch);
        var keywordHits = repository.searchByKeyword(query, fetch);

        var fused = Rrf.fuse(vectorHits, keywordHits, limit);

        // Index the raw scores by id so we can surface them alongside the
        // RRF score. Separate maps because the two score scales are different.
        Map<String, Double> vScores = indexScores(vectorHits);
        Map<String, Double> kScores = indexScores(keywordHits);

        var out = new ArrayList<SearchResultItem>(fused.size());
        for (var f : fused) {
            String id = f.hit().noticeId();
            out.add(toItem(
                    f.hit(),
                    f.score(),
                    vScores.get(id),
                    kScores.get(id),
                    f.inVector() && f.inKeyword() ? "both"
                            : f.inVector() ? "vector" : "keyword"));
        }
        return out;
    }

    // --- Helpers ---

    private static Map<String, Double> indexScores(List<OpportunityHit> hits) {
        var m = new HashMap<String, Double>(hits.size() * 2);
        for (var h : hits) m.put(h.noticeId(), h.score());
        return m;
    }

    private static SearchResultItem toItem(OpportunityHit h, double score,
                                           Double vectorScore, Double keywordScore,
                                           String matchedBy) {
        return SearchResultItem.builder()
                .noticeId(h.noticeId())
                .title(h.title())
                .solicitation(h.solicitation())
                .department(h.department())
                .subTier(h.subTier())
                .office(h.office())
                .type(h.type())
                .score(score)
                .vectorScore(vectorScore)
                .keywordScore(keywordScore)
                .matchedBy(matchedBy)
                .build();
    }

    /// @param s
    /// @return double
    private static double clampScore(double s) {
        if (Double.isNaN(s)) return 0.0;
        return Math.clamp(s, 0.0, 1.0);
    }
}