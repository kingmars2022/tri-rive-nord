package com.tririvenord.notice;

import java.time.LocalDate;

public record SpecialNotice(
        Long id,
        LocalDate startsOn,
        LocalDate endsOn,
        String titleFr,
        String titleEn,
        String titleZh,
        String bodyFr,
        String bodyEn,
        String bodyZh,
        String sourceUrl,
        boolean active,
        // Last, and nullable only in memory: MyBatis maps this record by
        // column order, and code that builds a notice before it is stored
        // (tests, toNotice) does not need to know the city.
        Long municipalityId
) {
    public SpecialNotice(Long id, LocalDate startsOn, LocalDate endsOn, String titleFr, String titleEn,
                         String titleZh, String bodyFr, String bodyEn, String bodyZh, String sourceUrl,
                         boolean active) {
        this(id, startsOn, endsOn, titleFr, titleEn, titleZh, bodyFr, bodyEn, bodyZh, sourceUrl, active, null);
    }
}
