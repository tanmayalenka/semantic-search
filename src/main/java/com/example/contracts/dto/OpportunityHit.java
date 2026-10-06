package com.example.contracts.dto;

/**
 * A candidate row plus the score it received from whichever ranker
 * produced it. Deliberately not the public DTO — the score here is
 * ranker-specific and not directly comparable across rankers.
 */
public record OpportunityHit(
        String noticeId,
        String title,
        String solicitation,
        String department,
        String subTier,
        String office,
        String type,
        double score
) {}
