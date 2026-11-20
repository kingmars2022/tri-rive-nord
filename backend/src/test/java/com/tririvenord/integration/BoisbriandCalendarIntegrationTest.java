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
 * Boisbriand's calendar, against the city's own "Calendrier des collectes
 * 2026" (data/municipal-sources/boisbriand/2026-11-20/).
 *
 * <p>V25 loaded the schedule by carrying the 2025 calendar's pattern forward,
 * before the 2026 one was in hand; V31 checked it against the 2026 calendar
 * when it arrived and changed only what residents are told. The expected
 * dates below, from 17 November to 1 January, are read off pages 3 and 4 of
 * the 2026 PDF for the two zones they cover: sector A on Tuesday and sector B
 * on Friday, which is the day both holidays fall on.
 *
 * <p>After 1 January 2027 nothing is printed yet. V31 carries the same
 * rhythm on and says so in every note; that part is checked for being what
 * V31 says it is, not against a document.
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
    void sectorA_tuesday_isWhatThe2026CalendarPrints() {
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
    void sectorB_friday_isWhatThe2026CalendarPrints_withBothHolidaysMovedADayEarlier() {
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
     * January 2027 is not printed yet: the same rhythm carries on, sector A's
     * Tuesday recycling and garbage a fortnight after 22 December and winter
     * organics a fortnight after 29 December, and the notes say where the
     * dates came from on each side of the year.
     */
    @Test
    void january2027CarriesTheRhythmOn_andTheNotesSaySo() {
        topUpOn("2026-11-17");

        assertThat(calendarOf("a-mardi", "2027-01-01", "2027-01-31")).containsExactly(
                "2027-01-05 garbage",
                "2027-01-05 recycling",
                "2027-01-12 organic",
                "2027-01-19 garbage",
                "2027-01-19 recycling",
                "2027-01-26 organic");

        assertThat(events.findUpcoming(city, Sector.of("a-mardi"),
                LocalDate.parse("2026-12-22"), LocalDate.parse("2026-12-22")))
                .allSatisfy(event -> assertThat(event.noteFr()).endsWith("D'apres le calendrier 2026 de la Ville."));
        assertThat(events.findUpcoming(city, Sector.of("a-mardi"),
                LocalDate.parse("2027-01-05"), LocalDate.parse("2027-01-05")))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.noteFr()).contains("en attendant celui de 2027"));
    }

    /**
     * The 2026 calendar prints recycling in both sectors in the week of
     * 21 December 2026. Every recycling rule, the 2026 ones and their 2027
     * continuations, sits a whole number of fortnights from it.
     */
    @Test
    void theRecyclingWeeksAreInPhaseWithThe2026Calendar() {
        LocalDate printed2026 = LocalDate.parse("2026-12-21");

        List<LocalDate> anchors = jdbc.queryForList(
                "select anchor_date from collection_schedule_rule "
                        + "where municipality_id = ? and collection_type = 'recycling'",
                LocalDate.class, city);

        assertThat(anchors).hasSize(16).allSatisfy(anchor -> {
            LocalDate mondayOfItsWeek = anchor.minusDays(anchor.getDayOfWeek().getValue() - 1);
            assertThat(java.time.temporal.ChronoUnit.DAYS.between(printed2026, mondayOfItsWeek) % 14)
                    .as("recycling anchored on %s", anchor)
                    .isZero();
        });
    }

    /** Printed in both sectors' December, 15-18, on each sector's day. */
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

        // Up to the 1 January 2027 occurrence, the 2026 calendar's own words;
        // after it, the carried-on rhythm, said as such. Nothing says 2025.
        assertThat(jdbc.queryForList(
                "select note_fr from collection_schedule_rule "
                        + "where municipality_id = ? and valid_until <= date '2027-01-01'", String.class, city))
                .hasSize(32)
                .allSatisfy(note -> assertThat(note).endsWith("D'apres le calendrier 2026 de la Ville."));
        assertThat(jdbc.queryForList(
                "select note_fr from collection_schedule_rule "
                        + "where municipality_id = ? and anchor_date > date '2027-01-01'", String.class, city))
                .hasSize(32)
                .allSatisfy(note -> assertThat(note).contains("en attendant celui de 2027"));
        assertThat(jdbc.queryForList(
                "select concat(note_fr, note_en, note_zh) from collection_schedule_rule where municipality_id = ?",
                String.class, city))
                .hasSize(64)
                .noneSatisfy(notes -> assertThat(notes).contains("2025"));

        assertThat(municipalities.sectorsOf(boisbriand)).hasSize(9);
        assertThat(municipalities.defaultSectorOf(boisbriand).isAll()).isFalse();
    }
}
