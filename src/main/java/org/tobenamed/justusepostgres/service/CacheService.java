package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.CacheEntry;
import org.tobenamed.justusepostgres.repository.CacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final CacheRepository repo;

    public CacheService(CacheRepository repo) {
        this.repo = repo;
    }

    public Optional<CacheEntry> get(String key) {
        return repo.get(key);
    }

    public void set(String key, String jsonValue, Duration ttl) {
        repo.set(key, jsonValue, ttl);
    }

    public boolean delete(String key) {
        return repo.delete(key);
    }

    /** Periodic expired entry cleanup — like Redis's lazy + active expiration. */
    @Scheduled(fixedRate = 60000) // every 60 seconds
    public void cleanupExpired() {
        int evicted = repo.evictExpired();
        if (evicted > 0) {
            log.info("Cache: evicted {} expired entries", evicted);
        }
    }
}
