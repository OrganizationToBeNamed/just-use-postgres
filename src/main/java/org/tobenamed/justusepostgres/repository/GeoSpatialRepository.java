package org.tobenamed.justusepostgres.repository;

import org.tobenamed.justusepostgres.model.GeoLocation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for geospatial queries using PostGIS.
 *
 * <h3>How it replaces specialized GIS</h3>
 * <ul>
 *   <li>{@code geography} type stores coordinates on a spheroid (not flat plane)</li>
 *   <li>{@code ST_DWithin} uses the GiST spatial index for fast radius queries</li>
 *   <li>{@code ST_Distance} computes great-circle distance in meters</li>
 *   <li>{@code ST_MakePoint} creates POINT geometries from lng/lat</li>
 *   <li>SRID 4326 = WGS84, the same coordinate system GPS uses</li>
 * </ul>
 *
 * PostGIS has been the industry standard since 2001 and supports
 * geometry, geography, raster, topology, and routing.
 */
@Repository
public class GeoSpatialRepository {

    private final JdbcTemplate jdbc;

    public GeoSpatialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Find all locations within {@code radiusMeters} of a point.
     * ST_DWithin uses the spatial index — it does NOT do a table scan.
     */
    public List<GeoLocation> findNearby(double lat, double lng, double radiusMeters, int limit) {
        return jdbc.query("""
            SELECT id, name, description,
                   ST_Y(coordinates::geometry) AS latitude,
                   ST_X(coordinates::geometry) AS longitude,
                   properties::text,
                   ST_Distance(coordinates, ST_MakePoint(?, ?)::geography) AS distance_meters,
                   created_at
            FROM geo_locations
            WHERE ST_DWithin(coordinates, ST_MakePoint(?, ?)::geography, ?)
            ORDER BY distance_meters
            LIMIT ?
            """,
            (rs, rowNum) -> new GeoLocation(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("properties"),
                rs.getDouble("distance_meters"),
                rs.getTimestamp("created_at").toInstant()
            ),
            lng, lat,   // ST_MakePoint takes (lng, lat) — note the order!
            lng, lat,
            radiusMeters,
            limit
        );
    }

    /** Insert a new location. */
    public Long insert(String name, String description, double lat, double lng, String properties) {
        return jdbc.queryForObject("""
            INSERT INTO geo_locations (name, description, coordinates, properties)
            VALUES (?, ?, ST_MakePoint(?, ?)::geography, ?::jsonb)
            RETURNING id
            """,
            Long.class,
            name, description, lng, lat, properties
        );
    }
}
