package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Schemaless document stored as JSONB (replaces MongoDB documents).
 *
 * Key advantages over MongoDB:
 *   - JSONB is stored in decomposed binary format — faster than BSON for reads
 *   - GIN index covers ALL keys/values — no need to define indexes per field
 *   - Can JOIN documents with relational tables (impossible in MongoDB)
 *   - Full ACID transactions across documents
 *
 * The {@code collection} field mimics MongoDB's collection concept.
 */
public record Document(
    Long id,
    String collection,
    String data,     // JSONB as String
    Instant createdAt,
    Instant updatedAt
) {}
