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
 * Boisbriand's calendar, as V25 loads it - which is NOT a check against the
 * city's 2026 calendar, because that document was not available.
 *
 * <p>Read V25's header before trusting any of this. At the owner's direction
 * the schedule was carried forward from "Calendrier des collectes 2025":
 * recycling every other week in both sectors, garbage with it in sector A and
 * in the other weeks in sector B, organics weekly to November and in the
 * non-recycling weeks from December, collections due on 25 December and
 * 1 January moved a day earlier. The expected lists below are that pattern
 * written out by hand for November and December 2026, so what this test can
 * catch is a migration that does not do what the 2025 calendar describes. It
 * cannot catch the city having changed the pattern for 2026.
 *
 * <p>Two things here are checked against something printed: the phase
 * arithmetic against the 2025 calendar's own recycling weeks, and the
 * excess-waste week against the 2026 collections page.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class BoisbriandCalendarIntegrationTest {

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
        city = municipalities.requireBySlug("boisbriand").id();
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

    @Test
    void sectorA_tuesday_followsThe2025Pattern() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("a-mardi", "2026-11-17", "2026-12-31")).containsExactly(
                "2026-11-17 organic",
                "2026-11-24 garbage",
                "2026-11-24 organic",
                "2026-11-24 recycling",
                // December: organics drop to every other week, in the weeks
                // without recycling.
                "2026-12-01 organic",
                "2026-12-08 garbage",
                "2026-12-08 recycling",
                "2026-12-15 bulky",
                "2026-12-15 organic",
                "2026-12-22 garbage",
                "2026-12-22 recycling",
                "2026-12-29 organic");
    }

    /** The sector the holidays land on, with garbage on the other weeks. */
    @Test
    void sectorB_friday_followsThe2025Pattern_withBothHolidaysMovedADayEarlier() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("b-vendredi", "2026-11-17", "2027-01-02")).containsExactly(
                "2026-11-20 garbage",
                "2026-11-20 organic",
                "2026-11-27 organic",
                "2026-11-27 recycling",
                "2026-12-04 garbage",
                "2026-12-04 organic",
                "2026-12-11 recycling",
                "2026-12-18 bulky",
                "2026-12-18 garbage",
                "2026-12-18 organic",
                // Friday 25 December -> Thursday 24; Friday 1 January -> Thursday 31.
                "2026-12-24 recycling",
                "2026-12-31 garbage",
                "2026-12-31 organic");
    }

    /**
     * The one projection everything rests on: the 2025 calendar prints
     * recycling in both sectors in the weeks of 8 and 22 December 2025, and
     * V25's recycling anchors sit a whole number of fortnights after them.
     */
    @Test
    void theRecyclingWeeksAreInPhaseWithThe2025Calendar() {
        LocalDate printed2025 = LocalDate.parse("2025-12-22");

        List<LocalDate> anchors = jdbc.queryForList(
                "select anchor_date from collection_schedule_rule "
                        + "where municipality_id = ? and collection_type = 'recycling'",
                LocalDate.class, city);

        assertThat(anchors).hasSize(8).allSatisfy(anchor -> {
            LocalDate mondayOfItsWeek = anchor.minusDays(anchor.getDayOfWeek().getValue() - 1);
            assertThat(java.time.temporal.ChronoUnit.DAYS.between(printed2025, mondayOfItsWeek) % 14)
                    .as("recycling anchored on %s", anchor)
                    .isZero();
        });
    }

    /** From the 2026 page: "Du 15 au 18 decembre 2026", on each sector's day. */
    @Test
    void theExcessWasteWeekIsThe2026PagesOwn() {
        assertThat(jdbc.queryForList(
                "select concat(sector, ' ', collection_date) from collection_event "
                        + "where municipality_id = ? and collection_type = 'bulky' order by sector",
                String.class, city))
                .containsExactly(
                        "a-jeudi 2026-12-17", "a-mardi 2026-12-15", "a-mercredi 2026-12-16", "a-vendredi 2026-12-18",
                        "b-jeudi 2026-12-17", "b-mardi 2026-12-15", "b-mercredi 2026-12-16", "b-vendredi 2026-12-18");
    }

    @Test
    void everyNoteSaysWhereTheScheduleCameFrom_andResidentsLandInASector() {
        Municipality boisbriand = municipalities.requireBySlug("boisbriand");
        assertThat(boisbriand.calendarVerified()).isTrue();

        assertThat(jdbc.queryForList(
                "select note_fr from collection_schedule_rule where municipality_id = ?", String.class, city))
                .hasSize(40)
                .allSatisfy(note -> assertThat(note).contains("calendrier 2025"));

        assertThat(municipalities.sectorsOf(boisbriand)).hasSize(9);
        assertThat(municipalities.defaultSectorOf(boisbriand).isAll()).isFalse();
    }
}
