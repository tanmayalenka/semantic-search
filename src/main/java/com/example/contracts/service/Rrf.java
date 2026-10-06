package com.example.contracts.service;

import com.example.contracts.dto.OpportunityHit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion — combines multiple ranked lists into one,
 * using only rank position so incompatible score scales don't matter.
 *
 * Reference: Cormack, Clarke & Buettcher (2009), "Reciprocal Rank Fusion
 * Outperforms Condorcet and Individual Rank Learning Methods."
 */
final class Rrf {

    /** The damping constant from the original paper. */
    static final int K = 60;

    /** Theoretical max: rank 1 in both lists. Used to normalize to [0, 1]. */
    private static final double MAX_SCORE = 2.0 / (K + 1);

    private Rrf() {}

    record Fused(OpportunityHit hit, double score, boolean inVector, boolean inKeyword) {}

    /**
     * Fuse two ranked lists. Both inputs must be ordered best-first and
     * must not contain duplicate noticeIds — the repository queries
     * guarantee both, since noticeId is the primary key.
     *
     * @param vectorHits  results from the semantic ranker, or null
     * @param keywordHits results from the keyword ranker, or null
     * @param limit       max results to return after fusion
     */
    static List<Fused> fuse(List<OpportunityHit> vectorHits,
                            List<OpportunityHit> keywordHits,
                            int limit) {

        Map<String, OpportunityHit> byId = new HashMap<>();
        Map<String, Double> scores = new HashMap<>();

        if (vectorHits != null) {
            for (int i = 0; i < vectorHits.size(); i++) {
                OpportunityHit h = vectorHits.get(i);
                byId.put(h.noticeId(), h);
                scores.merge(h.noticeId(), 1.0 / (K + i + 1), Double::sum);
            }
        }
        if (keywordHits != null) {
            for (int i = 0; i < keywordHits.size(); i++) {
                OpportunityHit h = keywordHits.get(i);
                byId.putIfAbsent(h.noticeId(), h);
                scores.merge(h.noticeId(), 1.0 / (K + i + 1), Double::sum);
            }
        }

        // Normalize to [0, 1] by the theoretical max, so the UI can use
        // a single set of color thresholds regardless of mode.
        var fused = new ArrayList<Fused>(byId.size());
        for (var entry : scores.entrySet()) {
            String id = entry.getKey();
            double normalized = Math.min(1.0, entry.getValue() / MAX_SCORE);
            fused.add(new Fused(
                    byId.get(id),
                    normalized,
                    vectorHits != null && containsId(vectorHits, id),
                    keywordHits != null && containsId(keywordHits, id)));
        }

        fused.sort((a, b) -> Double.compare(b.score(), a.score()));
        return fused.size() > limit ? fused.subList(0, limit) : fused;
    }

    private static boolean containsId(List<OpportunityHit> hits, String id) {
        // Linear scan is fine: lists are capped at limit*2, and this runs
        // once per fused result. Swap for a Set if limit ever grows past ~500.
        for (var h : hits) if (h.noticeId().equals(id)) return true;
        return false;
    }
}