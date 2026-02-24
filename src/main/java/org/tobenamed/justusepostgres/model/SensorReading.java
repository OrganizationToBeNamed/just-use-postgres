package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Time-series data point (replaces InfluxDB measurements).
 *
 * Stored in a partitioned table (by month) using the same time-partitioning
 * strategy that InfluxDB uses internally. Benefits:
 *   - Partition pruning: queries on a time range only scan relevant partitions
 *   - BRIN index: extremely compact (~1000x smaller than B-tree) for ordered data
 *   - Easy data lifecycle: DROP old partitions instead of DELETE (instant, no vacuum)
 */
public record SensorReading(
    Long id,
    String sensorId,
    String metricName,
    double value,
    Instant recordedAt,
    String tags    // JSONB as String
) {}
