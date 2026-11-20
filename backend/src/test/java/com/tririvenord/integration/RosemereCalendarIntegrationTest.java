package com.tririvenord.integration;

import com.tririvenord.collection.BinColor;
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
 * Rosemere's calendar, against the town's own 2026 municipal calendar
 * (data/municipal-sources/rosemere/2026-11-20/).
 *
 * <p>The expected dates are read off the November and December grids
 * (pages 27 and 29) for the two sectors at either end of the week: sector 1
 * collects on Tuesday, sector 4 on Friday, which is the day Christmas falls
 * on. Organics are on Thursday for every sector. Nothing after 31 December
 * is printed; past it the test checks that V32 carries the rhythm on as it
 * says, and that the one Friday the calendar does not settle - sector 4's
 * garbage due 1 January 2027 - is left out rather than guessed.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class RosemereCalendarIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    /** The day before the calendar was read; V32 sets every rule's mark here. */
    private static final LocalDate LOADED_THROUGH = LocalDate.parse("2026-11-19");

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
        city = municipalities.requireBySlug("rosemere").id();
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

    /** A top-up that sees only this town's rules, at a clock the test picks. */
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

    private List<CollectionEvent> eventsOf(String sector, String from, String through) {
        return events.findUpcoming(city, Sector.of(sector), LocalDate.parse(from), LocalDate.parse(through));
    }

    /** "2026-12-24 recycling" lines, in the order the home page reads them. */
    private List<String> calendarOf(String sector, String from, String through) {
        return eventsOf(sector, from, through).stream()
                .map(event -> event.collectionDate() + " " + event.collectionType())
                .toList();
    }

    @Test
    void sector1_tuesday_isWhatThe2026CalendarPrints() {
        topUpOn("2026-11-20");

        assertThat(calendarOf("secteur-1", "2026-11-20", "2026-12-31")).containsExactly(
                "2026-11-24 recycling",
                "2026-11-26 organic",
                "2026-12-01 garbage",
                "2026-12-08 recycling",
                // December: organics every other Thursday, in recycling weeks.
                "2026-12-10 organic",
                "2026-12-14 bulky",
                "2026-12-15 garbage",
                "2026-12-22 recycling",
                "2026-12-24 organic",
                "2026-12-29 garbage");
    }

    @Test
    void sector4_friday_isWhatThe2026CalendarPrints_withChristmasADayEarlier_andNoGuessForNewYear() {
        topUpOn("2026-11-20");

        assertThat(calendarOf("secteur-4", "2026-11-20", "2027-01-16")).containsExactly(
                "2026-11-20 garbage",
                "2026-11-26 organic",
                "2026-11-27 recycling",
                "2026-12-04 garbage",
                "2026-12-10 organic",
                "2026-12-11 recycling",
                "2026-12-14 bulky",
                "2026-12-18 garbage",
                // Friday 25 December, printed on Thursday 24.
                "2026-12-24 organic",
                "2026-12-24 recycling",
                // Friday 1 January 2027 is not printed: nothing, not a guess.
                "2027-01-07 organic",
                "2027-01-08 recycling",
                "2027-01-15 garbage");
    }

    /** Page 31: "Horaire d'hiver ... decembre 2026 a avril 2027", then weekly. */
    @Test
    void organicsStayEveryOtherWeekToApril2027_thenWeekly() {
        // Far enough on for the 180-day horizon to reach May.
        topUpOn("2027-01-04");

        assertThat(calendarOf("secteur-3", "2027-04-01", "2027-05-20").stream()
                .filter(line -> line.endsWith("organic")).toList())
                .containsExactly(
                        "2027-04-01 organic",
                        "2027-04-15 organic",
                        "2027-04-29 organic",
                        "2027-05-06 organic",
                        "2027-05-13 organic",
                        "2027-05-20 organic");
    }

    @Test
    void garbageGoesInTheGreyBin_andTheNotesSayWhereEachDateCameFrom() {
        topUpOn("2026-11-20");

        assertThat(eventsOf("secteur-2", "2026-11-20", "2027-02-28"))
                .filteredOn(event -> "garbage".equals(event.collectionType().name()))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.binColor()).isEqualTo(BinColor.grey));

        assertThat(eventsOf("secteur-2", "2026-12-01", "2026-12-31"))
                .allSatisfy(event -> assertThat(event.noteFr()).contains("calendrier municipal 2026"));
        assertThat(eventsOf("secteur-2", "2027-01-02", "2027-02-28"))
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.noteFr()).containsAnyOf(
                        "en attendant celui de 2027", "dates prolongees au meme rythme"));
    }

    @Test
    void theTownIsLoaded_withFourCollectionSectors() {
        Municipality rosemere = municipalities.requireBySlug("rosemere");
        assertThat(rosemere.calendarVerified()).isTrue();
        // Its guide is V33; see RosemereGuideIntegrationTest.

        assertThat(municipalities.sectorsOf(rosemere)).hasSize(5);
        assertThat(municipalities.defaultSectorOf(rosemere).isAll()).isFalse();
    }
}
