package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.CronJobDetail;
import org.tobenamed.justusepostgres.service.CronJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for Postgres-backed scheduled jobs (replaces external cron / Airflow).
 *
 * <h3>pg_cron in production</h3>
 * The pg_cron extension runs cron schedules natively inside Postgres:
 * <pre>
 * SELECT cron.schedule('cleanup', '0 * * * *',
 *   $$DELETE FROM cache_entries WHERE expires_at < NOW()$$);
 * </pre>
 *
 * This controller demonstrates the same concept: register SQL jobs, execute them,
 * and view execution history — all stored in Postgres tables.
 */
@RestController
@RequestMapping("/api/cron")
@Tag(name = "Cron Jobs")
public class CronJobController {

    private final CronJobService service;

    public CronJobController(CronJobService service) {
        this.service = service;
    }

    /**
     * POST /api/cron/schedule
     * Body: { "jobName": "cleanup", "cron": "0 * * * *", "sql": "DELETE FROM ..." }
     * Register a new scheduled job.
     */
    @PostMapping("/schedule")
    @Operation(summary = "Schedule a job", description = "Register a new cron-scheduled SQL job. Like pg_cron's cron.schedule().",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"jobName\": \"cleanup-expired-cache\", \"cron\": \"0 * * * *\", \"sql\": \"DELETE FROM cache_entries WHERE expires_at < NOW()\"}"))))
    public ResponseEntity<Map<String, Object>> schedule(@RequestBody Map<String, String> request) {
        String jobName = request.get("jobName");
        String cron = request.get("cron");
        String sql = request.get("sql");
        Long id = service.schedule(jobName, cron, sql);
        return ResponseEntity.ok(Map.of("id", id, "jobName", jobName, "status", "scheduled"));
    }

    /**
     * GET /api/cron/jobs
     * List all registered jobs.
     */
    @GetMapping("/jobs")
    @Operation(summary = "List all jobs", description = "Returns all registered cron jobs with schedule and status.")
    public List<CronJobDetail> listJobs() {
        return service.listJobs();
    }

    /**
     * POST /api/cron/execute/{jobId}
     * Manually trigger a job (like pg_cron's immediate execution).
     */
    @PostMapping("/execute/{jobId}")
    @Operation(summary = "Execute job now", description = "Manually trigger a scheduled job immediately.")
    public ResponseEntity<Map<String, Object>> execute(@Parameter(description = "Job ID", example = "1") @PathVariable Long jobId) {
        String result = service.executeJob(jobId);
        return ResponseEntity.ok(Map.of("jobId", jobId, "result", result));
    }

    /**
     * GET /api/cron/history?limit=20
     * View execution history (like cron.job_run_details).
     */
    @GetMapping("/history")
    @Operation(summary = "Execution history", description = "View past job executions with status and duration.")
    public List<Map<String, Object>> history(@Parameter(description = "Max results", example = "10") @RequestParam(defaultValue = "20") int limit) {
        return service.history(limit);
    }

    /**
     * DELETE /api/cron/jobs/{jobId}
     * Remove a scheduled job.
     */
    @DeleteMapping("/jobs/{jobId}")
    @Operation(summary = "Delete a job", description = "Remove a scheduled job. Like cron.unschedule().")
    public ResponseEntity<Map<String, Boolean>> delete(@Parameter(description = "Job ID", example = "1") @PathVariable Long jobId) {
        boolean deleted = service.delete(jobId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
