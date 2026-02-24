package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.QueueMessage;
import org.tobenamed.justusepostgres.service.QueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for Postgres-backed message queues (replaces Kafka/RabbitMQ/SQS).
 *
 * <h3>SKIP LOCKED pattern</h3>
 * {@code FOR UPDATE SKIP LOCKED} is the SQL equivalent of SQS visibility timeout:
 * multiple workers can dequeue concurrently without blocking. If a worker crashes,
 * the message reappears for another worker to pick up.
 *
 * <h3>pgmq extension</h3>
 * In production, consider the pgmq extension which wraps this pattern into a clean
 * API: {@code pgmq.send()}, {@code pgmq.read()}, {@code pgmq.delete()}.
 */
@RestController
@RequestMapping("/api/queue")
@Tag(name = "Message Queue")
public class QueueController {

    private final QueueService service;

    public QueueController(QueueService service) {
        this.service = service;
    }

    /**
     * POST /api/queue/{queue}/send
     * Body: { "payload": { ... } }
     * Enqueue a message (like RabbitMQ basic_publish or SQS SendMessage).
     */
    @PostMapping("/{queue}/send")
    @Operation(summary = "Send message", description = "Enqueue a message. Like RabbitMQ basic_publish or SQS SendMessage.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"payload\": {\"to\": \"user@example.com\", \"subject\": \"Welcome!\", \"template\": \"onboarding\"}}"))))
    public ResponseEntity<Map<String, Object>> send(
            @Parameter(description = "Queue name", example = "emails") @PathVariable String queue,
            @RequestBody Map<String, Object> request) {
        String payload = request.getOrDefault("payload", "{}").toString();
        Long id = service.send(queue, payload);
        return ResponseEntity.ok(Map.of("messageId", id, "queue", queue, "status", "sent"));
    }

    /**
     * POST /api/queue/{queue}/receive
     * Dequeue one message (like SQS ReceiveMessage).
     * Uses SKIP LOCKED — safe for concurrent workers.
     */
    @PostMapping("/{queue}/receive")
    @Operation(summary = "Receive message", description = "Dequeue one message using SKIP LOCKED. Safe for concurrent workers.")
    public ResponseEntity<?> receive(@Parameter(description = "Queue name", example = "emails") @PathVariable String queue) {
        Optional<QueueMessage> msg = service.receive(queue);
        return msg.map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    /**
     * POST /api/queue/complete/{messageId}
     * Mark message as completed (like SQS DeleteMessage).
     */
    @PostMapping("/complete/{messageId}")
    @Operation(summary = "Complete message", description = "Mark message as completed. Like SQS DeleteMessage.")
    public ResponseEntity<Map<String, Object>> complete(@Parameter(description = "Message ID", example = "1") @PathVariable Long messageId) {
        boolean ok = service.complete(messageId);
        return ResponseEntity.ok(Map.of("completed", ok, "messageId", messageId));
    }

    /**
     * POST /api/queue/fail/{messageId}
     * Mark message as failed (dead-letter).
     */
    @PostMapping("/fail/{messageId}")
    @Operation(summary = "Fail message", description = "Mark message as failed (dead-letter).")
    public ResponseEntity<Map<String, Object>> fail(@Parameter(description = "Message ID", example = "1") @PathVariable Long messageId) {
        boolean ok = service.fail(messageId);
        return ResponseEntity.ok(Map.of("failed", ok, "messageId", messageId));
    }

    /**
     * GET /api/queue/{queue}/stats
     * Queue depth by status.
     */
    @GetMapping("/{queue}/stats")
    @Operation(summary = "Queue statistics", description = "Get message count grouped by status.")
    public List<Map<String, Object>> stats(@Parameter(description = "Queue name", example = "emails") @PathVariable String queue) {
        return service.stats(queue);
    }

    /**
     * GET /api/queue/{queue}/messages?status=pending&limit=20
     * List messages in a queue.
     */
    @GetMapping("/{queue}/messages")
    @Operation(summary = "List queue messages", description = "Browse messages in a queue, optionally filtering by status.")
    public List<QueueMessage> list(
            @Parameter(description = "Queue name", example = "emails") @PathVariable String queue,
            @Parameter(description = "Filter by status", example = "pending") @RequestParam(required = false) String status,
            @Parameter(description = "Max results", example = "20") @RequestParam(defaultValue = "20") int limit) {
        return service.list(queue, status, limit);
    }
}
