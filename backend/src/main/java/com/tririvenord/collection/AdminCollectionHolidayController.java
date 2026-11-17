package com.tririvenord.collection;

import com.tririvenord.collection.dto.CollectionHolidayRequest;
import jakarta.validation.Valid;
import com.tririvenord.municipality.MunicipalityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Closed days, for administrators.
 *
 * <p>Not paginated, unlike the schedule: a municipal year has a handful of
 * these, and the list is meant to be read whole so somebody can see at a
 * glance what the calendar already knows about.
 */
@RestController
@RequestMapping("/api/admin/collection-holidays")
public class AdminCollectionHolidayController {
    private final CollectionHolidayService service;
    private final MunicipalityService municipalities;

    public AdminCollectionHolidayController(CollectionHolidayService service,
                                            MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    @GetMapping
    public List<CollectionHoliday> list(@RequestParam String municipality) {
        return service.list(municipalities.requireBySlug(municipality).id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionHoliday create(@RequestParam String municipality,
                                    @Valid @RequestBody CollectionHolidayRequest request) {
        long city = municipalities.requireBySlug(municipality).id();
        municipalities.requireWritableSector(city, request.sector());
        return service.create(city, request);
    }

    /** Checked against the holiday's own city; see AdminCollectionController. */
    @PutMapping("/{id}")
    public CollectionHoliday update(@PathVariable Long id, @Valid @RequestBody CollectionHolidayRequest request) {
        municipalities.requireWritableSector(service.get(id).municipalityId(), request.sector());
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
