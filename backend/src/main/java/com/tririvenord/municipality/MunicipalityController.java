package com.tririvenord.municipality;

import com.tririvenord.municipality.dto.MunicipalityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/municipalities")
public class MunicipalityController {
    private final MunicipalityService service;

    public MunicipalityController(MunicipalityService service) {
        this.service = service;
    }

    @GetMapping
    public List<MunicipalityView> list() {
        return service.findAll().stream().map(this::toView).toList();
    }

    @GetMapping("/{slug}")
    public MunicipalityView one(@PathVariable String slug) {
        return toView(service.requireBySlug(slug));
    }

    /**
     * "Which sector is my street in?" for cities that assign sectors by
     * street. Public, like the rest of this resource: the table is printed in
     * the city's own calendar.
     */
    @GetMapping("/{slug}/streets")
    public List<MunicipalityView.StreetView> streets(@PathVariable String slug,
                                                     @RequestParam(name = "q", defaultValue = "") String query) {
        return service.streetsMatching(service.requireBySlug(slug), query).stream()
                .map(street -> new MunicipalityView.StreetView(street.name(), street.sectorCode()))
                .toList();
    }

    private MunicipalityView toView(Municipality municipality) {
        List<MunicipalityView.SectorView> sectors = service.sectorsOf(municipality).stream()
                .map(s -> new MunicipalityView.SectorView(
                        s.code(), s.nameFr(), s.nameEn(), s.nameZh()))
                .toList();
        return new MunicipalityView(
                municipality.slug(),
                municipality.name(),
                municipality.sourceUrl(),
                municipality.calendarVerified(),
                municipality.guideVerified(),
                sectors,
                service.hasStreetDirectory(municipality));
    }
}
