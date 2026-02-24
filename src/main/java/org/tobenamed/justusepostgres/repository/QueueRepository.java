package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.QueueMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for message queue operations using SKIP LOCKED.
 *
 * <h3>How it replaces Kafka / RabbitMQ / SQS</h3>
 * <ul>
 *   <li>{@code FOR UPDATE SKIP LOCKED} — workers grab different rows without blocking</li>
 *   <li>Visibility timeout — if a worker crashes, the row becomes available again</li>
 *   <li>Dead-letter support — messages that fail N times are marked 'failed'</li>
 *   <li>Exactly-once processing — dequeue + business logic in one transaction</li>
 * </ul>
 *
 * <h3>pgmq equivalent</h3>
 * The pgmq extension (by Tembo) wraps this pattern into a clean API:
 * {@code pgmq.send()}, {@code pgmq.read()}, {@code pgmq.delete()}.
 * This repository demonstrates the raw SQL pattern for portability.
 *
 * <h3>When to still use Kafka</h3>
 * <ul>
 *   <li>Millions of events/sec throughput</li>
 *   <li>Multi-consumer replay (event sourcing)</li>
 *   <li>Partitioned topic ordering guarantees</li>
 * </ul>
 */
@Repository
public class QueueRepository {

    private final JdbcTemplate jdbc;

    public QueueRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Enqueue a new message (like RabbitMQ basic_publish or SQS SendMessage). */
    public Long send(String queue, String jsonPayload) {
        return jdbc.queryForObject("""
            INSERT INTO message_queue (queue, payload, status)
            VALUES (?, ?::jsonb, 'pending')
            RETURNING id
            """,
            Long.class,
            queue, jsonPayload
        );
    }

    /**
     * Dequeue one message using SKIP LOCKED (like SQS ReceiveMessage).
     *
     * This atomically:
     * 1. Finds the oldest pending message in the queue
     * 2. Locks it so no other worker can grab it
     * 3. Marks it as 'processing'
     * 4. Returns it
     *
     * If the transaction rolls back, the message becomes 'pending' again (automatic retry).
     */
    public Optional<QueueMessage> receive(String queue) {
        List<QueueMessage> results = jdbc.query("""
            UPDATE message_queue
            SET status = 'processing',
                attempts = attempts + 1,
                processed_at = NOW()
            WHERE id = (
                SELECT id FROM message_queue
                WHERE queue = ? AND status = 'pending'
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING id, queue, payload::text, status, attempts, created_at, processed_at
            """,
            (rs, rowNum) -> new QueueMessage(
                rs.getLong("id"),
                rs.getString("queue"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("processed_at") != null
                    ? rs.getTimestamp("processed_at").toInstant() : null
            ),
            queue
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Mark a message as completed (like SQS DeleteMessage). */
    public boolean complete(Long messageId) {
        return jdbc.update("""
            UPDATE message_queue SET status = 'completed', processed_at = NOW()
            WHERE id = ? AND status = 'processing'
            """, messageId) > 0;
    }

    /** Mark a message as failed (dead-letter). */
    public boolean fail(Long messageId) {
        return jdbc.update("""
            UPDATE message_queue SET status = 'failed', processed_at = NOW()
            WHERE id = ? AND status = 'processing'
            """, messageId) > 0;
    }

    /** Requeue messages stuck in 'processing' for longer than the visibility timeout. */
    public int requeueStale(String queue, int timeoutSeconds) {
        return jdbc.update("""
            UPDATE message_queue
            SET status = 'pending', processed_at = NULL
            WHERE queue = ? AND status = 'processing'
              AND processed_at < NOW() - (? || ' seconds')::interval
            """,
            queue, timeoutSeconds
        );
    }

    /** Get queue depth by status. */
    public List<java.util.Map<String, Object>> stats(String queue) {
        return jdbc.queryForList("""
            SELECT status, COUNT(*) as count
            FROM message_queue
            WHERE queue = ?
            GROUP BY status
            ORDER BY status
            """, queue);
    }

    /** List messages in a queue with optional status filter. */
    public List<QueueMessage> list(String queue, String status, int limit) {
        if (status != null && !status.isBlank()) {
            return jdbc.query("""
                SELECT id, queue, payload::text, status, attempts, created_at, processed_at
                FROM message_queue
                WHERE queue = ? AND status = ?
                ORDER BY created_at
                LIMIT ?
                """,
                (rs, rowNum) -> new QueueMessage(
                    rs.getLong("id"),
                    rs.getString("queue"),
                    rs.getString("payload"),
                    rs.getString("status"),
                    rs.getInt("attempts"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("processed_at") != null
                        ? rs.getTimestamp("processed_at").toInstant() : null
                ),
                queue, status, limit
            );
        }
        return jdbc.query("""
            SELECT id, queue, payload::text, status, attempts, created_at, processed_at
            FROM message_queue
            WHERE queue = ?
            ORDER BY created_at
            LIMIT ?
            """,
            (rs, rowNum) -> new QueueMessage(
                rs.getLong("id"),
                rs.getString("queue"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("processed_at") != null
                    ? rs.getTimestamp("processed_at").toInstant() : null
            ),
            queue, limit
        );
    }
}
