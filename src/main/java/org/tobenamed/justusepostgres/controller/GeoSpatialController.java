package org.tobenamed.justusepostgres.controller;

import org.tobenamed.justusepostgres.model.GeoLocation;
import org.tobenamed.justusepostgres.service.GeoSpatialService;
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
 * REST API for geospatial queries (replaces specialized GIS APIs).
 *
 * <h3>PostGIS capabilities</h3>
 * <ul>
 *   <li>Proximity search: "find all restaurants within 5km"</li>
 *   <li>Containment: "is this point inside a polygon?"</li>
 *   <li>Routing: pgRouting extension for shortest path</li>
 *   <li>Raster analysis: elevation, satellite imagery</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/geo")
@Tag(name = "Geospatial")
public class GeoSpatialController {

    private final GeoSpatialService service;

    public GeoSpatialController(GeoSpatialService service) {
        this.service = service;
    }

    /**
     * GET /api/geo/nearby?lat=40.7128&amp;lng=-74.0060&amp;radius=50000&amp;limit=10
     *
     * Find locations within {@code radius} meters. Returns results sorted by distance.
     */
    @GetMapping("/nearby")
    @Operation(summary = "Find nearby locations", description = "Like PostGIS ST_DWithin. Finds locations within a radius (meters) sorted by distance.")
    public ResponseEntity<List<GeoLocation>> findNearby(
            @Parameter(description = "Latitude", example = "40.7128") @RequestParam double lat,
            @Parameter(description = "Longitude", example = "-74.0060") @RequestParam double lng,
            @Parameter(description = "Radius in meters", example = "50000") @RequestParam(value = "radius", defaultValue = "10000") double radius,
            @Parameter(description = "Max results", example = "10") @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(service.findNearby(lat, lng, radius, limit));
    }

    @Operation(summary = "Add location", description = "Store a new geo point with metadata.",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = "{\"name\": \"Central Park\", \"description\": \"Urban park in NYC\", \"latitude\": 40.7829, \"longitude\": -73.9654, \"properties\": \"{\\\"type\\\": \\\"park\\\"}\"}"))))
    @PostMapping("/locations")
    public ResponseEntity<Map<String, Long>> addLocation(@RequestBody Map<String, Object> request) {
        Long id = service.addLocation(
            (String) request.get("name"),
            (String) request.get("description"),
            ((Number) request.get("latitude")).doubleValue(),
            ((Number) request.get("longitude")).doubleValue(),
            request.getOrDefault("properties", "{}").toString()
        );
        return ResponseEntity.ok(Map.of("id", id));
    }
}
