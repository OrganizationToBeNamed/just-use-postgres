package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.HybridSearchResult;
import org.tobenamed.justusepostgres.service.HybridSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for hybrid search: BM25 + vector similarity with RRF scoring.
 * Replaces a multi-service pipeline of Elasticsearch + Pinecone + app-side merging.
 *
 * <h3>Reciprocal Rank Fusion (RRF)</h3>
 * Merges keyword and vector rankings into a single score:
 * {@code rrf = 1/(60 + rank_keyword) + 1/(60 + rank_vector)}
 *
 * <h3>All in one query</h3>
 * Both BM25 and cosine similarity execute in a single SQL statement —
 * one network round trip, one transaction. No cross-service coordination.
 */
@RestController
@RequestMapping("/api/hybrid-search")
@Tag(name = "Hybrid Search")
public class HybridSearchController {

    private final HybridSearchService service;

    public HybridSearchController(HybridSearchService service) {
        this.service = service;
    }

    /**
     * POST /api/hybrid-search
     * Body: { "query": "vector database", "limit": 10 }
     * Hybrid search using both BM25 keyword and vector similarity + RRF fusion.
     * Demo uses random vectors; in production, pass embeddings from your model.
     */
    @PostMapping
    @Operation(summary = "Hybrid search (BM25 + vector)", description = "Combines keyword BM25 and vector cosine similarity using Reciprocal Rank Fusion in a single SQL query.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"query\": \"vector database performance\", \"limit\": 5}"))))
    public List<HybridSearchResult> hybridSearch(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int limit = ((Number) request.getOrDefault("limit", 10)).intValue();

        // In production, compute embedding from request; here we use demo vectors
        return service.hybridSearch(query, null, limit);
    }

    /**
     * GET /api/hybrid-search/keyword?q=vector+database&limit=10
     * Keyword-only search (BM25) for comparison with hybrid.
     */
    @GetMapping("/keyword")
    @Operation(summary = "Keyword-only search", description = "BM25 keyword search only, for comparison with hybrid results.")
    public List<HybridSearchResult> keywordOnly(
            @Parameter(description = "Search query", example = "vector database") @RequestParam String q,
            @Parameter(description = "Max results", example = "5") @RequestParam(defaultValue = "10") int limit) {
        return service.keywordOnly(q, limit);
    }
}
