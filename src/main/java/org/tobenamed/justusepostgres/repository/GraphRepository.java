package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.Employee;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for graph traversal using recursive CTEs (replaces Neo4j).
 *
 * <h3>How it replaces Neo4j</h3>
 * <ul>
 *   <li>Adjacency list model: {@code manager_id} references {@code id} (self-join)</li>
 *   <li>{@code WITH RECURSIVE} performs BFS/DFS traversal in pure SQL</li>
 *   <li>PATH array tracks the traversal route — cycle detection built in (PG 14+)</li>
 *   <li>Works for org charts, bill of materials, category trees, social graphs</li>
 * </ul>
 *
 * <h3>Apache AGE</h3>
 * For Cypher query syntax (like Neo4j), the Apache AGE extension adds a graph
 * engine on top of Postgres. You can write:
 * <pre>
 * SELECT * FROM cypher('my_graph', $$
 *   MATCH (a)-[:MANAGES]->(b) RETURN a.name, b.name
 * $$) AS (manager agtype, report agtype);
 * </pre>
 *
 * <h3>When to still use Neo4j</h3>
 * <ul>
 *   <li>50+ hop traversals on billion-edge graphs</li>
 *   <li>Need for native graph algorithms (PageRank, community detection)</li>
 * </ul>
 */
@Repository
public class GraphRepository {

    private final JdbcTemplate jdbc;

    public GraphRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get org tree starting from a given employee (BFS traversal).
     * Uses WITH RECURSIVE — same concept as Neo4j's variable-length path matching.
     */
    public List<Employee> getOrgTree(Long rootId) {
        return jdbc.query("""
            WITH RECURSIVE org_tree AS (
                -- Base case: start from root
                SELECT id, name, title, manager_id,
                       0 AS depth,
                       name::text AS path
                FROM employees
                WHERE id = ?

                UNION ALL

                -- Recursive: find direct reports
                SELECT e.id, e.name, e.title, e.manager_id,
                       t.depth + 1,
                       t.path || ' → ' || e.name
                FROM employees e
                JOIN org_tree t ON e.manager_id = t.id
                WHERE t.depth < 20  -- safety limit to prevent infinite loops
            )
            SELECT id, name, title, manager_id, depth, path
            FROM org_tree
            ORDER BY path
            """,
            (rs, rowNum) -> new Employee(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("title"),
                rs.getObject("manager_id") != null ? rs.getLong("manager_id") : null,
                rs.getInt("depth"),
                rs.getString("path")
            ),
            rootId
        );
    }

    /**
     * Get all ancestors (path to root) for a given employee.
     * Like Neo4j: MATCH (e)-[:REPORTS_TO*]->(root) WHERE e.id = ?
     */
    public List<Employee> getAncestors(Long employeeId) {
        return jdbc.query("""
            WITH RECURSIVE ancestors AS (
                SELECT id, name, title, manager_id,
                       0 AS depth,
                       name::text AS path
                FROM employees
                WHERE id = ?

                UNION ALL

                SELECT e.id, e.name, e.title, e.manager_id,
                       a.depth + 1,
                       e.name || ' → ' || a.path
                FROM employees e
                JOIN ancestors a ON e.id = a.manager_id
                WHERE a.depth < 20
            )
            SELECT id, name, title, manager_id, depth, path
            FROM ancestors
            ORDER BY depth DESC
            """,
            (rs, rowNum) -> new Employee(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("title"),
                rs.getObject("manager_id") != null ? rs.getLong("manager_id") : null,
                rs.getInt("depth"),
                rs.getString("path")
            ),
            employeeId
        );
    }

    /** Add an employee. */
    public Long addEmployee(String name, String title, Long managerId) {
        return jdbc.queryForObject("""
            INSERT INTO employees (name, title, manager_id)
            VALUES (?, ?, ?)
            RETURNING id
            """,
            Long.class,
            name, title, managerId
        );
    }

    /** List all employees. */
    public List<Employee> listAll() {
        return jdbc.query("""
            SELECT id, name, title, manager_id, 0 AS depth, name::text AS path
            FROM employees
            ORDER BY id
            """,
            (rs, rowNum) -> new Employee(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("title"),
                rs.getObject("manager_id") != null ? rs.getLong("manager_id") : null,
                rs.getInt("depth"),
                rs.getString("path")
            )
        );
    }

    /** Get team size (count of all reports, direct + indirect) for an employee. */
    public int getTeamSize(Long managerId) {
        return jdbc.queryForObject("""
            WITH RECURSIVE reports AS (
                SELECT id FROM employees WHERE manager_id = ?
                UNION ALL
                SELECT e.id FROM employees e JOIN reports r ON e.manager_id = r.id
            )
            SELECT COUNT(*)::int FROM reports
            """,
            Integer.class,
            managerId
        );
    }
}
