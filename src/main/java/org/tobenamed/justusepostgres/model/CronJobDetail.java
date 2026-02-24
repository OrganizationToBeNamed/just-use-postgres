package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Represents a scheduled job executed inside Postgres (replaces external cron / Airflow).
 *
 * <h3>pg_cron equivalent</h3>
 * In production you'd use the pg_cron extension which runs cron expressions
 * natively inside the database. This model demonstrates the concept using
 * application-level scheduling via Spring's {@code @Scheduled}, which
 * triggers SQL statements on a cron schedule — same net effect.
 *
 * <h3>Why inside the database?</h3>
 * <ul>
 *   <li>No missed jobs if an external cron daemon reboots</li>
 *   <li>Job + data mutation in the same transaction</li>
 *   <li>Built-in job history via a log table</li>
 * </ul>
 */
public record CronJobDetail(
    Long id,
    String jobName,
    String cronExpression,
    String sqlCommand,
    String status,       // 'scheduled', 'running', 'succeeded', 'failed'
    String resultMessage,
    Instant lastRunAt,
    Instant nextRunAt,
    Instant createdAt
) {}
