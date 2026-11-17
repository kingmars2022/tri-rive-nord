package com.bienvenueblainville.municipality;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Resolves the city a request is about.
 *
 * <p>Every read of a local rule goes through here first, and an unknown slug
 * is a 404 rather than a fallback to Blainville. Falling back would mean a
 * typo in the URL quietly answering with another city's rules, which is the
 * one outcome this whole layer exists to prevent.
 */
@Service
public class MunicipalityService {
    private final MunicipalityMapper mapper;

    public MunicipalityService(MunicipalityMapper mapper) {
        this.mapper = mapper;
    }

    public List<Municipality> findAll() {
        return mapper.findAll();
    }

    public Municipality requireBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A municipality is required");
        }
        return mapper.findBySlug(slug.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown municipality: " + slug));
    }

    public List<MunicipalitySector> sectorsOf(Municipality municipality) {
        return mapper.findSectors(municipality.id());
    }
}
