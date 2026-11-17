package com.tririvenord.sorting;

import com.tririvenord.sorting.dto.SortingItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tririvenord.municipality.MunicipalityService;

import java.util.List;

/**
 * The sorting guide, for residents.
 *
 * <p>This endpoint is why the frontend no longer ships its own copy of the
 * guide. Before it existed, the cards residents read came from a TypeScript
 * file while the assistant and the admin console read MySQL — so an
 * administrator could correct an entry and the page nobody else looks at would
 * change, while the page everybody looks at would not.
 */
@RestController
@RequestMapping("/api/sorting-items")
public class SortingItemController {
    private final SortingItemService service;
    private final MunicipalityService municipalities;

    public SortingItemController(SortingItemService service, MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    @GetMapping
    public List<SortingItemResponse> guide(@RequestParam String municipality) {
        return service.guide(municipalities.requireBySlug(municipality).id());
    }
}
