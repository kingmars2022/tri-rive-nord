package com.tririvenord.collection;

import com.tririvenord.collection.dto.CollectionEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.tririvenord.common.Page;
import com.tririvenord.municipality.MunicipalityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/collections")
public class AdminCollectionController {
    private final CollectionService service;
    private final MunicipalityService municipalities;

    public AdminCollectionController(CollectionService service, MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    /**
     * The schedule, a page at a time.
     *
     * <p>{@code size} is capped rather than free: the calendar grows without
     * bound, and an unbounded page size is an unbounded query however
     * politely it is asked for.
     */
    @GetMapping
    public Page<CollectionEvent> list(
            @RequestParam String municipality,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return service.page(municipalities.requireBySlug(municipality).id(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionEvent create(@RequestParam String municipality,
                                  @Valid @RequestBody CollectionEventRequest request) {
        long city = municipalities.requireBySlug(municipality).id();
        municipalities.requireWritableSector(city, request.sector());
        return service.create(city, request);
    }

    /**
     * Checked against the row's own city, not the one in the URL: a row keeps
     * the city it was created for, whichever city the console has switched to
     * since.
     */
    @PutMapping("/{id}")
    public CollectionEvent update(@PathVariable Long id, @Valid @RequestBody CollectionEventRequest request) {
        municipalities.requireWritableSector(service.get(id).municipalityId(), request.sector());
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
