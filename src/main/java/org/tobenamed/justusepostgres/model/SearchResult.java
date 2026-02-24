package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Full-text search result (replaces Elasticsearch search hits).
 *
 * {@code rank} is computed using ts_rank which implements BM25-style ranking —
 * the same algorithm Elasticsearch uses under the hood.
 * {@code headline} shows highlighted matching fragments (like ES highlighting).
 */
public record SearchResult(
    Long id,
    String title,
    String headline,
    double rank,
    Instant createdAt
) {}
