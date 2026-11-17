package com.tririvenord.notice;

import com.tririvenord.municipality.MunicipalityService;
import com.tririvenord.notice.dto.NoticeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notices")
public class AdminNoticeController {
    private final SpecialNoticeService service;
    private final MunicipalityService municipalities;

    public AdminNoticeController(SpecialNoticeService service, MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    @GetMapping
    public List<SpecialNotice> all(@RequestParam String municipality) {
        return service.all(cityId(municipality));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialNotice create(@RequestParam String municipality, @Valid @RequestBody NoticeRequest request) {
        return service.create(cityId(municipality), request);
    }

    /**
     * Required here as well as on create: it is checked against the notice's
     * own city, so an edit form left open across a city switch is refused
     * rather than editing a notice the administrator is no longer looking at.
     */
    @PutMapping("/{id}")
    public SpecialNotice update(@RequestParam String municipality, @PathVariable Long id,
                                @Valid @RequestBody NoticeRequest request) {
        return service.update(cityId(municipality), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String municipality, @PathVariable Long id) {
        service.delete(cityId(municipality), id);
    }

    private long cityId(String slug) {
        return municipalities.requireBySlug(slug).id();
    }
}
