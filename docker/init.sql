-- =============================================================================
-- INIT.SQL — One Postgres to rule them all
-- =============================================================================
-- This script enables every extension that replaces a specialized database.
-- Each section explains WHAT it replaces and WHY the algorithm is equivalent.

-- =============================================================================
-- 1. pgvector — Replaces Pinecone / Qdrant
-- =============================================================================
-- Both use HNSW (Hierarchical Navigable Small World) for approximate nearest
-- neighbor search. pgvectorscale adds DiskANN for even better performance:
--   • 28x lower p95 latency than Pinecone
--   • 75% lower cost
-- The core data type is `vector(N)` which stores N-dimensional float arrays.
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- 2. pg_trgm + built-in Full-Text Search — Replaces Elasticsearch
-- =============================================================================
-- Postgres has a native full-text search engine that uses the BM25 ranking
-- algorithm (via ts_rank) — the SAME algorithm Elasticsearch uses.
-- pg_trgm adds trigram-based fuzzy matching for "did you mean?" style queries.
-- Together they cover 95% of Elasticsearch use cases.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================================
-- 3. PostGIS — Replaces specialized GIS databases
-- =============================================================================
-- PostGIS has been the industry standard for geospatial since 2001.
-- It supports geometry, geography, raster, and topology types.
-- Provides spatial indexing via GiST/SP-GiST trees.
CREATE EXTENSION IF NOT EXISTS postgis;

-- =============================================================================
-- 4. UNLOGGED tables — Replaces Redis (in-memory caching)
-- =============================================================================
-- UNLOGGED tables skip the Write-Ahead Log (WAL), making them as fast as
-- in-memory stores. Data survives normal operation but is lost on crash —
-- exactly the same durability guarantee as Redis (which is also not durable
-- by default). Perfect for caching, sessions, rate-limiting.

-- =============================================================================
-- 5. JSONB — Replaces MongoDB (document store)
-- =============================================================================
-- JSONB stores JSON in a decomposed binary format, enabling GIN indexing on
-- any nested key. You get the schema flexibility of MongoDB PLUS the ability
-- to JOIN documents with relational tables — something MongoDB cannot do.

-- =============================================================================
-- 6. Time partitioning — Replaces InfluxDB (time-series)
-- =============================================================================
-- Native Postgres declarative partitioning by time range gives you the same
-- time-partitioned storage that InfluxDB uses. Combined with BRIN indexes
-- (Block Range INdex), queries on time ranges are extremely fast.
-- TimescaleDB automates this, but native partitioning works well too.

-- =============================================================================
-- 7. SKIP LOCKED / pgmq — Replaces Kafka / RabbitMQ / SQS (message queues)
-- =============================================================================
-- FOR UPDATE SKIP LOCKED lets multiple workers dequeue concurrently without
-- blocking each other — the same visibility-timeout concept as SQS.
-- The pgmq extension (by Tembo) wraps this into a clean API. Here we use
-- the raw SQL pattern for maximum portability.

-- =============================================================================
-- 8. pg_cron — Replaces external cron / Airflow (scheduled jobs)
-- =============================================================================
-- pg_cron runs cron schedules natively inside PostgreSQL. No external daemon,
-- no missed jobs on reboot, and job + data mutation in the same transaction.
-- Here we demonstrate the pattern with application-level tables.

-- =============================================================================
-- 9. Recursive CTEs — Replaces Neo4j (graph traversal)
-- =============================================================================
-- WITH RECURSIVE performs BFS/DFS traversal in pure SQL. Adjacency-list
-- (self-referencing FK) is the simplest graph model. For richer graph
-- workloads, Apache AGE adds Cypher query language on top of Postgres.

-- =============================================================================
-- 10. Hybrid Search — Replaces ES + Pinecone pipeline
-- =============================================================================
-- Combine BM25 keyword search (tsvector) with vector similarity (pgvector)
-- in a single SQL query using Reciprocal Rank Fusion (RRF). No multi-service
-- orchestration, no app-side merging, one network round trip.

-- =============================================================================
-- CREATE TABLES
-- =============================================================================

-- VECTOR SEARCH TABLE (replaces Pinecone)
-- Stores documents with their embedding vectors for semantic similarity search.
CREATE TABLE IF NOT EXISTS vector_documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(384),  -- 384-dim for all-MiniLM-L6-v2; change as needed
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- HNSW index: the SAME algorithm Pinecone uses internally
-- m = max connections per node, ef_construction = build-time search width
CREATE INDEX IF NOT EXISTS idx_vector_documents_embedding
    ON vector_documents
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- FULL-TEXT SEARCH TABLE (replaces Elasticsearch)
-- tsvector column stores pre-parsed lexemes for fast BM25-ranked search.
CREATE TABLE IF NOT EXISTS articles (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    tsv tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(body, '')), 'B')
    ) STORED,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- GIN index on the tsvector column — equivalent to an inverted index in ES
