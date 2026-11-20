package com.tririvenord.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three small things the simulated residents left open, against the real
 * guides and calendars: the French guide text without its accents, a
 * pizza-box answer that also listed the tin can, and an admin collection list
 * that started in May 2027.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class SmallFixesIntegrationTest {
    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode json(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void theFrenchGuideTextHasItsAccents_andItsAddressesAreUntouched() {
        assertThat(jdbc.queryForObject("""
                select t.name from sorting_item_translation t join sorting_item i on i.id = t.item_id
                join municipality m on m.id = i.municipality_id
                where m.slug = 'blainville' and i.item_key = 'food-soiled-paper-and-cardboard' and t.language_code = 'fr'
                """, String.class)).isEqualTo("Papiers et cartons souillés d'aliments");

        // Words that were written without their accents in every town, now none.
        Integer unaccented = jdbc.queryForObject("""
                select count(*) from sorting_item_translation where language_code = 'fr'
                  and (name || ' ' || instruction || ' ' || coalesce(location, '') || ' ' || coalesce(availability, ''))
                      ~* '\\m(decembre|ecocentre|residus|dechets|recuperation|boites?|legumes|cafe|metal|exigee)\\M'
                """, Integer.class);
        assertThat(unaccented).isZero();

        // Web and email addresses keep their spelling ("sainte-therese.ca").
        assertThat(jdbc.queryForObject("""
                select count(*) from sorting_item_translation where language_code = 'fr'
                  and instruction like '%travauxpublics@sainte-therese.ca%'
                """, Integer.class)).isPositive();
    }

    @Test
    void aPizzaBoxIsAnsweredWithoutUnrelatedEntries_inEveryTown() throws Exception {
        for (String town : List.of("blainville", "sainte-therese", "boisbriand", "rosemere")) {
            for (String[] q : new String[][]{{"fr", "Où va une boîte à pizza?"}, {"en", "pizza box"}}) {
                JsonNode answer = json(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", q[1], "language", q[0], "municipality", town))));
                List<String> sources = new ArrayList<>();
                answer.get("sources").forEach(s -> sources.add(s.get("name").asText()));
                assertThat(sources).as(town + " " + q[1]).hasSize(1);
                assertThat(sources.get(0).toLowerCase()).as(town + " " + q[1]).containsAnyOf("souill", "soiled");
            }
        }
    }

    private static final String LIST_MARKER = "test://admin-list-order";

    @org.junit.jupiter.api.AfterEach
    void removeListEvents() {
        jdbc.update("delete from collection_event where source_url = ?", LIST_MARKER);
    }

    @Test
    void theAdminCollectionListStartsWithWhatIsComing() throws Exception {
        // Its own events, a past one and two to come: what else is in the
        // calendar depends on which tests ran before (on CI, sometimes no
        // collection to come at all).
        LocalDate today = LocalDate.now();
        for (LocalDate day : List.of(today.minusDays(2), today.plusDays(1), today.plusDays(3))) {
            jdbc.update("""
                    insert into collection_event (municipality_id, collection_date, sector, collection_type, bin_color, source_url)
                    select id, ?, 'list-order-test', 'garbage', 'black', ? from municipality where slug = 'blainville'
                    """, day, LIST_MARKER);
        }
        JsonNode login = json(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", IntegrationEnvironment.ADMIN_EMAIL, "password", IntegrationEnvironment.ADMIN_PASSWORD))));
        String token = login.get("token").asText();

        JsonNode page = json(get("/api/admin/collections").param("municipality", "blainville")
                .param("page", "0").param("size", "100").header("Authorization", "Bearer " + token));
        List<LocalDate> dates = new ArrayList<>();
        page.get("items").forEach(e -> dates.add(LocalDate.parse(e.get("collectionDate").asText())));

        LocalDate nearest = jdbc.queryForObject("""
                select min(collection_date) from collection_event e join municipality m on m.id = e.municipality_id
                where m.slug = 'blainville' and collection_date >= ?
                """, LocalDate.class, today);
        assertThat(dates.get(0)).isEqualTo(nearest);
        // Everything to come, in date order, before anything past.
        int firstPast = (int) dates.stream().takeWhile(d -> !d.isBefore(today)).count();
        assertThat(dates.subList(0, firstPast)).isSorted();
        assertThat(dates.subList(firstPast, dates.size())).allSatisfy(d -> assertThat(d).isBefore(today));
        assertThat(dates.subList(firstPast, dates.size()))
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
