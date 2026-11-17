package com.tririvenord.collection;

import com.tririvenord.common.Sector;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The questions a resident would ask about an unexpected collection day,
 * answered without a database or a clock.
 */
class HolidayShiftTest {
    /** Any one city; these tests are about behaviour, not about which. */
    private static final long MUNI = 1L;


    private static CollectionHoliday holiday(String date, int shiftDays, Sector sector) {
        return new CollectionHoliday(1L,MUNI,  LocalDate.parse(date),
                "Noel", "Christmas Day", "圣诞节", shiftDays, sector, null, true);
    }

    private static CollectionHoliday named(String date, String fr, String en, String zh, Sector sector) {
        return new CollectionHoliday(1L,MUNI,  LocalDate.parse(date), fr, en, zh, 1, sector, null, true);
    }

    @Test
    void anOrdinaryWeekIsLeftAlone() {
        HolidayShift shift = HolidayShift.of(List.of(holiday("2026-12-25", 1, Sector.ALL)));

        HolidayShift.Shifted result = shift.apply(LocalDate.parse("2026-12-18"), Sector.ALL);

        assertThat(result.date()).isEqualTo(LocalDate.parse("2026-12-18"));
        assertThat(result.moved()).isFalse();
    }

    @Test
    void aCollectionOnAHolidayMovesByTheRecordedNumberOfDays() {
        HolidayShift shift = HolidayShift.of(List.of(holiday("2026-12-25", 1, Sector.ALL)));

        HolidayShift.Shifted result = shift.apply(LocalDate.parse("2026-12-25"), Sector.ALL);

        assertThat(result.date()).isEqualTo(LocalDate.parse("2026-12-26"));
        assertThat(result.causes()).singleElement().returns("Christmas Day", CollectionHoliday::nameEn);
    }

    @Test
    void aShiftThatLandsOnAnotherHolidayShiftsAgain() {
        // Christmas on the Friday, Boxing Day closed too: the collection has
        // to clear both, which a single shift would not.
        HolidayShift shift = HolidayShift.of(List.of(
                holiday("2026-12-25", 1, Sector.ALL),
                holiday("2026-12-26", 1, Sector.ALL)));

        HolidayShift.Shifted result = shift.apply(LocalDate.parse("2026-12-25"), Sector.ALL);

        assertThat(result.date()).isEqualTo(LocalDate.parse("2026-12-27"));
        assertThat(result.causes()).hasSize(2);
    }

    @Test
    void aShiftCarriesAcrossTheYearBoundary() {
        HolidayShift shift = HolidayShift.of(List.of(holiday("2026-12-31", 1, Sector.ALL)));

        HolidayShift.Shifted result = shift.apply(LocalDate.parse("2026-12-31"), Sector.ALL);

        assertThat(result.date()).isEqualTo(LocalDate.parse("2027-01-01"));
    }

    @Test
    void aSectorHolidayDoesNotMoveACityWideCollection() {
        // Organics run on one day for the whole territory. Moving them because
        // the north is closed would move them for the south as well.
        HolidayShift shift = HolidayShift.of(List.of(holiday("2026-12-25", 1, Sector.of("north"))));

        HolidayShift.Shifted result = shift.apply(LocalDate.parse("2026-12-25"), Sector.ALL);

        assertThat(result.moved()).isFalse();
        assertThat(result.date()).isEqualTo(LocalDate.parse("2026-12-25"));
    }

    @Test
    void aSectorHolidayMovesThatSectorAndLeavesTheOther() {
        HolidayShift shift = HolidayShift.of(List.of(holiday("2026-12-25", 1, Sector.of("north"))));

        assertThat(shift.apply(LocalDate.parse("2026-12-25"), Sector.of("north")).date())
                .isEqualTo(LocalDate.parse("2026-12-26"));
        assertThat(shift.apply(LocalDate.parse("2026-12-25"), Sector.of("south")).date())
                .isEqualTo(LocalDate.parse("2026-12-25"));
    }

    @Test
    void theLongestShiftWinsWhenTwoRowsShareADate() {
        // A city-wide holiday and a sector-scoped closure on the same day: the
        // collection has to clear the longer of the two, not the first found.
        HolidayShift shift = HolidayShift.of(List.of(
                holiday("2026-12-25", 1, Sector.ALL),
                holiday("2026-12-25", 3, Sector.of("north"))));

        assertThat(shift.apply(LocalDate.parse("2026-12-25"), Sector.of("north")).date())
                .isEqualTo(LocalDate.parse("2026-12-28"));
    }

    @Test
    void anInactiveHolidayMovesNothing() {
        CollectionHoliday cancelled = new CollectionHoliday(1L,MUNI,  LocalDate.parse("2026-12-25"),
                "Noel", "Christmas Day", "圣诞节", 1, Sector.ALL, null, false);

        assertThat(HolidayShift.of(List.of(cancelled))
                .apply(LocalDate.parse("2026-12-25"), Sector.ALL).moved()).isFalse();
    }

    @Test
    void anUnbrokenRunOfHolidaysTerminatesInsteadOfHanging() {
        // Nonsense data - every day closed for a fortnight. The daily job must
        // come back with an answer rather than spin.
        List<CollectionHoliday> everyDay = LocalDate.parse("2026-12-01").datesUntil(LocalDate.parse("2026-12-15"))
                .map(date -> new CollectionHoliday(1L,MUNI,  date, "x", "x", "x", 1, Sector.ALL, null, true))
                .toList();

        HolidayShift.Shifted result = HolidayShift.of(everyDay)
                .apply(LocalDate.parse("2026-12-01"), Sector.ALL);

        assertThat(result.date()).isEqualTo(LocalDate.parse("2026-12-08"));
        assertThat(result.causes()).hasSize(7);
    }

    @Test
    void noHolidaysAtAllIsNotASpecialCase() {
        assertThat(HolidayShift.none().apply(LocalDate.parse("2026-12-25"), Sector.ALL).moved()).isFalse();
    }

    @Test
    void aMovedCollectionExplainsItselfInTheResidentsLanguage() {
        HolidayShift shift = HolidayShift.of(List.of(
                named("2026-12-25", "Noel", "Christmas Day", "圣诞节", Sector.ALL)));

        HolidayShift.Shifted result = shift.apply(LocalDate.parse("2026-12-25"), Sector.ALL);

        assertThat(result.explain("Collecte du jeudi.", CollectionHoliday::nameFr,
                "Collecte déplacée en raison de {holiday}."))
                .isEqualTo("Collecte du jeudi. Collecte déplacée en raison de Noel.");
        assertThat(result.explain(null, CollectionHoliday::nameZh, "因{holiday}顺延。"))
                .isEqualTo("因圣诞节顺延。");
    }

    @Test
    void anUnmovedCollectionKeepsItsNoteExactly() {
        HolidayShift.Shifted result = HolidayShift.none().apply(LocalDate.parse("2026-12-18"), Sector.ALL);

        assertThat(result.explain("Collecte du jeudi.", CollectionHoliday::nameFr, "ignored"))
                .isEqualTo("Collecte du jeudi.");
        assertThat(result.explain(null, CollectionHoliday::nameFr, "ignored")).isNull();
    }
}
