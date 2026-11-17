package com.tririvenord.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tririvenord.collection.CollectionCalendarTopUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administering closed days, end to end.
 *
 * <p>The assertion that matters is not that a row was written — it is that the
 * calendar moved. {@code CollectionCalendarTopUp} only generates after each
 * rule's high-water mark, so a holiday saved for a date already materialized
 * would be stored and then quietly ignored: the screen would say saved and the
 * resident would still be told to put a bin out on a day nobody comes. Every
 * test here drives the HTTP endpoint and then reads the collection calendar
 * back.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CollectionHolidayAdminIntegrationTest {
    /** Blainville, the one city with data loaded. */
    private static final long MUNI = 1L;
    private static final String CITY = "blainville";


    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        // Left ON, unlike the calendar test: these assertions are about what
        // happens to a calendar that has already been materialized, so the
        // startup top-up is the precondition rather than interference.
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CollectionCalendarTopUp topUp;

    /**
     * Every test here deliberately rewrites the shared calendar, so each one
     * starts and ends by putting it back. Without this they interfere with one
     * another and with any other class that reads the schedule - the failure
     * looks exactly like a broken feature, which cost an hour to rule out
     * once already.
     */
    @BeforeEach
    @AfterEach
    void restoreTheCalendar() {
        jdbc.update("delete from collection_holiday where source_url = 'test://admin-holiday'");
        jdbc.update("delete from collection_event where source_url = 'test://admin-holiday'");
        jdbc.update("delete from collection_event where auto_generated = true and collection_date >= current_date");
        jdbc.update("update collection_schedule_rule set generated_through = date_sub(current_date, interval 1 day) "
                + "where generated_through > date_sub(current_date, interval 1 day)");
        topUp.topUp();
    }

    private Map<String, Object> request(LocalDate date, int shiftDays) {
        Map<String, Object> body = new HashMap<>();
        body.put("holidayDate", date.toString());
        body.put("nameFr", "Jour test");
        body.put("nameEn", "Test holiday");
        body.put("nameZh", "测试假日");
        body.put("shiftDays", shiftDays);
        body.put("sector", "all");
        body.put("sourceUrl", "test://admin-holiday");
        body.put("active", true);
        return body;
    }

    /** The next organics Thursday the generated calendar already holds. */
    private LocalDate anAlreadyGeneratedOrganicsDate() {
        List<LocalDate> dates = jdbc.queryForList(
                "select collection_date from collection_event "
                        + "where municipality_id = (select id from municipality where slug = ?) "
                        + "and collection_type = 'organic' and collection_date > date_add(current_date, interval 21 day) "
                        + "order by collection_date asc limit 1",
                LocalDate.class, CITY);
        assertThat(dates).as("the calendar should already be materialized this far out").isNotEmpty();
        return dates.getFirst();
    }

    private boolean collectionExistsOn(LocalDate date) {
        Integer count = jdbc.queryForObject(
                // The holiday is this city's, so only this city's collections
                // can answer whether it moved one: another city collecting
                // organics on the same day is not a bug in this one.
                "select count(*) from collection_event where municipality_id = "
                        + "(select id from municipality where slug = ?) "
                        + "and collection_type = 'organic' and collection_date = ?",
                Integer.class, CITY, date);
        return count != null && count > 0;
    }

    @Test
    void addingAHolidayMovesACollectionThatWasAlreadyGenerated() throws Exception {
        LocalDate closed = anAlreadyGeneratedOrganicsDate();
        assertThat(collectionExistsOn(closed)).as("precondition: the collection is there to move").isTrue();

        mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(closed, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nameZh").value("测试假日"));

        assertThat(collectionExistsOn(closed)).as("the closed day is now empty").isFalse();
        assertThat(collectionExistsOn(closed.plusDays(1))).as("and the collection moved to the next day").isTrue();
    }

    @Test
    void deletingAHolidayPutsTheCollectionBack() throws Exception {
        LocalDate closed = anAlreadyGeneratedOrganicsDate();

        String created = mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(closed, 1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();
        assertThat(collectionExistsOn(closed)).isFalse();

        mockMvc.perform(delete("/api/admin/collection-holidays/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        assertThat(collectionExistsOn(closed)).as("the ordinary day is restored").isTrue();
    }

    @Test
    void movingAHolidayFreesTheDayItNoLongerCloses() throws Exception {
        LocalDate closed = anAlreadyGeneratedOrganicsDate();

        String created = mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(closed, 1))))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        // Move it a week later, onto the following organics collection.
        LocalDate movedTo = closed.plusDays(7);
        mockMvc.perform(put("/api/admin/collection-holidays/" + id)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(movedTo, 1))))
                .andExpect(status().isOk());

        assertThat(collectionExistsOn(closed)).as("the day it no longer closes is collected again").isTrue();
        assertThat(collectionExistsOn(movedTo)).as("and the day it now closes is not").isFalse();
    }

    @Test
    void aCollectionAnAdministratorTypedInIsNotCollateralDamage() throws Exception {
        LocalDate closed = anAlreadyGeneratedOrganicsDate();
        LocalDate manualDay = closed.plusDays(3);
        jdbc.update("insert into collection_event "
                        + "(municipality_id, collection_date, sector, collection_type, bin_color, note_en, source_url, auto_generated) "
                        + "values (?, ?, 'all', 'special', 'none', 'Hand-entered', 'test://admin-holiday', false)",
                MUNI, manualDay);

        mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(closed, 1))))
                .andExpect(status().isCreated());

        Integer survived = jdbc.queryForObject(
                "select count(*) from collection_event where source_url = 'test://admin-holiday' and auto_generated = false",
                Integer.class);
        assertThat(survived).as("rebuilding the calendar must not delete what a person entered").isEqualTo(1);

    }

    @Test
    void theEndpointIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/admin/collection-holidays")
                        .param("municipality", CITY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(LocalDate.parse("2030-01-01"), 1))))
                .andExpect(status().isUnauthorized());
    }

    private Map<String, Object> collection(String sector) {
        Map<String, Object> body = new HashMap<>();
        body.put("collectionDate", LocalDate.now().plusDays(40).toString());
        body.put("sector", sector);
        body.put("collectionType", "bulky");
        body.put("binColor", "none");
        body.put("sourceUrl", "test://admin-holiday");
        return body;
    }

    /**
     * The console used to offer Blainville's north/south/all whichever city
     * was selected, and the server saved whatever arrived: a collection in a
     * sector the city does not have, which no resident there can ever see.
     */
    @Test
    void aSectorTheCityDoesNotHaveIsRefused_andTheErrorNamesTheRealOnes() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/admin/collections")
                        .param("municipality", "sainte-therese")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collection("north"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "Sainte-Therese has no sector 'north'. Its sectors are: all, a, b, c, d")));

        Map<String, Object> holiday = request(LocalDate.now().plusDays(40), 1);
        holiday.put("sector", "south");
        mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", "sainte-therese")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(holiday)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/collections")
                        .param("municipality", "sainte-therese")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collection("a"))))
                .andExpect(status().isCreated());
    }

    @Test
    void anUpdateIsCheckedAgainstTheRowsOwnCity_notTheOneSelectedNow() throws Exception {
        String token = adminToken();
        long blainvilleRow = objectMapper.readTree(mockMvc.perform(post("/api/admin/collections")
                        .param("municipality", "blainville")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collection("north"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        // The console has since switched to Sainte-Therese, where "a" exists;
        // the row is still Blainville's, where it does not.
        mockMvc.perform(put("/api/admin/collections/" + blainvilleRow)
                        .param("municipality", "sainte-therese")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collection("a"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "Blainville has no sector 'a'")));
    }

    @Test
    void aShiftOfZeroIsRefusedBeforeItCanReachTheCascadeLoop() throws Exception {
        mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(LocalDate.parse("2030-01-01"), 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aHolidayWithoutAllThreeNamesIsRefused() throws Exception {
        Map<String, Object> missingChinese = request(LocalDate.parse("2030-01-01"), 1);
        missingChinese.put("nameZh", "  ");

        mockMvc.perform(post("/api/admin/collection-holidays")
                        .param("municipality", CITY)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingChinese)))
                .andExpect(status().isBadRequest());
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", IntegrationEnvironment.ADMIN_EMAIL,
                                "password", IntegrationEnvironment.ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
