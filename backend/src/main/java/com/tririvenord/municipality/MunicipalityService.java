package com.tririvenord.municipality;

import com.tririvenord.common.Sector;
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

    /**
     * The city a new account starts on, before the resident has chosen.
     *
     * <p>The first by name rather than a hard-coded slug, so this stays
     * correct if Blainville is ever not the one loaded. It is a starting
     * point, not an answer: the resident changes it in the switcher, and
     * every read is scoped to whatever they chose.
     */
    public Municipality defaultMunicipality() {
        List<Municipality> all = mapper.findAll();
        if (all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No municipality is configured");
        }
        return all.get(0);
    }

    /**
     * Where a new resident starts, before they say which part of town they
     * live in.
     *
     * <p>The first sector that is a real part of the city, which means
     * skipping {@code all}. That code is a wildcard for collections covering
     * the whole territory, not a place anybody lives, and the upcoming query
     * reads {@code sector = ? or sector = 'all'} -- so a resident whose own
     * sector is {@code all} matches only the city-wide collections and never
     * sees the garbage or recycling for either half. An integration test
     * caught this: picking the first row returned {@code all}, which is worse
     * than the hard-coded 'north' it replaced.
     *
     * <p>Falls back to {@code all} only for a city that genuinely has no
     * subdivisions, where it is the right answer rather than a wildcard.
     */
    public Sector defaultSectorOf(Municipality municipality) {
        List<MunicipalitySector> sectors = mapper.findSectors(municipality.id());
        return sectors.stream()
                .map(MunicipalitySector::code)
                .filter(code -> !Sector.ALL.code().equals(code))
                .findFirst()
                .map(Sector::of)
                .orElse(Sector.ALL);
    }

    public Municipality requireBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A municipality is required");
        }
        return mapper.findBySlug(slug.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Unknown municipality: " + slug));
    }

    /** Enough to choose from, few enough to read on a phone. */
    private static final int STREET_RESULTS = 15;

    /**
     * Streets of this city whose name contains {@code query}, best first.
     *
     * <p>Fewer than two characters returns nothing rather than the first
     * fifteen streets: a list that matches everything answers no question.
     */
    public List<MunicipalityStreet> streetsMatching(Municipality municipality, String query) {
        String typed = query == null ? "" : query.trim();
        if (typed.length() < 2) {
            return List.of();
        }
        String escaped = typed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return mapper.findStreets(municipality.id(), escaped, STREET_RESULTS);
    }

    public boolean hasStreetDirectory(Municipality municipality) {
        return mapper.countStreets(municipality.id()) > 0;
    }

    public List<MunicipalitySector> sectorsOf(Municipality municipality) {
        return mapper.findSectors(municipality.id());
    }

    /**
     * Checks that a sector exists in this city before anything queries with it.
     *
     * <p>{@link com.tririvenord.common.Sector} validates the shape of
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
