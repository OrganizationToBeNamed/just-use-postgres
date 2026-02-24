package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Combined keyword + vector search result using Reciprocal Rank Fusion
 * (replaces Elasticsearch + Pinecone multi-service pipeline).
 *
 * <h3>Reciprocal Rank Fusion (RRF)</h3>
 * RRF merges two ranked lists into one score:
 * <pre>
 *   rrf_score = 1/(k + rank_keyword) + 1/(k + rank_vector)
 * </pre>
 * where k=60 is the standard smoothing constant. A document ranked #1 in both
 * lists scores ~0.0328; a document in only one list scores ~0.0164.
 *
 * <h3>Why this matters</h3>
 * <ul>
 *   <li>Keyword search catches exact terms ("PostgreSQL 16")</li>
 *   <li>Vector search catches semantic meaning ("open-source relational DB")</li>
 *   <li>Together: 5-15% better relevance than either alone (per BEIR benchmarks)</li>
 *   <li>One SQL query instead of two services + app-side merge</li>
 * </ul>
 */
public record HybridSearchResult(
    Long id,
    String title,
    Integer keywordRank,
    Integer vectorRank,
    double rrfScore,
    Instant createdAt
) {}
