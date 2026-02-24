package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.CronJobDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for scheduled job management (replaces external cron / Airflow).
 *
 * <h3>How it replaces pg_cron / external cron</h3>
 * <ul>
 *   <li>Jobs are stored in a table with cron expression + SQL command</li>
 *   <li>Execution history is logged for auditing (like {@code cron.job_run_details})</li>
 *   <li>Combined with Spring {@code @Scheduled}, this runs jobs reliably</li>
 * </ul>
 *
 * <h3>pg_cron in production</h3>
 * For real deployments, enable the pg_cron extension:
 * <pre>
 * CREATE EXTENSION pg_cron;
 * SELECT cron.schedule('cleanup', '0 * * * *',
 *   $$DELETE FROM cache_entries WHERE expires_at < NOW()$$);
 * </pre>
 * This runs entirely inside Postgres — no external process needed.
 */
@Repository
public class CronJobRepository {

    private final JdbcTemplate jdbc;

    public CronJobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Register a new scheduled job. */
    public Long schedule(String jobName, String cronExpression, String sqlCommand) {
        return jdbc.queryForObject("""
            INSERT INTO cron_jobs (job_name, cron_expression, sql_command, status)
            VALUES (?, ?, ?, 'scheduled')
            RETURNING id
            """,
            Long.class,
            jobName, cronExpression, sqlCommand
        );
    }

    /** List all registered jobs. */
    public List<CronJobDetail> listJobs() {
        return jdbc.query("""
            SELECT id, job_name, cron_expression, sql_command, status,
                   result_message, last_run_at, next_run_at, created_at
            FROM cron_jobs
            ORDER BY created_at
            """,
            (rs, rowNum) -> new CronJobDetail(
                rs.getLong("id"),
                rs.getString("job_name"),
                rs.getString("cron_expression"),
                rs.getString("sql_command"),
                rs.getString("status"),
                rs.getString("result_message"),
                rs.getTimestamp("last_run_at") != null
                    ? rs.getTimestamp("last_run_at").toInstant() : null,
                rs.getTimestamp("next_run_at") != null
                    ? rs.getTimestamp("next_run_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            )
        );
    }

    /** Execute a job's SQL command and log the result. */
    public String executeJob(Long jobId) {
        CronJobDetail job = jdbc.queryForObject("""
            SELECT id, job_name, cron_expression, sql_command, status,
                   result_message, last_run_at, next_run_at, created_at
            FROM cron_jobs WHERE id = ?
            """,
            (rs, rowNum) -> new CronJobDetail(
                rs.getLong("id"),
                rs.getString("job_name"),
                rs.getString("cron_expression"),
                rs.getString("sql_command"),
                rs.getString("status"),
                rs.getString("result_message"),
                rs.getTimestamp("last_run_at") != null
                    ? rs.getTimestamp("last_run_at").toInstant() : null,
                rs.getTimestamp("next_run_at") != null
                    ? rs.getTimestamp("next_run_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
            ),
            jobId
        );

        try {
            jdbc.update("UPDATE cron_jobs SET status = 'running' WHERE id = ?", jobId);
            jdbc.execute(job.sqlCommand());
            String result = "Executed OK at " + java.time.Instant.now();
            jdbc.update("""
                UPDATE cron_jobs SET status = 'succeeded',
                    result_message = ?, last_run_at = NOW()
                WHERE id = ?
                """, result, jobId);
            return result;
        } catch (Exception e) {
            String error = "FAILED: " + e.getMessage();
            jdbc.update("""
                UPDATE cron_jobs SET status = 'failed',
                    result_message = ?, last_run_at = NOW()
                WHERE id = ?
                """, error, jobId);
            return error;
        }
    }

    /** Log a job execution to the history table. */
    public void logExecution(Long jobId, String jobName, String status, String message) {
        jdbc.update("""
            INSERT INTO cron_job_history (job_id, job_name, status, result_message)
            VALUES (?, ?, ?, ?)
            """, jobId, jobName, status, message);
    }

    /** Get execution history (like cron.job_run_details). */
    public List<java.util.Map<String, Object>> history(int limit) {
        return jdbc.queryForList("""
            SELECT id, job_id, job_name, status, result_message, executed_at
            FROM cron_job_history
            ORDER BY executed_at DESC
            LIMIT ?
            """, limit);
    }

    /** Delete a scheduled job. */
    public boolean delete(Long jobId) {
        return jdbc.update("DELETE FROM cron_jobs WHERE id = ?", jobId) > 0;
    }
}
