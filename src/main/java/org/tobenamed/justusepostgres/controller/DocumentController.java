package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.Document;
import org.tobenamed.justusepostgres.service.DocumentService;
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
 * REST API for document storage (replaces MongoDB CRUD API).
 *
 * <h3>JSONB operators used</h3>
 * <ul>
 *   <li>{@code @>}: "contains" — checks if JSONB contains a given sub-document</li>
 *   <li>{@code ||}: merge — applies a patch to an existing document ($set in MongoDB)</li>
 *   <li>{@code ->>}: extract as text — like MongoDB's dot notation (data->>'name')</li>
 *   <li>{@code #>>}: extract nested path — data #>> '{address,city}'</li>
 * </ul>
 *
 * The GIN index on the {@code data} column automatically indexes ALL keys and
 * values, so ANY query using {@code @>} is fast without defining per-field indexes.
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    /**
     * GET /api/documents/{collection}
     * Like MongoDB: db.collection.find()
     */
    @GetMapping("/{collection}")
    @Operation(summary = "List documents", description = "Like MongoDB db.collection.find(). Returns all documents in a collection.")
    public ResponseEntity<List<Document>> findAll(@Parameter(description = "Collection name", example = "users") @PathVariable String collection) {
        return ResponseEntity.ok(service.findAll(collection));
    }

    /**
     * POST /api/documents/{collection}/query
     * Body: { "address": { "city": "New York" } }
     * Like MongoDB: db.collection.find({ address: { city: "New York" } })
     */
    @PostMapping("/{collection}/query")
    @Operation(summary = "Query documents by filter", description = "Like MongoDB db.collection.find({filter}). Uses JSONB @> containment operator.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"address\": {\"city\": \"New York\"}}"))))
    public ResponseEntity<List<Document>> query(
            @Parameter(description = "Collection name", example = "users") @PathVariable String collection,
            @RequestBody String jsonFilter) {
        return ResponseEntity.ok(service.findByFilter(collection, jsonFilter));
    }

    /**
     * POST /api/documents/{collection}
     * Body: { "name": "Dave", "email": "dave@example.com" }
     * Like MongoDB: db.collection.insertOne({ ... })
     */
    @PostMapping("/{collection}")
    @Operation(summary = "Insert document", description = "Like MongoDB db.collection.insertOne(). Stores a JSON document.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"name\": \"Dave\", \"email\": \"dave@example.com\", \"age\": 28, \"address\": {\"city\": \"San Francisco\", \"zip\": \"94105\"}}"))))
    public ResponseEntity<Map<String, Long>> insert(
            @Parameter(description = "Collection name", example = "users") @PathVariable String collection,
            @RequestBody String jsonData) {
        Long id = service.insert(collection, jsonData);
        return ResponseEntity.ok(Map.of("id", id));
    }

    /**
     * PATCH /api/documents/{id}
     * Body: { "email": "newemail@example.com" }
     * Like MongoDB: db.collection.updateOne({ _id: id }, { $set: { ... } })
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Patch document", description = "Like MongoDB $set. Merges partial JSON update into existing document.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"age\": 31, \"email\": \"dave.updated@example.com\"}"))))
    public ResponseEntity<Map<String, Boolean>> update(
            @Parameter(description = "Document ID", example = "1") @PathVariable Long id,
            @RequestBody String jsonPatch) {
        return ResponseEntity.ok(Map.of("updated", service.update(id, jsonPatch)));
    }

    /**
     * DELETE /api/documents/byId/{id}
     * Like MongoDB: db.collection.deleteOne({ _id: id })
     */
    @DeleteMapping("/byId/{id}")
    @Operation(summary = "Delete document", description = "Like MongoDB db.collection.deleteOne(). Removes a document by ID.")
    public ResponseEntity<Map<String, Boolean>> delete(@Parameter(description = "Document ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(Map.of("deleted", service.delete(id)));
    }
}
