package com.tririvenord.municipality.dto;

import java.util.List;

/**
 * One city as the city switcher needs it.
 *
 * <p>{@code calendarVerified} and {@code guideVerified} are part of the
 * payload rather than filtered out server-side, so the switcher can offer a
 * city and still tell a resident that its calendar has not been loaded yet.
 * Hiding unloaded cities would be tidier and less honest: the resident would
 * have no way to tell "we do not cover your city" from "we have not read your
 * city's calendar yet".
 */
public record MunicipalityView(
        String slug,
        String name,
        String sourceUrl,
        boolean calendarVerified,
        boolean guideVerified,
        List<SectorView> sectors
) {
    public record SectorView(String code, String nameFr, String nameEn, String nameZh) {
    }
}
