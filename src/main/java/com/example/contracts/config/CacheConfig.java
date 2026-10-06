package com.example.contracts.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String EMBEDDING_CACHE = "queryEmbeddings";
    public static final String SEARCH_CACHE    = "searchResults";

    @Bean
    public CacheManager cacheManager() {
        var embeddingCache = new CaffeineCache(EMBEDDING_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        // No expireAfterWrite: an embedding for a given string is
                        // immutable until the model changes. Eviction is by size
                        // only (W-TinyLFU keeps the hot queries resident).
                        .recordStats()
                        .build());

        var searchCache = new CaffeineCache(SEARCH_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        // Short TTL bounds staleness as new opportunities are
                        // ingested. 5 minutes is a deliberate choice: long enough
                        // to absorb a burst, short enough that a fresh ingest
                        // shows up while the user is still at their desk.
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()
                        .build());

        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(embeddingCache, searchCache));
        return manager;
    }
}
