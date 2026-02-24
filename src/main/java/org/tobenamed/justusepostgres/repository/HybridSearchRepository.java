package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.HybridSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Repository for hybrid search: BM25 + vector similarity with Reciprocal Rank Fusion.
 * Replaces a multi-service pipeline of Elasticsearch + Pinecone + app-side merging.
 *
 * <h3>How it works — single SQL query</h3>
 * <ol>
 *   <li>CTE 1 ({@code keyword_search}): BM25-ranked full-text search via tsvector</li>
 *   <li>CTE 2 ({@code vector_search}): cosine similarity via pgvector</li>
 *   <li>FULL OUTER JOIN + RRF scoring: {@code 1/(60+rank_kw) + 1/(60+rank_vec)}</li>
 * </ol>
 *
 * <h3>Reciprocal Rank Fusion (RRF)</h3>
 * RRF is the standard method to merge rankings from different signals:
 * <ul>
 *   <li>k=60 is the smoothing constant (from the original Cormack et al. paper)</li>
 *   <li>A document ranked #1 in both lists: score ≈ 0.0328</li>
 *   <li>A document ranked #1 in one list only: score ≈ 0.0164</li>
 *   <li>Studies show hybrid search outperforms either method alone by 5-15%</li>
 * </ul>
 *
 * <h3>Requirements</h3>
 * The {@code articles} table must have both:
 * <ul>
 *   <li>A {@code tsv} tsvector column (for keyword/BM25 search)</li>
 *   <li>An {@code embedding} vector column (for semantic similarity)</li>
 * </ul>
 */
@Repository
public class HybridSearchRepository {

    private final JdbcTemplate jdbc;
    private static final int RRF_K = 60; // standard RRF smoothing constant

    public HybridSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Hybrid search combining BM25 keyword search and vector similarity via RRF.
     *
     * @param query      natural language query for BM25 search
     * @param queryVector embedding vector for semantic similarity search
     * @param limit      max results to return
     * @return ranked results with RRF scores
     */
    public List<HybridSearchResult> hybridSearch(String query, float[] queryVector, int limit) {
        String vectorLiteral = toVectorLiteral(queryVector);

        return jdbc.query("""
            WITH keyword_search AS (
                SELECT id, title,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank(tsv, websearch_to_tsquery('english', ?)) DESC
                       ) AS rank_kw,
                       created_at
                FROM articles
                WHERE tsv @@ websearch_to_tsquery('english', ?)
                LIMIT 20
            ),
            vector_search AS (
                SELECT a.id, a.title,
                       ROW_NUMBER() OVER (
                           ORDER BY a.embedding <=> ?::vector
                       ) AS rank_vec,
                       a.created_at
                FROM articles a
                WHERE a.embedding IS NOT NULL
                ORDER BY a.embedding <=> ?::vector
                LIMIT 20
            )
            SELECT COALESCE(k.id, v.id) AS id,
                   COALESCE(k.title, v.title) AS title,
                   k.rank_kw::int AS keyword_rank,
                   v.rank_vec::int AS vector_rank,
                   COALESCE(1.0/(? + k.rank_kw), 0) + COALESCE(1.0/(? + v.rank_vec), 0) AS rrf_score,
                   COALESCE(k.created_at, v.created_at) AS created_at
            FROM keyword_search k
            FULL OUTER JOIN vector_search v ON k.id = v.id
            ORDER BY rrf_score DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new HybridSearchResult(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getObject("keyword_rank") != null ? rs.getInt("keyword_rank") : null,
                rs.getObject("vector_rank") != null ? rs.getInt("vector_rank") : null,
                rs.getDouble("rrf_score"),
                rs.getTimestamp("created_at").toInstant()
            ),
            query, query,
            vectorLiteral, vectorLiteral,
            RRF_K, RRF_K,
            limit
        );
    }

    /**
     * Keyword-only search (BM25) — useful for comparison with hybrid.
     */
    public List<HybridSearchResult> keywordOnly(String query, int limit) {
        return jdbc.query("""
            SELECT id, title,
                   ROW_NUMBER() OVER (ORDER BY ts_rank(tsv, websearch_to_tsquery('english', ?)) DESC)::int AS keyword_rank,
                   NULL::int AS vector_rank,
                   ts_rank(tsv, websearch_to_tsquery('english', ?))::double precision AS rrf_score,
                   created_at
            FROM articles
            WHERE tsv @@ websearch_to_tsquery('english', ?)
            ORDER BY rrf_score DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new HybridSearchResult(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getInt("keyword_rank"),
                null,
                rs.getDouble("rrf_score"),
                rs.getTimestamp("created_at").toInstant()
            ),
            query, query, query, limit
        );
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /** Helper to convert double[] to a string for pgvector. */
    private String toVectorLiteral(double[] vector) {
        return "[" + Arrays.stream(vector)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(",")) + "]";
    }
}
