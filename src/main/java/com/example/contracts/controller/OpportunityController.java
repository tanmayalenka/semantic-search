package com.example.contracts.controller;

import com.example.contracts.dto.SearchResponse;
import com.example.contracts.service.OpportunitySearchService;
import com.example.contracts.service.SearchMode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
public class OpportunityController {

    private final OpportunitySearchService searchService;

    /**
     * Semantic search over contract opportunity titles.
     *
     * Example:
     *   GET /api/opportunities/search?q=cloud+migration&limit=20&minScore=0.5
     */
    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(value = "mode", required = false) String mode,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "minScore", required = false) Double minScore) {

        return searchService.search(query, SearchMode.parse(mode), limit, minScore);
    }
}
