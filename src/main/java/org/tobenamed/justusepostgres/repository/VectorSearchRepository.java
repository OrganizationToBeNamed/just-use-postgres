package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.VectorDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for vector similarity search using pgvector.
 *
 * <h3>How it works</h3>
 * pgvector adds the {@code vector} type and three distance operators:
 * <ul>
 *   <li>{@code <->} : L2 (Euclidean) distance</li>
 *   <li>{@code <#>} : negative inner product</li>
 *   <li>{@code <=>} : cosine distance (1 - cosine_similarity)</li>
 * </ul>
 *
 * The HNSW index makes this an Approximate Nearest Neighbor (ANN) search,
 * the SAME algorithm that Pinecone, Qdrant, and Weaviate use internally.
 */
@Repository
public class VectorSearchRepository {

    private final JdbcTemplate jdbc;

    public VectorSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Find the K most similar documents using cosine similarity.
     * {@code <=>} is the cosine distance operator; we compute similarity as (1 - distance).
     */
    public List<VectorDocument> findSimilar(float[] queryVector, int limit) {
        String vectorLiteral = toVectorLiteral(queryVector);

        return jdbc.query("""
            SELECT id, title, content,
                   1 - (embedding <=> ?::vector) AS similarity,
                   created_at
            FROM vector_documents
            WHERE embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """,
            (rs, rowNum) -> new VectorDocument(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                null,  // don't return the full vector in results
                rs.getDouble("similarity"),
                rs.getTimestamp("created_at").toInstant()
            ),
            vectorLiteral, vectorLiteral, limit
        );
    }

    /** Insert a document with its embedding vector. */
    public Long insert(String title, String content, float[] embedding) {
        return jdbc.queryForObject("""
            INSERT INTO vector_documents (title, content, embedding)
            VALUES (?, ?, ?::vector)
            RETURNING id
            """,
            Long.class,
            title, content, toVectorLiteral(embedding)
        );
    }

    /** Convert float[] to Postgres vector literal: '[0.1,0.2,0.3]' */
    private String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
