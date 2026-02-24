package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.SensorReading;
import org.tobenamed.justusepostgres.repository.TimeSeriesRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class TimeSeriesService {

    private final TimeSeriesRepository repo;

    public TimeSeriesService(TimeSeriesRepository repo) {
        this.repo = repo;
    }

    public void record(String sensorId, String metricName, double value, String tags) {
        repo.insert(sensorId, metricName, value, Instant.now(), tags);
    }

    public List<SensorReading> query(String sensorId, Instant from, Instant to, int limit) {
        return repo.findByTimeRange(sensorId, from, to, limit);
    }

    public List<Map<String, Object>> aggregate(
            String sensorId, String metricName, String interval, Instant from, Instant to) {
        return repo.aggregateByInterval(sensorId, metricName, interval, from, to);
    }

    public List<SensorReading> latestPerSensor() {
        return repo.getLatestPerSensor();
    }
}
