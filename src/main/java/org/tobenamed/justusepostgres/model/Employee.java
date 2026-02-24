package org.tobenamed.justusepostgres.model;

/**
 * Employee node for graph traversal demos (replaces Neo4j).
 *
 * <h3>Recursive CTE traversal</h3>
 * The {@code manager_id} self-reference creates an adjacency list — the simplest
 * graph representation. {@code WITH RECURSIVE} then performs BFS/DFS traversal
 * in pure SQL:
 * <pre>
 * WITH RECURSIVE org_tree AS (
 *   SELECT id, name, manager_id, 0 AS depth FROM employees WHERE id = ?
 *   UNION ALL
 *   SELECT e.id, e.name, e.manager_id, t.depth + 1
 *   FROM employees e JOIN org_tree t ON e.manager_id = t.id
 * )
 * SELECT * FROM org_tree ORDER BY depth;
 * </pre>
 *
 * For richer graph workloads, Apache AGE adds Cypher query support on top of Postgres.
 */
public record Employee(
    Long id,
    String name,
    String title,
    Long managerId,
    int depth,
    String path
) {}
