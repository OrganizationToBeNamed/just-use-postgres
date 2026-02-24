package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.Employee;
import org.tobenamed.justusepostgres.repository.GraphRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for graph traversal using recursive CTEs (replaces Neo4j).
 */
@Service
public class GraphService {

    private final GraphRepository repo;

    public GraphService(GraphRepository repo) {
        this.repo = repo;
    }

    /** Get the full org tree starting from a given employee (BFS). */
    public List<Employee> getOrgTree(Long rootId) {
        return repo.getOrgTree(rootId);
    }

    /** Get all ancestors (path to root) for a given employee. */
    public List<Employee> getAncestors(Long employeeId) {
        return repo.getAncestors(employeeId);
    }

    /** Add a new employee to the org chart. */
    public Long addEmployee(String name, String title, Long managerId) {
        return repo.addEmployee(name, title, managerId);
    }

    /** List all employees. */
    public List<Employee> listAll() {
        return repo.listAll();
    }

    /** Count all direct + indirect reports for a manager. */
    public int getTeamSize(Long managerId) {
        return repo.getTeamSize(managerId);
    }
}
