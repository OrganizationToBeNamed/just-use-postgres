package org.tobenamed.justusepostgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Just Use Postgres — A single Spring Boot app demonstrating how PostgreSQL
 * extensions replace 6 specialized databases:
 *
 * <table>
 *   <tr><th>Need</th><th>Specialized Tool</th><th>Postgres Extension</th></tr>
 *   <tr><td>Full-text search</td><td>Elasticsearch</td><td>Built-in tsvector + pg_trgm</td></tr>
 *   <tr><td>Vector search</td><td>Pinecone</td><td>pgvector (HNSW)</td></tr>
 *   <tr><td>Time-series</td><td>InfluxDB</td><td>Partitioning + BRIN</td></tr>
 *   <tr><td>Caching</td><td>Redis</td><td>UNLOGGED tables</td></tr>
 *   <tr><td>Documents</td><td>MongoDB</td><td>JSONB + GIN indexes</td></tr>
 *   <tr><td>Geospatial</td><td>Specialized GIS</td><td>PostGIS</td></tr>
 * </table>
 */
@SpringBootApplication
@EnableScheduling
public class JustUsePostgresApplication {
    public static void main(String[] args) {
        SpringApplication.run(JustUsePostgresApplication.class, args);
    }
}
