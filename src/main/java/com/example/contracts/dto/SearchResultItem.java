package com.example.contracts.dto;

import lombok.Builder;

@Builder
public record SearchResultItem(
        String noticeId,
        String title,
        String solicitation,
        String department,
        String subTier,
        String office,
        String type,
        /** Primary score for the mode used. Meaning varies by mode — see SearchMode. */
        double score,

        /** Cosine similarity, present only when the vector ranker matched this row. */
        Double vectorScore,

        /** ts_rank, present only when the keyword ranker matched this row. */
        Double keywordScore,

        /** Which rankers contributed: "vector", "keyword", or "both". */
        String matchedBy
) {}