CREATE INDEX IF NOT EXISTS idx_articles_tsv ON articles USING gin(tsv);
-- Trigram index for fuzzy / "did you mean" queries
CREATE INDEX IF NOT EXISTS idx_articles_title_trgm ON articles USING gin(title gin_trgm_ops);

-- GEOSPATIAL TABLE (replaces specialized GIS)
-- `geography` type stores lat/lng on a sphere for accurate distance calculations.
CREATE TABLE IF NOT EXISTS geo_locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    coordinates geography(POINT, 4326) NOT NULL, -- SRID 4326 = WGS84 (GPS)
    properties JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Spatial index using GiST — enables fast bounding-box and distance queries
CREATE INDEX IF NOT EXISTS idx_geo_locations_coordinates
    ON geo_locations USING gist(coordinates);

-- CACHE TABLE (replaces Redis)
-- UNLOGGED = no WAL writes = in-memory speed. Data lost on crash (same as Redis).
CREATE UNLOGGED TABLE IF NOT EXISTS cache_entries (
    key VARCHAR(255) PRIMARY KEY,
    value JSONB NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cache_entries_expires ON cache_entries(expires_at);

-- DOCUMENT STORE TABLE (replaces MongoDB)
-- JSONB column stores arbitrary nested documents with full indexing support.
CREATE TABLE IF NOT EXISTS documents (
    id BIGSERIAL PRIMARY KEY,
    collection VARCHAR(100) NOT NULL,  -- like a MongoDB "collection"
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- GIN index on JSONB — indexes ALL keys and values for fast lookups
CREATE INDEX IF NOT EXISTS idx_documents_data ON documents USING gin(data);
CREATE INDEX IF NOT EXISTS idx_documents_collection ON documents(collection);

-- TIME-SERIES TABLE (replaces InfluxDB)
-- Declarative partitioning by time range — same strategy InfluxDB uses internally.
CREATE TABLE IF NOT EXISTS sensor_readings (
    id BIGSERIAL,
    sensor_id VARCHAR(100) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tags JSONB DEFAULT '{}',
    PRIMARY KEY (recorded_at, id)
) PARTITION BY RANGE (recorded_at);

-- Create monthly partitions (in production, automate with pg_partman or cron)
CREATE TABLE IF NOT EXISTS sensor_readings_2025_01 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_02 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_03 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_04 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_05 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_06 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_07 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_08 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_09 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_10 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_11 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2025_12 PARTITION OF sensor_readings
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_01 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_02 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_03 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_04 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_05 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_06 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_07 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_08 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_09 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_10 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_11 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS sensor_readings_2026_12 PARTITION OF sensor_readings
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');
CREATE TABLE IF NOT EXISTS sensor_readings_default PARTITION OF sensor_readings DEFAULT;

-- BRIN index: extremely compact index for time-ordered data (same idea as InfluxDB's TSI)
CREATE INDEX IF NOT EXISTS idx_sensor_readings_time
    ON sensor_readings USING brin(recorded_at) WITH (pages_per_range = 32);
CREATE INDEX IF NOT EXISTS idx_sensor_readings_sensor
    ON sensor_readings(sensor_id, recorded_at);

-- MESSAGE QUEUE TABLE (replaces Kafka / RabbitMQ / SQS)
-- Uses SKIP LOCKED for concurrent dequeue without blocking.
CREATE TABLE IF NOT EXISTS message_queue (
    id BIGSERIAL PRIMARY KEY,
    queue VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending, processing, completed, failed
    attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mq_queue_status ON message_queue(queue, status, created_at);
CREATE INDEX IF NOT EXISTS idx_mq_processing ON message_queue(status, processed_at)
    WHERE status = 'processing';

-- CRON JOBS TABLE (replaces external cron / Airflow)
-- Stores job definitions; in production use pg_cron for native cron scheduling.
CREATE TABLE IF NOT EXISTS cron_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    sql_command TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'scheduled',  -- scheduled, running, succeeded, failed
    result_message TEXT,
    last_run_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- CRON JOB HISTORY (like cron.job_run_details in pg_cron)
CREATE TABLE IF NOT EXISTS cron_job_history (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES cron_jobs(id) ON DELETE SET NULL,
    job_name VARCHAR(255),
    status VARCHAR(20),
    result_message TEXT,
    executed_at TIMESTAMPTZ DEFAULT NOW()
);

-- EMPLOYEES TABLE (for graph traversal — replaces Neo4j)
-- Self-referencing FK creates an adjacency list (simplest graph model).
CREATE TABLE IF NOT EXISTS employees (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    title VARCHAR(255),
    manager_id BIGINT REFERENCES employees(id)
);

CREATE INDEX IF NOT EXISTS idx_employees_manager ON employees(manager_id);

-- EMBEDDING COLUMN on articles table (for hybrid search)
-- Adds vector column to existing articles table so hybrid search can combine
-- BM25 (tsvector) with vector similarity (pgvector) in a single query.
ALTER TABLE articles ADD COLUMN IF NOT EXISTS embedding vector(384);

-- =============================================================================
-- SEED DATA
-- =============================================================================

-- Seed articles for full-text search demo
INSERT INTO articles (title, body) VALUES
('Introduction to PostgreSQL', 'PostgreSQL is a powerful open source relational database management system. It has a strong reputation for reliability, feature robustness, and performance.'),
('Why PostgreSQL Beats NoSQL', 'With JSONB support, PostgreSQL can handle document workloads just as well as MongoDB, while still providing ACID transactions and relational joins.'),
('PostGIS and Geospatial Data', 'PostGIS extends PostgreSQL with geospatial types and functions. It has been the industry standard for GIS workloads since 2001.'),
('Vector Search with pgvector', 'The pgvector extension brings vector similarity search to PostgreSQL. Combined with HNSW indexes, it rivals dedicated vector databases like Pinecone.'),
('Time-Series Data in Postgres', 'Using declarative partitioning and BRIN indexes, PostgreSQL handles time-series data efficiently, replacing InfluxDB for many use cases.'),
('Caching with UNLOGGED Tables', 'UNLOGGED tables skip WAL writes, providing Redis-like speed for caching. They are perfect for ephemeral data like sessions and rate limits.'),
('Full-Text Search in PostgreSQL', 'PostgreSQL built-in full-text search uses tsvector and tsquery types with BM25 ranking via ts_rank. The GIN index on tsvector is equivalent to Elasticsearch inverted index.'),
('JSONB Document Store', 'JSONB in PostgreSQL stores documents in decomposed binary format. The GIN index automatically indexes all keys and values, making any containment query fast without per-field indexes.');

-- Seed geo_locations
INSERT INTO geo_locations (name, description, coordinates, properties) VALUES
('Statue of Liberty', 'Iconic statue in New York Harbor', ST_MakePoint(-74.0445, 40.6892)::geography, '{"type":"landmark","country":"US"}'),
('Eiffel Tower', 'Iron lattice tower in Paris', ST_MakePoint(2.2945, 48.8584)::geography, '{"type":"landmark","country":"FR"}'),
('Big Ben', 'Clock tower at the Palace of Westminster', ST_MakePoint(-0.1246, 51.5007)::geography, '{"type":"landmark","country":"UK"}'),
('Sydney Opera House', 'Multi-venue performing arts centre', ST_MakePoint(151.2153, -33.8568)::geography, '{"type":"landmark","country":"AU"}'),
('Tokyo Tower', 'Communications and observation tower', ST_MakePoint(139.7454, 35.6586)::geography, '{"type":"landmark","country":"JP"}'),
('Christ the Redeemer', 'Art Deco statue of Jesus Christ', ST_MakePoint(-43.2105, -22.9519)::geography, '{"type":"landmark","country":"BR"}'),
('Taj Mahal', 'Ivory-white marble mausoleum in Agra', ST_MakePoint(78.0421, 27.1751)::geography, '{"type":"landmark","country":"IN"}'),
('Colosseum', 'Ancient amphitheatre in Rome', ST_MakePoint(12.4924, 41.8902)::geography, '{"type":"landmark","country":"IT"}');

-- Seed documents (MongoDB-style)
INSERT INTO documents (collection, data) VALUES
('users', '{"name":"Alice","email":"alice@example.com","age":30,"address":{"city":"New York","zip":"10001"}}'),
('users', '{"name":"Bob","email":"bob@example.com","age":25,"address":{"city":"London","zip":"SW1A 1AA"}}'),
('users', '{"name":"Charlie","email":"charlie@example.com","age":35,"address":{"city":"Tokyo","zip":"100-0001"}}'),
('products', '{"name":"Widget","price":29.99,"tags":["electronics","gadget"],"specs":{"weight":"100g","color":"blue"}}'),
('products', '{"name":"Gizmo","price":49.99,"tags":["electronics","tool"],"specs":{"weight":"200g","color":"red"}}'),
('orders', '{"customer":"Alice","items":[{"product":"Widget","qty":2},{"product":"Gizmo","qty":1}],"total":109.97}');

-- Seed time-series data
INSERT INTO sensor_readings (sensor_id, metric_name, value, recorded_at, tags)
SELECT
    'sensor-' || (i % 5 + 1),
    CASE (i % 3) WHEN 0 THEN 'temperature' WHEN 1 THEN 'humidity' ELSE 'pressure' END,
    20 + random() * 30,
    NOW() - (interval '1 hour' * i),
    jsonb_build_object('location', CASE (i % 3) WHEN 0 THEN 'warehouse-A' WHEN 1 THEN 'warehouse-B' ELSE 'warehouse-C' END)
FROM generate_series(1, 100) AS i;

-- Seed vector documents (random 384-dim vectors for demo purposes)
-- In production, these would come from an embedding model like all-MiniLM-L6-v2
INSERT INTO vector_documents (title, content, embedding)
SELECT
    'Document ' || i,
    'This is sample content for document number ' || i || '. It covers topic ' ||
    CASE (i % 5) WHEN 0 THEN 'artificial intelligence' WHEN 1 THEN 'database systems'
    WHEN 2 THEN 'web development' WHEN 3 THEN 'cloud computing' ELSE 'machine learning' END,
    ('[' || string_agg((random() - 0.5)::text, ',') || ']')::vector(384)
FROM generate_series(1, 20) AS i,
     LATERAL generate_series(1, 384) AS dim
GROUP BY i;

-- Seed cache entries
INSERT INTO cache_entries (key, value, expires_at) VALUES
('session:user-1', '{"userId":1,"role":"admin","theme":"dark"}', NOW() + interval '1 hour'),
('session:user-2', '{"userId":2,"role":"user","theme":"light"}', NOW() + interval '1 hour'),
('rate-limit:api-key-1', '{"remaining":98,"window":"60s"}', NOW() + interval '1 minute'),
('config:feature-flags', '{"darkMode":true,"betaFeatures":false,"maxUploadSize":"10MB"}', NOW() + interval '24 hours');

-- Seed message queue (task queue demo)
INSERT INTO message_queue (queue, payload, status) VALUES
('emails', '{"to":"alice@example.com","subject":"Welcome!","template":"onboarding"}', 'pending'),
('emails', '{"to":"bob@example.com","subject":"Order shipped","template":"shipping"}', 'pending'),
('emails', '{"to":"charlie@example.com","subject":"Password reset","template":"reset"}', 'completed'),
('data-pipeline', '{"task":"import_csv","file":"users_2026.csv","rows":50000}', 'pending'),
('data-pipeline', '{"task":"generate_report","type":"monthly","month":"2026-01"}', 'pending');

-- Seed cron jobs (pg_cron equivalent)
INSERT INTO cron_jobs (job_name, cron_expression, sql_command, status) VALUES
('cleanup-expired-cache', '0 * * * *', 'DELETE FROM cache_entries WHERE expires_at < NOW()', 'scheduled'),
('requeue-stale-messages', '*/5 * * * *', 'UPDATE message_queue SET status = ''pending'', processed_at = NULL WHERE status = ''processing'' AND processed_at < NOW() - interval ''30 seconds''', 'scheduled'),
('vacuum-analyze', '0 3 * * *', 'VACUUM ANALYZE', 'scheduled');

-- Seed employees (org chart for graph traversal)
INSERT INTO employees (id, name, title, manager_id) VALUES
(1, 'Alice',   'CEO',                  NULL),
(2, 'Bob',     'VP Engineering',       1),
(3, 'Carol',   'VP Product',           1),
(4, 'Dave',    'Engineering Manager',  2),
(5, 'Eve',     'Senior Engineer',      4),
(6, 'Frank',   'Engineer',             4),
(7, 'Grace',   'Product Manager',      3),
(8, 'Heidi',   'Designer',             3),
(9, 'Ivan',    'Tech Lead',            2),
(10, 'Judy',   'Engineer',             9);

-- Reset the sequence after explicit ID inserts
SELECT setval('employees_id_seq', (SELECT MAX(id) FROM employees));

-- Add embeddings to articles for hybrid search demo
UPDATE articles SET embedding = (
    SELECT ('[' || string_agg((random() - 0.5)::text, ',') || ']')::vector(384)
    FROM generate_series(1, 384)
)
WHERE embedding IS NULL;
