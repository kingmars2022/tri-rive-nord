package com.tririvenord.notice;

import java.time.LocalDate;

public record SpecialNotice(
        Long id,
        long municipalityId,
        LocalDate startsOn,
        LocalDate endsOn,
        String titleFr,
        String titleEn,
        String titleZh,
        String bodyFr,
        String bodyEn,
        String bodyZh,
        String sourceUrl,
        boolean active
) {
}
