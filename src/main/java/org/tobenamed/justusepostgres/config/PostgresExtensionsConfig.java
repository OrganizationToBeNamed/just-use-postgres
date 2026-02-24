package org.tobenamed.justusepostgres.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Verifies that all required Postgres extensions are loaded at startup.
 * This gives immediate feedback if the database is not configured correctly.
 */
@Configuration
public class PostgresExtensionsConfig {

    private static final Logger log = LoggerFactory.getLogger(PostgresExtensionsConfig.class);

    @Bean
    CommandLineRunner verifyExtensions(JdbcTemplate jdbc) {
        return args -> {
            List<String> extensions = jdbc.queryForList(
                "SELECT extname FROM pg_extension ORDER BY extname", String.class
            );
            log.info("=== Loaded PostgreSQL Extensions ===");
            extensions.forEach(ext -> log.info("  ✓ {}", ext));

            // Verify critical extensions
            List<String> required = List.of("vector", "pg_trgm", "postgis");
            for (String ext : required) {
                if (!extensions.contains(ext)) {
                    log.warn("  ✗ MISSING: {} — some features will not work!", ext);
                }
            }
            log.info("====================================");
        };
    }
}
