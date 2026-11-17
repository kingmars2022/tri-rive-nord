package com.tririvenord.notice;

import com.tririvenord.municipality.MunicipalityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {
    private final SpecialNoticeService service;
    private final MunicipalityService municipalities;

    public NoticeController(SpecialNoticeService service, MunicipalityService municipalities) {
        this.service = service;
        this.municipalities = municipalities;
    }

    /**
     * The notices showing today in one city. The city is required, with no
     * fallback, for the same reason the schedule's is: a missing parameter
     * answering with Blainville's notices would look like an answer.
     */
    @GetMapping("/active")
    public List<SpecialNotice> active(@RequestParam String municipality) {
        return service.active(municipalities.requireBySlug(municipality).id());
    }
}
