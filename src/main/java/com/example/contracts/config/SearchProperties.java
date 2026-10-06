package com.example.contracts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        int defaultLimit,
        int maxLimit,
        double minScore
) {
    public SearchProperties {
        if (defaultLimit <= 0) defaultLimit = 20;
        if (maxLimit <= 0) maxLimit = 100;
    }

    public int clamp(int requested) {
        if (requested <= 0) return defaultLimit;
        return Math.min(requested, maxLimit);
    }
}