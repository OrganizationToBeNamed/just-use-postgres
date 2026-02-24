package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.service.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * REST API for caching (replaces Redis GET/SET/DEL commands).
 *
 * <h3>UNLOGGED tables internals</h3>
 * Normal Postgres tables write to the WAL (Write-Ahead Log) before applying
 * changes — this ensures durability but adds I/O overhead. UNLOGGED tables
 * skip this step entirely:
 * <ul>
 *   <li>INSERT/UPDATE are 2-5x faster</li>
 *   <li>Data survives normal Postgres restart (CHECKPOINT still flushes to disk)</li>
 *   <li>Data is LOST after a crash (same as Redis without AOF)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cache")
@Tag(name = "Cache")
public class CacheController {

    private final CacheService service;

    public CacheController(CacheService service) {
        this.service = service;
    }

    /**
     * GET /api/cache/{key}
     * Like Redis: GET key
     */
    @GetMapping("/{key}")
    @Operation(summary = "Get cached value", description = "Like Redis GET. Returns the value for a key, or 404 if expired/missing.")
    public ResponseEntity<?> get(@Parameter(description = "Cache key", example = "session:user-1") @PathVariable String key) {
        return service.get(key)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/cache/{key}
     * Body: { "value": { ... }, "ttlSeconds": 3600 }
     * Like Redis: SET key value EX 3600
     */
    @PutMapping("/{key}")
    @Operation(summary = "Set cached value", description = "Like Redis SET key value EX ttl. Stores a value with optional TTL.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"value\": {\"userId\": 1, \"role\": \"admin\", \"theme\": \"dark\"}, \"ttlSeconds\": 3600}"))))
    public ResponseEntity<Map<String, String>> set(
            @Parameter(description = "Cache key", example = "session:user-1") @PathVariable String key,
            @RequestBody Map<String, Object> request) {
        String value = request.get("value").toString();
        long ttlSeconds = ((Number) request.getOrDefault("ttlSeconds", 0)).longValue();
        Duration ttl = ttlSeconds > 0 ? Duration.ofSeconds(ttlSeconds) : null;

        service.set(key, value, ttl);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    /**
     * DELETE /api/cache/{key}
     * Like Redis: DEL key
     */
    @DeleteMapping("/{key}")
    @Operation(summary = "Delete cached value", description = "Like Redis DEL. Removes a key from the cache.")
    public ResponseEntity<Map<String, Boolean>> delete(@Parameter(description = "Cache key", example = "session:user-1") @PathVariable String key) {
        boolean deleted = service.delete(key);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
