package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.SensorReading;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Repository for time-series data using partitioned tables + BRIN indexes.
 *
 * <h3>How it replaces InfluxDB</h3>
 * <ul>
 *   <li>Declarative partitioning by time range = InfluxDB's time-structured merge tree</li>
 *   <li>BRIN index = Block Range INdex, extremely compact for ordered data
 *       (stores min/max per block, ~1000x smaller than B-tree)</li>
 *   <li>Partition pruning: queries on time ranges skip irrelevant partitions entirely</li>
 *   <li>Easy retention: DROP a partition (instant) vs DELETE rows (slow + vacuum)</li>
 * </ul>
 *
 * <h3>Aggregation queries</h3>
 * <ul>
 *   <li>time_bucket-style queries via {@code date_trunc()}</li>
 *   <li>Native SQL window functions replace InfluxQL/Flux</li>
 * </ul>
 */
@Repository
public class TimeSeriesRepository {

    private final JdbcTemplate jdbc;

    public TimeSeriesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert a sensor reading. Postgres routes it to the correct partition automatically. */
    public void insert(String sensorId, String metricName, double value, Instant recordedAt, String tags) {
        jdbc.update("""
            INSERT INTO sensor_readings (sensor_id, metric_name, value, recorded_at, tags)
            VALUES (?, ?, ?, ?, ?::jsonb)
            """,
            sensorId, metricName, value,
            java.sql.Timestamp.from(recordedAt),
            tags
        );
    }

    /** Query readings for a sensor in a time range. Partition pruning kicks in automatically. */
    public List<SensorReading> findByTimeRange(String sensorId, Instant from, Instant to, int limit) {
        return jdbc.query("""
            SELECT id, sensor_id, metric_name, value, recorded_at, tags::text
            FROM sensor_readings
            WHERE sensor_id = ?
              AND recorded_at BETWEEN ? AND ?
            ORDER BY recorded_at DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new SensorReading(
                rs.getLong("id"),
                rs.getString("sensor_id"),
                rs.getString("metric_name"),
                rs.getDouble("value"),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("tags")
            ),
            sensorId,
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to),
            limit
        );
    }

    /**
     * Aggregate readings into time buckets (like InfluxDB's GROUP BY time()).
     * Uses {@code date_trunc} which is equivalent to InfluxDB's time() function.
     *
     * @param interval one of: 'minute', 'hour', 'day', 'week', 'month'
     */
    public List<Map<String, Object>> aggregateByInterval(
            String sensorId, String metricName, String interval, Instant from, Instant to) {
        return jdbc.queryForList("""
            SELECT date_trunc(?, recorded_at) AS bucket,
                   AVG(value) AS avg_value,
                   MIN(value) AS min_value,
                   MAX(value) AS max_value,
                   COUNT(*) AS sample_count
            FROM sensor_readings
            WHERE sensor_id = ?
              AND metric_name = ?
              AND recorded_at BETWEEN ? AND ?
            GROUP BY bucket
            ORDER BY bucket
            """,
            interval,
            sensorId,
            metricName,
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(to)
        );
    }

    /** Get the latest reading per sensor (like InfluxDB's LAST()). */
    public List<SensorReading> getLatestPerSensor() {
        return jdbc.query("""
            SELECT DISTINCT ON (sensor_id)
                   id, sensor_id, metric_name, value, recorded_at, tags::text
            FROM sensor_readings
            ORDER BY sensor_id, recorded_at DESC
            """,
            (rs, rowNum) -> new SensorReading(
                rs.getLong("id"),
                rs.getString("sensor_id"),
                rs.getString("metric_name"),
                rs.getDouble("value"),
                rs.getTimestamp("recorded_at").toInstant(),
                rs.getString("tags")
            )
        );
    }
}
