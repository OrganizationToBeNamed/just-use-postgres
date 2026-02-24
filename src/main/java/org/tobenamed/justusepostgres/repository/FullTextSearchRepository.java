package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for full-text search using Postgres built-in tsvector/tsquery.
 *
 * <h3>How it replaces Elasticsearch</h3>
 * <ul>
 *   <li>{@code to_tsvector()} parses text into lexemes (stemmed tokens) — same as ES analyzers</li>
 *   <li>{@code to_tsquery()} parses search terms with boolean operators (&amp;, |, !)</li>
 *   <li>{@code ts_rank()} implements BM25-style TF-IDF ranking — SAME algorithm as ES</li>
 *   <li>{@code ts_headline()} generates highlighted snippets — like ES highlighting</li>
 *   <li>GIN index on tsvector = inverted index — same structure as ES/Lucene</li>
 * </ul>
 *
 * <h3>Fuzzy search with pg_trgm</h3>
 * <ul>
 *   <li>{@code similarity()} computes trigram overlap (0.0 to 1.0)</li>
 *   <li>Useful for typo-tolerant / "did you mean?" queries</li>
 * </ul>
 */
@Repository
public class FullTextSearchRepository {

    private final JdbcTemplate jdbc;

    public FullTextSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * BM25-ranked full-text search with highlighted snippets.
     * The query string supports natural language: 'postgres full text search'.
     * {@code websearch_to_tsquery} converts natural language to boolean tsquery automatically.
     */
    public List<SearchResult> search(String query, int limit) {
        return jdbc.query("""
            SELECT id, title,
                   ts_headline('english', body, websearch_to_tsquery('english', ?),
                               'StartSel=<b>, StopSel=</b>, MaxWords=35, MinWords=15') AS headline,
                   ts_rank(tsv, websearch_to_tsquery('english', ?)) AS rank,
                   created_at
            FROM articles
            WHERE tsv @@ websearch_to_tsquery('english', ?)
            ORDER BY rank DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new SearchResult(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("headline"),
                rs.getDouble("rank"),
                rs.getTimestamp("created_at").toInstant()
            ),
            query, query, query, limit
        );
    }

    /**
     * Fuzzy search using trigram similarity (pg_trgm).
     * Finds titles even with typos: "postgre" matches "PostgreSQL".
     */
    public List<SearchResult> fuzzySearch(String query, double threshold, int limit) {
        return jdbc.query("""
            SELECT id, title, body AS headline,
                   similarity(title, ?) AS rank,
                   created_at
            FROM articles
            WHERE similarity(title, ?) > ?
            ORDER BY rank DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new SearchResult(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("headline"),
                rs.getDouble("rank"),
                rs.getTimestamp("created_at").toInstant()
            ),
            query, query, threshold, limit
        );
    }

    /** Insert a new article (tsvector is auto-generated via GENERATED ALWAYS column). */
    public Long insert(String title, String body) {
        return jdbc.queryForObject("""
            INSERT INTO articles (title, body)
            VALUES (?, ?)
            RETURNING id
            """,
            Long.class,
            title, body
        );
    }
}
