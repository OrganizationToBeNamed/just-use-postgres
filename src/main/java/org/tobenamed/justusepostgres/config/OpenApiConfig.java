package org.tobenamed.justusepostgres.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI justUsePostgresOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Just Use Postgres API")
                        .description("""
                                One PostgreSQL database replacing 10 specialized services — \
                                Elasticsearch, Pinecone, InfluxDB, Redis, MongoDB, GIS databases, \
                                Kafka, cron daemons, Neo4j, and multi-service search pipelines. \
                                Each endpoint demonstrates the same algorithm the specialized tool uses internally.""")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Rajesh Anji")
                                .url("https://github.com/OrganizationToBeNamed/just-use-postgres"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .tags(List.of(
                        new Tag().name("Full-Text Search")
                                .description("BM25 ranking via tsvector + pg_trgm (replaces Elasticsearch)"),
                        new Tag().name("Vector Search")
                                .description("HNSW approximate nearest neighbor via pgvector (replaces Pinecone)"),
                        new Tag().name("Time-Series")
                                .description("Partitioned tables + BRIN indexes (replaces InfluxDB)"),
                        new Tag().name("Cache")
                                .description("UNLOGGED tables for WAL-skip caching (replaces Redis)"),
                        new Tag().name("Documents")
                                .description("JSONB + GIN indexes for document storage (replaces MongoDB)"),
                        new Tag().name("Geospatial")
                                .description("PostGIS R-tree spatial indexes (replaces specialized GIS)"),
                        new Tag().name("Message Queue")
                                .description("SKIP LOCKED + pgmq pattern (replaces Kafka/RabbitMQ)"),
                        new Tag().name("Cron Jobs")
                                .description("pg_cron for in-database scheduling (replaces external cron/Airflow)"),
                        new Tag().name("Graph Traversal")
                                .description("Recursive CTEs for graph BFS/DFS (replaces Neo4j)"),
                        new Tag().name("Hybrid Search")
                                .description("BM25 + vector with Reciprocal Rank Fusion (replaces ES + Pinecone pipeline)")
                ));
    }
}
