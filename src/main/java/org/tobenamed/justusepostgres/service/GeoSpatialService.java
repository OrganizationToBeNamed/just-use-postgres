package org.tobenamed.justusepostgres.service;

import org.tobenamed.justusepostgres.model.GeoLocation;
import org.tobenamed.justusepostgres.repository.GeoSpatialRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeoSpatialService {

    private final GeoSpatialRepository repo;

    public GeoSpatialService(GeoSpatialRepository repo) {
        this.repo = repo;
    }

    /** Find locations within a radius (meters). */
    public List<GeoLocation> findNearby(double lat, double lng, double radiusMeters, int limit) {
        return repo.findNearby(lat, lng, radiusMeters, limit);
    }

    public Long addLocation(String name, String description, double lat, double lng, String properties) {
        return repo.insert(name, description, lat, lng, properties);
    }
}
