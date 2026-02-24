package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.SensorReading;
import org.tobenamed.justusepostgres.service.TimeSeriesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * REST API for time-series data (replaces InfluxDB HTTP API).
 *
 * <h3>Partition pruning explained</h3>
 * When you query {@code WHERE recorded_at BETWEEN '2025-03-01' AND '2025-03-31'},
 * Postgres ONLY scans the {@code sensor_readings_2025_03} partition.
 * All other monthly partitions are skipped entirely (check with EXPLAIN ANALYZE).
 *
 * <h3>BRIN index explained</h3>
 * Block Range INdex stores the min/max value for each block of pages.
 * For time-ordered data, each block naturally covers a contiguous time range,
 * making the index extremely effective and ~1000x smaller than B-tree.
 */
@RestController
@RequestMapping("/api/timeseries")
@Tag(name = "Time-Series")
public class TimeSeriesController {

    private final TimeSeriesService service;

    public TimeSeriesController(TimeSeriesService service) {
        this.service = service;
    }

    /**
     * POST /api/timeseries/record
     * Body: { "sensorId": "sensor-1", "metricName": "temperature", "value": 23.5, "tags": "{}" }
     * Like InfluxDB: INSERT temperature,sensor=sensor-1 value=23.5
     */
    @PostMapping("/record")
    @Operation(summary = "Record sensor reading", description = "Like InfluxDB INSERT. Writes a metric value with sensor ID and tags.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"sensorId\": \"sensor-1\", \"metricName\": \"temperature\", \"value\": 23.5, \"tags\": \"{\\\"location\\\": \\\"warehouse-A\\\"}\"}"))))
    public ResponseEntity<Map<String, String>> record(@RequestBody Map<String, Object> request) {
        service.record(
            (String) request.get("sensorId"),
            (String) request.get("metricName"),
            ((Number) request.get("value")).doubleValue(),
            request.getOrDefault("tags", "{}").toString()
        );
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }

    /**
     * GET /api/timeseries/query?sensorId=sensor-1&amp;hoursBack=24&amp;limit=100
     * Like InfluxQL: SELECT * FROM sensor_readings WHERE sensor_id='sensor-1' AND time > now()-24h
     */
    @GetMapping("/query")
    @Operation(summary = "Query sensor data", description = "Like InfluxQL SELECT. Retrieves readings within a time range using partition pruning.")
    public ResponseEntity<List<SensorReading>> query(
            @Parameter(description = "Sensor ID", example = "sensor-1") @RequestParam String sensorId,
            @Parameter(description = "Hours to look back", example = "24") @RequestParam(value = "hoursBack", defaultValue = "24") int hoursBack,
            @Parameter(description = "Max results", example = "20") @RequestParam(value = "limit", defaultValue = "100") int limit) {
        Instant from = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        Instant to = Instant.now();
        return ResponseEntity.ok(service.query(sensorId, from, to, limit));
    }

    /**
     * GET /api/timeseries/aggregate?sensorId=sensor-1&amp;metric=temperature&amp;interval=hour&amp;hoursBack=168
     * Like InfluxQL: SELECT MEAN(value) FROM temperature GROUP BY time(1h)
     */
    @GetMapping("/aggregate")
    @Operation(summary = "Aggregate time-series data", description = "Like InfluxQL GROUP BY time(). Computes AVG/MIN/MAX over time buckets.")
    public ResponseEntity<List<Map<String, Object>>> aggregate(
            @Parameter(description = "Sensor ID", example = "sensor-1") @RequestParam String sensorId,
            @Parameter(description = "Metric name", example = "temperature") @RequestParam String metric,
            @Parameter(description = "Time bucket interval", example = "hour") @RequestParam(value = "interval", defaultValue = "hour") String interval,
            @Parameter(description = "Hours to look back", example = "168") @RequestParam(value = "hoursBack", defaultValue = "168") int hoursBack) {
        Instant from = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        Instant to = Instant.now();
        return ResponseEntity.ok(service.aggregate(sensorId, metric, interval, from, to));
    }

    /**
     * GET /api/timeseries/latest
     * Like InfluxQL: SELECT LAST(value) FROM sensor_readings GROUP BY sensor_id
     */
    @GetMapping("/latest")
    @Operation(summary = "Latest reading per sensor", description = "Like InfluxQL LAST(). Returns the most recent value for each sensor.")
    public ResponseEntity<List<SensorReading>> latest() {
        return ResponseEntity.ok(service.latestPerSensor());
    }
}
