package com.bienvenueblainville.municipality;

/**
 * A part of one city that gets its own collection days.
 *
 * <p>Data rather than an enum, because how a city divides itself is a local
 * fact: Blainville splits at boulevard de la Seigneurie, and whether any other
 * city splits at all is not known until its calendar is read. The code
 * {@code all} is a row like any other so that a rule covering the whole
 * territory joins the same way as one covering half of it.
 */
public record MunicipalitySector(
        Long id,
        Long municipalityId,
        String code,
        int position,
        String nameFr,
        String nameEn,
        String nameZh,
        String sourceUrl
) {
}
