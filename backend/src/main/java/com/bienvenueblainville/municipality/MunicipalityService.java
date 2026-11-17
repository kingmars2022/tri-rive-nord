package com.bienvenueblainville.municipality;

import com.bienvenueblainville.common.Sector;
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

    /**
     * Checks that a sector exists in this city before anything queries with it.
     *
     * <p>{@link com.bienvenueblainville.common.Sector} validates the shape of
     * a code and deliberately not its membership, because whether
     * {@code north} exists depends on which city is being asked. This is
     * where the city is known, so this is where that check belongs.
     *
     * <p>Without it, asking Rosemere for its northern sector returns an empty
     * list with a 200, which reads as "no collections this fortnight" when
     * what happened is that the sector does not exist. An empty success is
     * the worst of the available answers: a resident cannot tell it from a
     * quiet week.
     */
    public void requireSector(Municipality municipality, Sector sector) {
        boolean known = mapper.findSectors(municipality.id()).stream()
                .anyMatch(s -> s.code().equals(sector.code()));
        if (!known) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "%s has no sector '%s'".formatted(municipality.name(), sector.code()));
        }
    }
}
