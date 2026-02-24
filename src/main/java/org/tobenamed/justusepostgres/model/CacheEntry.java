package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Cache entry stored in an UNLOGGED table (replaces Redis).
 *
 * UNLOGGED tables skip the Write-Ahead Log (WAL), giving us:
 *   - ~2-5x faster writes than regular tables
 *   - Same durability as Redis (data lost on crash, survives restart)
 *   - SQL query capability that Redis doesn't have
 *
 * TTL is implemented via {@code expires_at} + periodic cleanup (like Redis EXPIRE).
 */
public record CacheEntry(
    String key,
    String value,    // JSONB stored as String
    Instant expiresAt,
    Instant createdAt
) {}
