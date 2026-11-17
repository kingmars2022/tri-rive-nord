package com.tririvenord.collection;

import com.tririvenord.common.Sector;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which dates a pattern produces, without a database: the arithmetic every
 * generated collection rests on.
 */
class CollectionScheduleRuleTest {

    private static CollectionScheduleRule rule(String anchor, int weeks, String validUntil) {
        return new CollectionScheduleRule(1L, 1L, Sector.ALL, CollectionType.organic, BinColor.brown,
                LocalDate.parse(anchor), weeks, LocalDate.parse("2026-11-16"),
                null, null, null, null, true,
                validUntil == null ? null : LocalDate.parse(validUntil));
    }

    private static java.util.List<LocalDate> dates(String... isoDates) {
        return java.util.Arrays.stream(isoDates).map(LocalDate::parse).toList();
    }

    @Test
    void aRuleWithNoEndKeepsGoing() {
        assertThat(rule("2026-11-02", 1, null)
                .occurrencesAfter(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-12-14")))
                .isEqualTo(dates("2026-11-23", "2026-11-30", "2026-12-07", "2026-12-14"));
    }

    @Test
    void aRuleStopsOnItsLastDayInclusive() {
        // Sainte-Therese's weekly organics end with November; 30 November is
        // still a collection, 7 December is not.
        assertThat(rule("2026-11-02", 1, "2026-11-30")
                .occurrencesAfter(LocalDate.parse("2026-11-16"), LocalDate.parse("2026-12-31")))
                .isEqualTo(dates("2026-11-23", "2026-11-30"));
    }

    @Test
    void aRuleThatHasAlreadyEndedProducesNothing() {
        assertThat(rule("2026-11-02", 1, "2026-11-30")
                .occurrencesAfter(LocalDate.parse("2026-12-01"), LocalDate.parse("2027-06-01")))
                .isEmpty();
    }

    @Test
    void aRuleThatStartsLaterBeginsAtItsAnchor_notBeforeIt() {
        // Which is why every V22 anchor is the first printed collection on or
        // after the day the calendar was read: an anchor later than that
        // silently drops the collections in between.
        assertThat(rule("2026-12-14", 2, "2027-03-31")
                .occurrencesAfter(LocalDate.parse("2026-11-16"), LocalDate.parse("2027-01-31")))
                .isEqualTo(dates("2026-12-14", "2026-12-28", "2027-01-11", "2027-01-25"));
    }
}
