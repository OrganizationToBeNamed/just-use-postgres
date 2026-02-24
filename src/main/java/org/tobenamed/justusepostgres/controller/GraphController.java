package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.Employee;
import org.tobenamed.justusepostgres.service.GraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for graph traversal using recursive CTEs (replaces Neo4j).
 *
 * <h3>How recursive CTEs work</h3>
 * {@code WITH RECURSIVE} starts from a base row and repeatedly joins to find
 * children (BFS) or parents (ancestor walk). This is the SQL equivalent of
 * Neo4j's variable-length path matching: {@code MATCH (a)-[:MANAGES*]->(b)}.
 *
 * <h3>Common use cases</h3>
 * <ul>
 *   <li>Org charts (this demo)</li>
 *   <li>Bill of materials / product assemblies</li>
 *   <li>Category trees / taxonomy</li>
 *   <li>Social network (friends-of-friends)</li>
 *   <li>Dependency graphs</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/graph")
@Tag(name = "Graph Traversal")
public class GraphController {

    private final GraphService service;

    public GraphController(GraphService service) {
        this.service = service;
    }

    /**
     * GET /api/graph/org-tree/{employeeId}
     * Get the full org tree starting from a given employee.
     * Like Neo4j: MATCH (mgr)-[:MANAGES*]->(report) WHERE mgr.id = ?
     */
    @GetMapping("/org-tree/{employeeId}")
    @Operation(summary = "Get org tree", description = "Traverse the full org tree from a given employee downward using recursive CTEs (BFS).")
    public List<Employee> getOrgTree(@Parameter(description = "Root employee ID", example = "1") @PathVariable Long employeeId) {
        return service.getOrgTree(employeeId);
    }

    /**
     * GET /api/graph/ancestors/{employeeId}
     * Get all ancestors (path to the root) for a given employee.
     * Like Neo4j: MATCH (e)-[:REPORTS_TO*]->(root) WHERE e.id = ?
     */
    @GetMapping("/ancestors/{employeeId}")
    @Operation(summary = "Get ancestors", description = "Walk up the org chart to the root. Like Neo4j MATCH (e)-[:REPORTS_TO*]->(root).")
    public List<Employee> getAncestors(@Parameter(description = "Employee ID", example = "6") @PathVariable Long employeeId) {
        return service.getAncestors(employeeId);
    }

    /**
     * GET /api/graph/team-size/{managerId}
     * Count all direct + indirect reports.
     */
    @GetMapping("/team-size/{managerId}")
    @Operation(summary = "Get team size", description = "Count all direct + indirect reports for a manager.")
    public Map<String, Object> getTeamSize(@Parameter(description = "Manager ID", example = "1") @PathVariable Long managerId) {
        int size = service.getTeamSize(managerId);
        return Map.of("managerId", managerId, "teamSize", size);
    }

    /**
     * GET /api/graph/employees
     * List all employees.
     */
    @GetMapping("/employees")
    @Operation(summary = "List all employees", description = "Returns every employee in the org chart.")
    public List<Employee> listAll() {
        return service.listAll();
    }

    /**
     * POST /api/graph/employees
     * Body: { "name": "Alice", "title": "CEO", "managerId": null }
     * Add a new employee to the org chart.
     */
    @PostMapping("/employees")
    @Operation(summary = "Add employee", description = "Add a new node to the org chart graph.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"name\": \"Kate\", \"title\": \"Intern\", \"managerId\": 4}"))))
    public ResponseEntity<Map<String, Object>> addEmployee(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String title = (String) request.get("title");
        Long managerId = request.get("managerId") != null
            ? ((Number) request.get("managerId")).longValue() : null;

        Long id = service.addEmployee(name, title, managerId);
        return ResponseEntity.ok(Map.of("id", id, "name", name, "title", title));
    }
}
