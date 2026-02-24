package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for schemaless document storage using JSONB.
 *
 * <h3>How it replaces MongoDB</h3>
 * <ul>
 *   <li>JSONB stores JSON in decomposed binary format — faster reads than BSON</li>
 *   <li>GIN index on JSONB indexes ALL keys/values automatically</li>
 *   <li>{@code @>} operator: contains check (like MongoDB's $elemMatch)</li>
 *   <li>{@code ->>} operator: extract text value by key (like MongoDB's dot notation)</li>
 *   <li>{@code jsonb_path_query}: SQL/JSON path expressions (like MongoDB's aggregation)</li>
 * </ul>
 *
 * <h3>Advantages over MongoDB</h3>
 * <ul>
 *   <li>JOIN documents with relational tables</li>
 *   <li>Full ACID transactions across multiple documents</li>
 *   <li>No need for a separate "collection" server — just a column value</li>
 * </ul>
 */
@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Find all documents in a collection (like MongoDB's db.collection.find()). */
    public List<Document> findByCollection(String collection) {
        return jdbc.query("""
            SELECT id, collection, data::text, created_at, updated_at
            FROM documents
            WHERE collection = ?
            ORDER BY created_at DESC
            """,
            (rs, rowNum) -> new Document(
                rs.getLong("id"),
                rs.getString("collection"),
                rs.getString("data"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            ),
            collection
        );
    }

    /**
     * Query documents by a JSONB containment filter.
     * Example: filter = '{"address":{"city":"New York"}}' uses the {@code @>} (contains) operator.
     * This uses the GIN index for fast lookups.
     */
    public List<Document> findByFilter(String collection, String jsonFilter) {
        return jdbc.query("""
            SELECT id, collection, data::text, created_at, updated_at
            FROM documents
            WHERE collection = ?
              AND data @> ?::jsonb
            ORDER BY created_at DESC
            """,
            (rs, rowNum) -> new Document(
                rs.getLong("id"),
                rs.getString("collection"),
                rs.getString("data"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            ),
            collection, jsonFilter
        );
    }

    /** Insert a document (like MongoDB's insertOne). */
    public Long insert(String collection, String jsonData) {
        return jdbc.queryForObject("""
            INSERT INTO documents (collection, data)
            VALUES (?, ?::jsonb)
            RETURNING id
            """,
            Long.class,
            collection, jsonData
        );
    }

    /** Update a document by merging JSONB (like MongoDB's $set). */
    public boolean update(Long id, String jsonPatch) {
        return jdbc.update("""
            UPDATE documents
            SET data = data || ?::jsonb,
                updated_at = NOW()
            WHERE id = ?
            """,
            jsonPatch, id
        ) > 0;
    }

    /** Delete a document (like MongoDB's deleteOne). */
    public boolean delete(Long id) {
        return jdbc.update("DELETE FROM documents WHERE id = ?", id) > 0;
    }
}
