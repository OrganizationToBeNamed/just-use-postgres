package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Represents a document with a vector embedding (replaces Pinecone documents).
 *
 * The {@code embedding} field stores a float array that maps to Postgres {@code vector(384)}.
 * In production, you'd generate embeddings via an LLM (e.g., OpenAI, Hugging Face).
 */
public record VectorDocument(
    Long id,
    String title,
    String content,
    float[] embedding,
    double similarity,  // populated only in search results
    Instant createdAt
) {
    /** Constructor without similarity score (for inserts) */
    public VectorDocument(Long id, String title, String content, float[] embedding, Instant createdAt) {
        this(id, title, content, embedding, 0.0, createdAt);
    }
}
