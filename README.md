# Just Use Postgres

A Spring Boot application demonstrating how **one PostgreSQL instance** replaces
10 specialized databases/services — using the **same algorithms** they use internally.

> **[Live Interactive Guide](https://OrganizationToBeNamed.github.io/just-use-postgres/)** — explore the concepts in your browser.
>
> **[Live API (Swagger UI)](http://140.245.248.160/swagger-ui/index.html)** — interact with the hosted REST API directly.

---

## The Thesis

| What You Need    | Specialized Tool       | Postgres Extension                    | Same Algorithm?               |
|------------------|------------------------|----------------------------------------|-------------------------------|
| Full-text search | Elasticsearch          | Built-in `tsvector` + `pg_trgm`       | Both use **BM25**             |
| Vector search    | Pinecone               | `pgvector` (HNSW index)               | Both use **HNSW/DiskANN**     |
| Time-series      | InfluxDB               | Partitioning + BRIN index              | Both use **time partitioning**|
| Caching          | Redis                  | `UNLOGGED` tables                      | Both use **in-memory storage**|
| Documents        | MongoDB                | `JSONB` + GIN index                    | Both use **document indexing**|
| Geospatial       | Specialized GIS        | `PostGIS`                              | Industry standard since 2001  |
| Message queues   | Kafka / RabbitMQ / SQS | `SKIP LOCKED` + `pgmq`                | Visibility timeout pattern    |
| Cron / scheduling| External cron / Airflow| `pg_cron`                              | Cron scheduling in-database   |
| Graph traversal  | Neo4j                  | Recursive `CTEs` + `Apache AGE`        | BFS/DFS traversal             |
| Hybrid search    | ES + Pinecone pipeline | `tsvector` + `pgvector` RRF            | Reciprocal Rank Fusion        |

### Key Extensions

| Extension        | Replaces               | Highlights                                          |
|------------------|------------------------|-----------------------------------------------------|
| pgvector         | Pinecone, Qdrant       | HNSW algorithm. 28x lower latency, 75% less cost.   |
| pg_trgm          | Elasticsearch          | Trigram fuzzy matching + BM25 ranking natively.      |
| PostGIS          | Specialized GIS        | Industry standard since 2001: geometry, geography.   |
| JSONB + GIN      | MongoDB                | Schema-free docs with full indexing + SQL JOINs.     |
| UNLOGGED tables  | Redis                  | WAL-skip = in-memory speed, same crash semantics.    |
| Partitioning     | InfluxDB               | Time-range partitions + BRIN for time-series.        |
| SKIP LOCKED/pgmq | Kafka/RabbitMQ/SQS     | Concurrent dequeue + visibility timeout in SQL.      |
| pg_cron          | External cron/Airflow  | Cron scheduler inside the database + job history.    |
| Recursive CTEs   | Neo4j                  | Graph BFS/DFS traversal in pure SQL.                 |
| RRF hybrid search| ES + Pinecone          | BM25 + vector fusion in a single query.              |

---

## Quick Start

```bash
# Clone and run — one command does everything
git clone https://github.com/OrganizationToBeNamed/just-use-postgres.git
cd just-use-postgres
./start.sh
```

The script builds Docker images, starts Postgres + Spring Boot, waits for health checks, and prints:

| URL | What |
|-----|------|
| **[http://140.245.248.160/swagger-ui/index.html](http://140.245.248.160/swagger-ui/index.html)** | **Live Swagger UI** — hosted instance, no setup needed |
| http://localhost:8080/swagger-ui/index.html | Swagger UI (local) — explore all 10 APIs interactively |
| http://localhost:8080/api-docs | OpenAPI spec (JSON) |
| http://localhost:8080/actuator/health | Health check |

<details>
<summary>Alternative: run app outside Docker (for development)</summary>

```bash
# Start only the database in Docker
docker compose up postgres -d

# Run Spring Boot locally with Maven
./mvnw spring-boot:run
```
</details>

To stop everything:
```bash
docker compose down
```

---

## API Reference

### 1. Full-Text Search (replaces Elasticsearch)

```bash
# BM25-ranked search with highlighted snippets
curl "http://localhost:8080/api/search?q=postgresql+features&limit=5"

# Fuzzy search — tolerates typos (e.g., "postgre" → "PostgreSQL")
curl "http://localhost:8080/api/search/fuzzy?q=postgre&threshold=0.3"

# Add a new searchable article
curl -X POST http://localhost:8080/api/search/articles \
  -H "Content-Type: application/json" \
  -d '{"title":"New Article","body":"Content about databases and search..."}'
```

**How it works:**
- `tsvector` stores pre-parsed lexemes (stemmed tokens) — same as ES analyzers
- `ts_rank()` implements BM25-style TF-IDF ranking — same algorithm as ES
- `ts_headline()` generates `<b>highlighted</b>` snippets — like ES highlighting
- GIN index on tsvector = inverted index — same structure as Lucene
- `pg_trgm` adds trigram-based fuzzy matching for typo tolerance

---

### 2. Vector Search (replaces Pinecone)

```bash
# Similarity search (random demo vector if none provided)
curl -X POST http://localhost:8080/api/vectors/search \
  -H "Content-Type: application/json" \
  -d '{"limit": 5}'

# Add a document (auto-generates demo embedding)
curl -X POST http://localhost:8080/api/vectors/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"AI Paper","content":"Deep learning approaches to NLP..."}'
```

**How it works:**
- `vector(384)` type stores N-dimensional float arrays
- `<=>` operator computes cosine distance
- HNSW index = same ANN algorithm as Pinecone/Qdrant/Weaviate
- In production: generate embeddings via OpenAI/Hugging Face, store with `pgai`

---

### 3. Time-Series (replaces InfluxDB)

```bash
# Record a sensor reading
curl -X POST http://localhost:8080/api/timeseries/record \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"sensor-1","metricName":"temperature","value":23.5,"tags":"{\"location\":\"warehouse-A\"}"}'

# Query last 24 hours
curl "http://localhost:8080/api/timeseries/query?sensorId=sensor-1&hoursBack=24"

# Aggregate by hour (like InfluxDB GROUP BY time(1h))
curl "http://localhost:8080/api/timeseries/aggregate?sensorId=sensor-1&metric=temperature&interval=hour&hoursBack=168"

# Latest reading per sensor
curl "http://localhost:8080/api/timeseries/latest"
```

**How it works:**
- `PARTITION BY RANGE (recorded_at)` = same time-partitioning as InfluxDB
- Partition pruning: queries only scan relevant monthly partitions
- `BRIN` index: ~1000x smaller than B-tree for time-ordered data
- `date_trunc()` = InfluxDB's `GROUP BY time()` for aggregation
- `DISTINCT ON` = InfluxDB's `LAST()` function

---

### 4. Cache (replaces Redis)

```bash
# SET with TTL (like Redis: SET key value EX 3600)
curl -X PUT http://localhost:8080/api/cache/my-key \
  -H "Content-Type: application/json" \
  -d '{"value":{"foo":"bar","count":42},"ttlSeconds":3600}'

# GET (like Redis: GET key)
curl http://localhost:8080/api/cache/my-key

# DEL (like Redis: DEL key)
curl -X DELETE http://localhost:8080/api/cache/my-key
```

**How it works:**
- `UNLOGGED` tables skip Write-Ahead Log → 2-5x faster writes
- Same durability as Redis: data survives restart, lost on crash
- `ON CONFLICT DO UPDATE` = atomic upsert (like Redis SET)
- `expires_at` + scheduled cleanup = TTL expiration (like Redis EXPIRE)
- Unlike Redis: you can query cache values with SQL and JSONB operators

---

### 5. Documents (replaces MongoDB)

```bash
# List all documents in a collection (like db.users.find())
curl http://localhost:8080/api/documents/users

# Query with filter (like db.users.find({address:{city:"New York"}}))
curl -X POST http://localhost:8080/api/documents/users/query \
  -H "Content-Type: application/json" \
  -d '{"address":{"city":"New York"}}'

# Insert document (like db.users.insertOne({...}))
curl -X POST http://localhost:8080/api/documents/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Dave","email":"dave@example.com","age":28}'

# Update — merge/patch (like db.users.updateOne({}, {$set: {...}}))
curl -X PATCH http://localhost:8080/api/documents/1 \
  -H "Content-Type: application/json" \
  -d '{"age":31}'

# Delete (like db.users.deleteOne({_id: 1}))
curl -X DELETE http://localhost:8080/api/documents/byId/1
```

**How it works:**
- `JSONB` stores documents in decomposed binary → faster than MongoDB's BSON
- `@>` operator: containment check (like MongoDB's `$elemMatch`)
- `||` operator: merge patch (like MongoDB's `$set`)
- GIN index on JSONB indexes ALL keys/values automatically
- Unlike MongoDB: supports JOINs and full ACID transactions

---

### 6. Geospatial (replaces GIS databases)

```bash
# Find landmarks near New York (50km radius)
curl "http://localhost:8080/api/geo/nearby?lat=40.7128&lng=-74.0060&radius=50000"

# Find landmarks near London (100km radius)
curl "http://localhost:8080/api/geo/nearby?lat=51.5074&lng=-0.1278&radius=100000"

# Add a new location
curl -X POST http://localhost:8080/api/geo/locations \
  -H "Content-Type: application/json" \
  -d '{"name":"Central Park","description":"Urban park in NYC","latitude":40.7829,"longitude":-73.9654,"properties":"{\"type\":\"park\"}"}'
```

**How it works:**
- `geography(POINT, 4326)` stores coordinates on a spheroid (WGS84/GPS)
- `ST_DWithin()` uses GiST spatial index for fast radius queries
- `ST_Distance()` computes great-circle distance in meters
- PostGIS has been the industry standard for geospatial since 2001

---

### 7. Message Queues (replaces Kafka / RabbitMQ / SQS)

```bash
# Send a message to a queue
curl -X POST http://localhost:8080/api/queue/emails/send \
  -H "Content-Type: application/json" \
  -d '{"payload":{"to":"user@example.com","subject":"Welcome!"}}'

# Receive (dequeue) a message — uses SKIP LOCKED
curl -X POST http://localhost:8080/api/queue/emails/receive

# Mark as completed (like SQS DeleteMessage)
curl -X POST http://localhost:8080/api/queue/complete/1

# Queue stats (depth by status)
curl http://localhost:8080/api/queue/emails/stats

# List messages
curl "http://localhost:8080/api/queue/emails/messages?status=pending&limit=10"
```

**How it works:**
- `FOR UPDATE SKIP LOCKED` — workers grab different rows without blocking
- Visibility timeout — if worker crashes, message reappears for retry
- Process message + business logic in ONE transaction — exactly-once delivery
- `pgmq` extension wraps this into a clean send/read/delete API

---

### 8. Cron / Scheduled Jobs (replaces external cron / Airflow)

```bash
# Schedule a cleanup job
curl -X POST http://localhost:8080/api/cron/schedule \
  -H "Content-Type: application/json" \
  -d '{"jobName":"cleanup-cache","cron":"0 * * * *","sql":"DELETE FROM cache_entries WHERE expires_at < NOW()"}'

# List registered jobs
curl http://localhost:8080/api/cron/jobs

# Manually trigger a job
curl -X POST http://localhost:8080/api/cron/execute/1

# View execution history (like cron.job_run_details)
curl "http://localhost:8080/api/cron/history?limit=10"
```

**How it works:**
- `pg_cron` extension runs cron schedules natively inside Postgres
- Standard 5-field cron syntax — no new DSL to learn
- Job history stored in `cron.job_run_details` — queryable via SQL
- Jobs run as SQL inside Postgres — full transactional guarantees

---

### 9. Graph Traversal (replaces Neo4j)

```bash
# Get org tree starting from employee #1 (CEO)
curl http://localhost:8080/api/graph/org-tree/1

# Get ancestors (path to root) for employee #6
curl http://localhost:8080/api/graph/ancestors/6

# Get team size (all direct + indirect reports)
curl http://localhost:8080/api/graph/team-size/1

# List all employees
curl http://localhost:8080/api/graph/employees

# Add an employee
curl -X POST http://localhost:8080/api/graph/employees \
  -H "Content-Type: application/json" \
  -d '{"name":"Kate","title":"Intern","managerId":4}'
```

**How it works:**
- `WITH RECURSIVE` CTE performs BFS/DFS traversal in pure SQL
- Adjacency list model: `manager_id` self-references `id`
- PATH array tracks traversal route; cycle detection built in (PG 14+)
- Apache AGE extension adds Cypher query language for richer graph workloads

---

### 10. Hybrid Search (replaces ES + Pinecone pipeline)

```bash
# Hybrid search: BM25 + vector similarity with RRF fusion
curl -X POST http://localhost:8080/api/hybrid-search \
  -H "Content-Type: application/json" \
  -d '{"query":"vector database performance","limit":5}'

# Keyword-only search (for comparison)
curl "http://localhost:8080/api/hybrid-search/keyword?q=vector+database&limit=5"
```

**How it works:**
- CTE 1: BM25-ranked keyword search via `tsvector`
- CTE 2: cosine similarity via `pgvector`
- FULL OUTER JOIN + Reciprocal Rank Fusion: `1/(60+rank_kw) + 1/(60+rank_vec)`
- One SQL query, one round trip — no multi-service orchestration
- Studies show hybrid search outperforms either method alone by 5-15%

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                    │
│                                                              │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐  │
│  │  Vector    │ │ Full-Text │ │Time-Series│ │   Cache     │  │
│  │  Search    │ │  Search   │ │ Analytics │ │  (Redis)    │  │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────┐  │
│  │ Document  │ │GeoSpatial │ │  Message  │ │    Cron     │  │
│  │ (MongoDB) │ │  (GIS)    │ │  Queues   │ │   Jobs      │  │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────┘  │
│  ┌───────────┐ ┌───────────┐                                │
│  │   Graph   │ │  Hybrid   │                                │
│  │ Traversal │ │  Search   │                                │
│  └─────┬─────┘ └─────┬─────┘                                │
│        └──────────────┼──────────────────────────────────────│
│              ┌────────┴────────┐                             │
│              │   JdbcTemplate  │                             │
│              └────────┬────────┘                             │
└───────────────────────┼──────────────────────────────────────┘
                        │
         ┌──────────────┴──────────────┐
         │     PostgreSQL 16            │
         │                              │
         │  ✅ pgvector    (HNSW)       │
         │  ✅ tsvector    (BM25)       │
         │  ✅ pg_trgm     (fuzzy)      │
         │  ✅ PostGIS     (geospatial) │
         │  ✅ JSONB+GIN   (documents)  │
         │  ✅ UNLOGGED    (caching)    │
         │  ✅ Partitions  (time-series)│
         │  ✅ SKIP LOCKED (queues)     │
         │  ✅ pg_cron     (scheduling) │
         │  ✅ Recursive CTEs (graphs)  │
         │  ✅ RRF hybrid  (search)     │
         └──────────────────────────────┘
```

---

## Project Structure

```
just-use-postgres/
├── docker/
│   ├── Dockerfile.postgres    # Custom PG16 with pgvector + PostGIS
│   └── init.sql               # Extensions, tables, indexes, seed data
├── docs/
│   └── index.html             # Interactive static website (GitHub Pages)
├── docker-compose.yml         # One-command startup
├── Dockerfile                 # Multi-stage Spring Boot build
├── start.sh                   # One-click launcher script
├── pom.xml
└── src/main/java/org/tobenamed/justusepostgres/
    ├── JustUsePostgresApplication.java    # Entry point + @EnableScheduling
    ├── config/
    │   └── PostgresExtensionsConfig.java  # Extension health check at startup
    ├── controller/
    │   ├── VectorSearchController.java    # /api/vectors/*
    │   ├── FullTextSearchController.java  # /api/search/*
    │   ├── TimeSeriesController.java      # /api/timeseries/*
    │   ├── CacheController.java           # /api/cache/*
    │   ├── DocumentController.java        # /api/documents/*
    │   ├── GeoSpatialController.java      # /api/geo/*
    │   ├── QueueController.java           # /api/queue/*
    │   ├── CronJobController.java         # /api/cron/*
    │   ├── GraphController.java           # /api/graph/*
    │   └── HybridSearchController.java    # /api/hybrid-search/*
    ├── model/
    │   ├── VectorDocument.java            # vector(384) embeddings
    │   ├── SearchResult.java              # tsvector + ts_rank
    │   ├── SensorReading.java             # partitioned time-series
    │   ├── CacheEntry.java                # UNLOGGED table entry
    │   ├── Document.java                  # JSONB document
    │   ├── GeoLocation.java               # geography(POINT, 4326)
    │   ├── QueueMessage.java              # SKIP LOCKED message queue
    │   ├── CronJobDetail.java             # pg_cron job definition
    │   ├── Employee.java                  # Recursive CTE graph node
    │   └── HybridSearchResult.java        # RRF hybrid search result
    ├── repository/                        # Raw JDBC with JdbcTemplate
    │   ├── VectorSearchRepository.java
    │   ├── FullTextSearchRepository.java
    │   ├── TimeSeriesRepository.java
    │   ├── CacheRepository.java
    │   ├── DocumentRepository.java
    │   ├── GeoSpatialRepository.java
    │   ├── QueueRepository.java
    │   ├── CronJobRepository.java
    │   ├── GraphRepository.java
    │   └── HybridSearchRepository.java
    └── service/
        ├── VectorSearchService.java
        ├── FullTextSearchService.java
        ├── TimeSeriesService.java
        ├── CacheService.java              # Includes @Scheduled TTL cleanup
        ├── DocumentService.java
        ├── GeoSpatialService.java
        ├── QueueService.java              # Includes @Scheduled stale requeue
        ├── CronJobService.java
        ├── GraphService.java
        └── HybridSearchService.java
```

---

## When to Still Use Specialized Tools

| Scenario                        | Why Postgres might not be enough         |
|---------------------------------|------------------------------------------|
| Sub-millisecond latency         | Redis: ~0.1ms vs Postgres UNLOGGED: ~1-5ms |
| 1B+ vectors                     | Dedicated vector DB may be more cost-effective |
| Massive text corpus (TB+)       | Elasticsearch's distributed sharding is more mature |
| High-throughput event streaming  | Kafka/Pulsar for millions of events/sec with replay |
| Deep graph traversals (50+ hops)| Neo4j's native storage engine is faster at scale |

But for **90% of applications**, just use Postgres.
