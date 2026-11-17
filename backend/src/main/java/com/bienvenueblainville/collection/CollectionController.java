package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import com.bienvenueblainville.municipality.MunicipalityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService service;

    private final MunicipalityService municipalities;

    public CollectionController(CollectionService service, MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    @GetMapping("/upcoming")
    public List<CollectionEvent> upcoming(
            @RequestParam String municipality,
            @RequestParam Sector sector,
            @RequestParam(defaultValue = "14") @Min(1) @Max(62) int days
    ) {
        var city = municipalities.requireBySlug(municipality);
        municipalities.requireSector(city, sector);
        return service.upcoming(city.id(), sector, days);
    }
}

