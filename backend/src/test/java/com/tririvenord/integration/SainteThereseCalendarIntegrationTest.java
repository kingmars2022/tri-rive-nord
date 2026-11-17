package com.tririvenord.integration;

import com.tririvenord.collection.CollectionCalendarTopUp;
import com.tririvenord.collection.CollectionEvent;
import com.tririvenord.collection.CollectionEventMapper;
import com.tririvenord.collection.CollectionHolidayMapper;
import com.tririvenord.collection.CollectionScheduleRule;
import com.tririvenord.collection.CollectionScheduleRuleMapper;
import com.tririvenord.common.Sector;
import com.tririvenord.municipality.Municipality;
import com.tririvenord.municipality.MunicipalityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sainte-Therese's calendar, checked against the city's printed calendar
 * rather than against the rules it was loaded from.
 *
 * <p>That distinction is the whole reason this test exists. Blainville's
 * calendar printed the wrong bin for weeks while every test passed, because
 * every test asked the database whether it agreed with itself (see V15). So
 * the expectations below are not computed from anchors and intervals: each
 * line was copied off the zone's own grid in "Calendrier des collectes 2026"
 * (archived under data/municipal-sources/sainte-therese/2026-11-17/), for
 * every collection from the day the calendar was read to the end of the year.
 * If V22 is wrong about a single bin, a weekday, a fortnight or a holiday,
 * one of these lists stops matching.
 *
 * <p>Only Sainte-Therese's rules are driven, through a filtered view of the
 * rule mapper, so the other cities' calendars are neither generated nor
 * touched, and the city is put back exactly as V22 left it afterwards.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class SainteThereseCalendarIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    /** The day before the calendar was read; V22 sets every rule's mark here. */
    private static final LocalDate LOADED_THROUGH = LocalDate.parse("2026-11-16");

    @Autowired
    private CollectionScheduleRuleMapper rules;
    @Autowired
    private CollectionEventMapper events;
    @Autowired
    private CollectionHolidayMapper holidays;
    @Autowired
    private MunicipalityService municipalities;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private JdbcTemplate jdbc;

    private long city;

    @BeforeEach
    void freshFromTheMigration() {
        city = municipalities.requireBySlug("sainte-therese").id();
        resetToTheMigratedState();
    }

    @AfterEach
    void putItBack() {
        resetToTheMigratedState();
    }

    private void resetToTheMigratedState() {
        jdbc.update("delete from collection_event where municipality_id = ? and auto_generated = true", city);
        jdbc.update("update collection_schedule_rule set generated_through = ? where municipality_id = ?",
                LOADED_THROUGH, city);
    }

    /** A top-up that sees only this city's rules, at a clock the test picks. */
    private void topUpOn(String today) {
        CollectionScheduleRuleMapper onlyThisCity = new CollectionScheduleRuleMapper() {
            @Override
            public List<CollectionScheduleRule> findActive() {
                return rules.findActive().stream()
                        .filter(rule -> rule.municipalityId() == city)
                        .toList();
            }

            @Override
            public void advanceGeneratedThrough(Long id, LocalDate generatedThrough) {
                rules.advanceGeneratedThrough(id, generatedThrough);
            }

            @Override
            public void rewindGeneratedThrough(long municipalityId, LocalDate generatedThrough) {
                rules.rewindGeneratedThrough(municipalityId, generatedThrough);
            }
        };
        Clock clock = Clock.fixed(
                ZonedDateTime.of(LocalDate.parse(today).atStartOfDay(), ZONE).toInstant(), ZONE);
        new CollectionCalendarTopUp(onlyThisCity, holidays, events, cacheManager, clock, true, 180).topUp();
    }

    /** "2026-12-24 garbage" lines, in the order the home page reads them. */
    private List<String> calendarOf(String zone, String from, String through) {
        return events.findUpcoming(city, Sector.of(zone), LocalDate.parse(from), LocalDate.parse(through))
                .stream()
                .map(event -> event.collectionDate() + " " + event.collectionType())
                .toList();
    }

    // Every zone shares these, from the "pour toutes les zones" Mondays:
    // organics weekly through 30 November, then fortnightly (14 and 28
    // December, with nothing on 7 or 21), and encombrants on 7 December.

    @Test
    void zoneA_tuesdays_matchThePrintedGrid() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("a", "2026-11-17", "2026-12-31")).containsExactly(
                "2026-11-17 recycling",
                "2026-11-23 organic",
                "2026-11-24 garbage",
                "2026-11-30 organic",
                "2026-12-01 recycling",
                "2026-12-07 bulky",
                "2026-12-08 garbage",
                "2026-12-14 organic",
                "2026-12-15 recycling",
                "2026-12-22 garbage",
                "2026-12-28 organic",
                "2026-12-29 recycling");
    }

    @Test
    void zoneB_wednesdays_matchThePrintedGrid() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("b", "2026-11-17", "2026-12-31")).containsExactly(
                "2026-11-18 recycling",
                "2026-11-23 organic",
                "2026-11-25 garbage",
                "2026-11-30 organic",
                "2026-12-02 recycling",
                "2026-12-07 bulky",
                "2026-12-09 garbage",
                "2026-12-14 organic",
                "2026-12-16 recycling",
                "2026-12-23 garbage",
                "2026-12-28 organic",
                "2026-12-30 recycling");
    }

    @Test
    void zoneC_thursdays_matchThePrintedGrid_andChristmasEveIsAnOrdinaryDay() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("c", "2026-11-17", "2026-12-31")).containsExactly(
                "2026-11-19 recycling",
                "2026-11-23 organic",
                "2026-11-26 garbage",
                "2026-11-30 organic",
                "2026-12-03 recycling",
                "2026-12-07 bulky",
                "2026-12-10 garbage",
                "2026-12-14 organic",
                "2026-12-17 recycling",
                "2026-12-24 garbage",
                "2026-12-28 organic",
                "2026-12-31 recycling");
    }

    /**
     * The zone the holidays land on. The grid prints Friday 25 December empty
     * and the garbage collection on Thursday 24; and the recycling due on
     * Friday 1 January 2027 on Thursday 31 December.
     */
    @Test
    void zoneD_fridays_matchThePrintedGrid_withBothHolidaysMovedADayEarlier() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("d", "2026-11-17", "2027-01-02")).containsExactly(
                "2026-11-20 recycling",
                "2026-11-23 organic",
                "2026-11-27 garbage",
                "2026-11-30 organic",
                "2026-12-04 recycling",
                "2026-12-07 bulky",
                "2026-12-11 garbage",
                "2026-12-14 organic",
                "2026-12-18 recycling",
                "2026-12-24 garbage",
                "2026-12-28 organic",
                "2026-12-31 recycling");
    }

    @Test
    void aMovedCollectionSaysWhyItMoved() {
        topUpOn("2026-11-17");

        CollectionEvent christmasEve = events.findUpcoming(city, Sector.of("d"),
                        LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-24"))
                .getFirst();

        assertThat(christmasEve.noteEn()).contains("Christmas Day");
        assertThat(christmasEve.noteFr()).contains("Noel");
    }

    /**
     * Beyond the printed calendar. These come from the standing rule on the
     * city's page, "tous les lundis d'avril a novembre et un lundi sur deux
     * de decembre a mars", not from a grid, and are the dates to re-check
     * first when the city publishes its 2027 calendar.
     */
    @Test
    void organicsGoBackToWeeklyInApril2027() {
        topUpOn("2027-03-01");

        assertThat(calendarOf("a", "2027-03-01", "2027-04-30"))
                .filteredOn(line -> line.endsWith("organic"))
                .containsExactly(
                        "2027-03-08 organic",
                        "2027-03-22 organic",
                        "2027-04-05 organic",
                        "2027-04-12 organic",
                        "2027-04-19 organic",
                        "2027-04-26 organic");
    }

    @Test
    void theCityIsMarkedLoaded_andAResidentLandsInAZone_notOnTheWildcard() {
        Municipality sainteTherese = municipalities.requireBySlug("sainte-therese");

        assertThat(sainteTherese.calendarVerified()).isTrue();
        assertThat(sainteTherese.guideVerified())
                .as("the sorting guide is a separate document V22 did not load")
                .isFalse();
        assertThat(municipalities.sectorsOf(sainteTherese))
                .extracting(sector -> sector.code())
                .containsExactly("all", "a", "b", "c", "d");
        // "all" would match only the city-wide Mondays and hide every
        // garbage and recycling collection - the defect docs/loading-a-city.md
        // warns about.
        assertThat(municipalities.defaultSectorOf(sainteTherese).isAll()).isFalse();
    }
}
