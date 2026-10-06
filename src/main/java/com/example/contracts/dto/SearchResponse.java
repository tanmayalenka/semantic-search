package com.example.contracts.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SearchResponse(
        String query,
        int limit,
        double minScore,
        int returned,
        long tookMs,
        List<SearchResultItem> results
) {}