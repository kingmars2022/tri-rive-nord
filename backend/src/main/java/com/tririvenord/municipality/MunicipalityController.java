package com.tririvenord.municipality;

import com.tririvenord.municipality.dto.MunicipalityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
                sectors);
    }
}
