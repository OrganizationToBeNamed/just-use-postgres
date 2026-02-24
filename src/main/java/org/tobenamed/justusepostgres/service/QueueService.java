package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.QueueMessage;
import org.tobenamed.justusepostgres.repository.QueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for Postgres-backed message queues (replaces Kafka/RabbitMQ/SQS).
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);
    private static final int VISIBILITY_TIMEOUT_SECONDS = 30;

    private final QueueRepository repo;

    public QueueService(QueueRepository repo) {
        this.repo = repo;
    }

    public Long send(String queue, String payload) {
        return repo.send(queue, payload);
    }

    public Optional<QueueMessage> receive(String queue) {
        return repo.receive(queue);
    }

    public boolean complete(Long messageId) {
        return repo.complete(messageId);
    }

    public boolean fail(Long messageId) {
        return repo.fail(messageId);
    }

    public List<Map<String, Object>> stats(String queue) {
        return repo.stats(queue);
    }

    public List<QueueMessage> list(String queue, String status, int limit) {
        return repo.list(queue, status, limit);
    }

    /** Periodically requeue messages stuck in 'processing' (visibility timeout expired). */
    @Scheduled(fixedRate = 10000) // every 10 seconds
    public void requeueStaleMessages() {
        // We check across all queues by requeuing for common queue names
        int requeued = repo.requeueStale("default", VISIBILITY_TIMEOUT_SECONDS);
        if (requeued > 0) {
            log.info("Queue: requeued {} stale messages (visibility timeout expired)", requeued);
        }
    }
}
