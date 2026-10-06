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
        double score
) {}