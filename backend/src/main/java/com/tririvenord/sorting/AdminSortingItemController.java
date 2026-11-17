package com.tririvenord.sorting;

import com.tririvenord.sorting.dto.SortingItemRequest;
import com.tririvenord.sorting.dto.SortingItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tririvenord.municipality.MunicipalityService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sorting-items")
public class AdminSortingItemController {
    private final SortingItemService service;
    private final MunicipalityService municipalities;

    public AdminSortingItemController(SortingItemService service, MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    @GetMapping
    public List<SortingItemResponse> all(@RequestParam String municipality) {
        return service.all(municipalities.requireBySlug(municipality).id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SortingItemResponse create(@RequestParam String municipality,
                                      @Valid @RequestBody SortingItemRequest request) {
        return service.create(municipalities.requireBySlug(municipality).id(), request);
    }

    @PutMapping("/{id}")
    public SortingItemResponse update(@PathVariable Long id, @Valid @RequestBody SortingItemRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
