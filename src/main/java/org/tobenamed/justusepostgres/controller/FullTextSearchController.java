package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.SearchResult;
import org.tobenamed.justusepostgres.service.FullTextSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for full-text search (replaces Elasticsearch _search API).
 *
 * <h3>Algorithm: BM25</h3>
 * Both Postgres ts_rank and Elasticsearch use BM25 (Best Matching 25)
 * for relevance scoring. It considers:
 * <ul>
 *   <li>Term Frequency (TF): how often the word appears in the document</li>
 *   <li>Inverse Document Frequency (IDF): how rare the word is across all docs</li>
 *   <li>Document length normalization</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Full-Text Search")
public class FullTextSearchController {

    private final FullTextSearchService service;

    public FullTextSearchController(FullTextSearchService service) {
        this.service = service;
    }

    /**
     * GET /api/search?q=postgresql+features&amp;limit=10
     *
     * Supports natural language queries. Postgres's websearch_to_tsquery
     * converts natural language to boolean tsquery automatically.
     * Example queries:
     * <ul>
     *   <li>"postgresql features" — finds articles about PostgreSQL features</li>
     *   <li>"redis OR cache" — boolean OR</li>
     *   <li>"vector -mongodb" — exclude MongoDB results</li>
     * </ul>
     */
    @GetMapping
    @Operation(summary = "BM25 full-text search", description = "Equivalent to Elasticsearch _search. Uses websearch_to_tsquery for natural language queries.")
    public ResponseEntity<List<SearchResult>> search(
            @Parameter(description = "Search query (natural language)", example = "postgresql features") @RequestParam("q") String query,
            @Parameter(description = "Max results", example = "5") @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.search(query, limit));
    }

    /**
     * GET /api/search/fuzzy?q=postgre&amp;threshold=0.3&amp;limit=10
     *
     * Trigram-based fuzzy search — finds results even with typos.
     * threshold=0.3 means at least 30% trigram overlap is required.
     */
    @GetMapping("/fuzzy")
    @Operation(summary = "Trigram fuzzy search", description = "Finds results even with typos using pg_trgm trigram similarity.")
    public ResponseEntity<List<SearchResult>> fuzzySearch(
            @Parameter(description = "Search query (typo-tolerant)", example = "postgre") @RequestParam("q") String query,
            @Parameter(description = "Minimum trigram overlap (0.0–1.0)", example = "0.3") @RequestParam(value = "threshold", defaultValue = "0.3") double threshold,
            @Parameter(description = "Max results", example = "5") @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.fuzzySearch(query, threshold, limit));
    }

    @Operation(summary = "Add searchable article", description = "Insert a new article into the full-text search index.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"title\": \"Getting Started with pgvector\", \"body\": \"pgvector adds vector similarity search to PostgreSQL using HNSW indexes for fast approximate nearest neighbor queries.\"}"))))
    @PostMapping("/articles")
    public ResponseEntity<Map<String, Long>> addArticle(@RequestBody Map<String, String> request) {
        Long id = service.addArticle(request.get("title"), request.get("body"));
        return ResponseEntity.ok(Map.of("id", id));
    }
}
