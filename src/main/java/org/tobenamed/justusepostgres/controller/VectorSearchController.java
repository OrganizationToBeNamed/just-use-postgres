package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.VectorDocument;
import org.tobenamed.justusepostgres.service.VectorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for vector similarity search (replaces Pinecone/Qdrant API).
 *
 * <h3>Algorithm: HNSW (Hierarchical Navigable Small World)</h3>
 * Both pgvector and Pinecone use HNSW for approximate nearest neighbor search.
 * pgvectorscale adds DiskANN for even better performance:
 * <ul>
 *   <li>28x lower p95 latency</li>
 *   <li>75% lower cost</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/vectors")
@Tag(name = "Vector Search")
public class VectorSearchController {

    private final VectorSearchService service;

    public VectorSearchController(VectorSearchService service) {
        this.service = service;
    }

    /**
     * POST /api/vectors/search
     * Body: { "vector": [0.1, 0.2, ...], "limit": 5 }
     *
     * Equivalent to Pinecone's query() method.
     * If no vector is provided, a random one is generated for demo purposes.
     */
    @PostMapping("/search")
    @Operation(summary = "Vector similarity search", description = "Equivalent to Pinecone query(). Uses HNSW index for approximate nearest neighbor. Pass a vector or omit for random demo vector.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"limit\": 5}"))))
    public ResponseEntity<List<VectorDocument>> search(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Double> vectorList = (List<Double>) request.getOrDefault("vector", List.of());
        int limit = (int) request.getOrDefault("limit", 5);

        float[] queryVector;
        if (vectorList.isEmpty()) {
            // Generate random vector for demo
            queryVector = service.generateRandomEmbedding(384);
        } else {
            queryVector = new float[vectorList.size()];
            for (int i = 0; i < vectorList.size(); i++) {
                queryVector[i] = vectorList.get(i).floatValue();
            }
        }

        return ResponseEntity.ok(service.search(queryVector, limit));
    }

    /**
     * POST /api/vectors/documents
     * Body: { "title": "...", "content": "..." }
     *
     * Equivalent to Pinecone's upsert() method.
     * In production, embeddings would be generated from an LLM before storing.
     */
    @PostMapping("/documents")
    @Operation(summary = "Upsert vector document", description = "Equivalent to Pinecone upsert(). Stores document with auto-generated embedding.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"title\": \"AI Research Paper\", \"content\": \"Deep learning approaches to natural language processing and transformer architectures.\"}"))))
    public ResponseEntity<Map<String, Long>> addDocument(@RequestBody Map<String, String> request) {
        Long id = service.addDocument(request.get("title"), request.get("content"));
        return ResponseEntity.ok(Map.of("id", id));
    }
}
