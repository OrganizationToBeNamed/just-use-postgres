package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Message from a Postgres-backed task queue (replaces Kafka/RabbitMQ/SQS).
 *
 * Uses the SKIP LOCKED pattern: multiple workers can dequeue concurrently
 * without blocking each other — the same concept as SQS visibility timeout.
 *
 * <h3>SKIP LOCKED pattern</h3>
 * {@code FOR UPDATE SKIP LOCKED} acquires a row-level lock and skips any rows
 * already locked by other transactions. This gives you:
 * <ul>
 *   <li>Exactly-once delivery within a transaction</li>
 *   <li>Automatic retry on rollback (the row becomes visible again)</li>
 *   <li>No separate broker — the queue IS your database</li>
 * </ul>
 */
public record QueueMessage(
    Long id,
    String queue,
    String payload,     // JSONB stored as String
    String status,      // 'pending', 'processing', 'completed', 'failed'
    int attempts,
    Instant createdAt,
    Instant processedAt
) {}
