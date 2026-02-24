package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.CronJobDetail;
import org.tobenamed.justusepostgres.repository.CronJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for scheduled jobs inside Postgres (replaces external cron / Airflow).
 *
 * In production, pg_cron runs cron expressions natively inside the database:
 * <pre>
 * SELECT cron.schedule('cleanup', '0 * * * *',
 *   $$DELETE FROM cache_entries WHERE expires_at < NOW()$$);
 * </pre>
 *
 * This service demonstrates the same pattern at the application level, allowing
 * you to register, execute, and audit scheduled SQL jobs.
 */
@Service
public class CronJobService {

    private static final Logger log = LoggerFactory.getLogger(CronJobService.class);

    private final CronJobRepository repo;

    public CronJobService(CronJobRepository repo) {
        this.repo = repo;
    }

    public Long schedule(String jobName, String cronExpression, String sqlCommand) {
        log.info("Scheduling job '{}' with cron '{}'", jobName, cronExpression);
        return repo.schedule(jobName, cronExpression, sqlCommand);
    }

    public List<CronJobDetail> listJobs() {
        return repo.listJobs();
    }

    public String executeJob(Long jobId) {
        String result = repo.executeJob(jobId);
        log.info("Job {} executed: {}", jobId, result);
        return result;
    }

    public List<Map<String, Object>> history(int limit) {
        return repo.history(limit);
    }

    public boolean delete(Long jobId) {
        return repo.delete(jobId);
    }
}
