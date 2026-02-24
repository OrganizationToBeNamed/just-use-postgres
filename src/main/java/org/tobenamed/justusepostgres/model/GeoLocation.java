package org.tobenamed.justusepostgres.model;

import java.time.Instant;

/**
 * Geospatial entity (replaces specialized GIS databases).
 *
 * Coordinates are stored as PostGIS {@code geography(POINT, 4326)} —
 * SRID 4326 is the WGS84 standard used by GPS.
 * Distance calculations use great-circle (spherical) math automatically.
 */
public record GeoLocation(
    Long id,
    String name,
    String description,
    double latitude,
    double longitude,
    String properties,      // JSONB as String
    double distanceMeters,  // populated in proximity queries
    Instant createdAt
) {
    /** Constructor without distance (for inserts) */
    public GeoLocation(Long id, String name, String description,
                       double latitude, double longitude, String properties, Instant createdAt) {
        this(id, name, description, latitude, longitude, properties, 0.0, createdAt);
    }
}
