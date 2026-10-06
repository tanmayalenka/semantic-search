package com.example.contracts.service;

import java.util.Locale;

public enum SearchMode {
    /** Semantic only. Best for paraphrase and fuzzy concept matching. */
    VECTOR,
    /** Keyword only. Best for exact terms, solicitation IDs, acronyms. Fastest. */
    KEYWORD,
    /** Both, fused with RRF. Best default. */
    HYBRID;

    public static SearchMode parse(String raw) {
        if (raw == null || raw.isBlank()) return HYBRID;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "mode must be one of: vector, keyword, hybrid");
        }
    }
}
