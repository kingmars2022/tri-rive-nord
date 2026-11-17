package com.tririvenord.collection;

import com.tririvenord.common.Sector;

import java.time.LocalDate;

/**
 * A day the city does not collect, and how far collection moves because of it.
 *
 * @param shiftDays how many days a collection landing on {@code holidayDate}
 *                  moves: forward when positive, earlier when negative, never
 *                  zero - enforced by a CHECK constraint and again in
 *                  {@link HolidayShift}
 * @param sector    which sectors observe it; {@code all} is the normal case
 */
public record CollectionHoliday(
        Long id,
        Long municipalityId,
        LocalDate holidayDate,
        String nameFr,
        String nameEn,
        String nameZh,
        int shiftDays,
        Sector sector,
        String sourceUrl,
        boolean active
) {
    /**
     * Whether this holiday moves a collection belonging to {@code ruleSector}.
     *
     * <p>A sector-scoped holiday deliberately does <em>not</em> move a
     * city-wide collection. Organics run on one day for the whole territory,
     * so "the north has a holiday" cannot move them without moving the south
     * along with it — a shift that would be wrong for half the city. An
     * exception that really applies everywhere is recorded as {@code all},
     * which is what a statutory holiday is.
     */
    public boolean applies(Sector ruleSector) {
        // equals, not ==: Sector stopped being an enum when sectors became
        // per-city data, so identity comparison would now be false for two
        // values that read the same. This is the one place in the codebase
        // that compared sectors rather than passing them through, which is
        // why it is the one place the change could have gone wrong silently.
        return sector.isAll() || sector.equals(ruleSector);
    }
}
