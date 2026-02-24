package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.CacheEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Repository for key-value caching using UNLOGGED tables.
 *
 * <h3>How it replaces Redis</h3>
 * <ul>
 *   <li>UNLOGGED tables skip WAL — writes are ~2-5x faster than regular tables</li>
 *   <li>Data survives normal restarts but is LOST on crash — same as Redis</li>
 *   <li>Unlike Redis: supports SQL queries, JOINs, and JSONB operators on values</li>
 *   <li>TTL via {@code expires_at} column + scheduled cleanup (like Redis EXPIRE)</li>
 * </ul>
 *
 * <h3>When to still use Redis</h3>
 * <ul>
 *   <li>Sub-millisecond latency requirements (Postgres is ~1-5ms, Redis is ~0.1ms)</li>
 *   <li>Pub/Sub messaging (Postgres has LISTEN/NOTIFY but it's different)</li>
 *   <li>Lua scripting on cache operations</li>
 * </ul>
 */
@Repository
public class CacheRepository {

    private final JdbcTemplate jdbc;

    public CacheRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** GET — Retrieve a cache entry by key, respecting TTL. */
    public Optional<CacheEntry> get(String key) {
        List<CacheEntry> results = jdbc.query("""
            SELECT key, value::text, expires_at, created_at
            FROM cache_entries
            WHERE key = ?
              AND (expires_at IS NULL OR expires_at > NOW())
            """,
            (rs, rowNum) -> new CacheEntry(
                rs.getString("key"),
                rs.getString("value"),
                rs.getTimestamp("expires_at") != null
                    ? rs.getTimestamp("expires_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            ),
            key
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** SET — Upsert a cache entry with optional TTL. */
    public void set(String key, String jsonValue, Duration ttl) {
        long ttlSeconds = ttl != null ? ttl.getSeconds() : 0;
        jdbc.update("""
            INSERT INTO cache_entries (key, value, expires_at)
            VALUES (?, ?::jsonb, CASE WHEN ? > 0 THEN NOW() + (? || ' seconds')::interval ELSE NULL END)
            ON CONFLICT (key) DO UPDATE
            SET value = EXCLUDED.value,
                expires_at = EXCLUDED.expires_at,
                created_at = NOW()
            """,
            key, jsonValue, ttlSeconds, ttlSeconds
        );
    }

    /** DEL — Remove a cache entry. */
    public boolean delete(String key) {
        return jdbc.update("DELETE FROM cache_entries WHERE key = ?", key) > 0;
    }

    /** Cleanup expired entries (call this periodically, like Redis lazy expiration). */
    public int evictExpired() {
        return jdbc.update("DELETE FROM cache_entries WHERE expires_at < NOW()");
    }
}
