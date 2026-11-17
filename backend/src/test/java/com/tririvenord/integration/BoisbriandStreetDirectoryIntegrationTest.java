package com.tririvenord.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Which sector is my street in?" for Boisbriand, through the public API a
 * resident's settings page calls.
 *
 * <p>The expected sectors are read off page 6 of the city's 2025 calendar
 * ("Journees et secteurs de collectes"), not off V26, and were checked
 * against the rendered page. They include the rows the extraction had to
 * work for: civic-number ranges that wrap onto a second line, and a
 * superscript ("6e Avenue") that the PDF stores as a separate line.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class BoisbriandStreetDirectoryIntegrationTest {

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

    private JsonNode search(String city, String query) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/municipalities/" + city + "/streets")
                        .param("q", query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> lines(JsonNode streets) {
        List<String> out = new ArrayList<>();
        streets.forEach(s -> out.add(s.get("name").asText() + " -> " + s.get("sectorCode").asText()));
        return out;
    }

    @Test
    void aSplitStreetComesBackOncePerCivicRange_withoutSigningIn() throws Exception {
        // Page 6: sector A "Grande-Allee (nos 505 a 1700)" Mardi and
        // "(nos 3153 a 3245)" Vendredi; sector B "(nos 3305 a 4640)" Vendredi.
        // Typed the way residents type it: no hyphen, no accent.
        assertThat(lines(search("boisbriand", "grande allee"))).containsExactly(
                "Grande-Allée (nos 3153 à 3245) -> a-vendredi",
                "Grande-Allée (nos 3305 à 4640) -> b-vendredi",
                "Grande-Allée (nos 505 à 1700) -> a-mardi");
    }

    @Test
    void streetsTheExtractionHadToWorkFor() throws Exception {
        // Each line is one cell pair on page 6.
        assertThat(lines(search("boisbriand", "chenaies"))).containsExactly("Chênaies -> a-mercredi");
        assertThat(lines(search("boisbriand", "Chevalier"))).containsExactly("Chevalier -> a-jeudi");
        assertThat(lines(search("boisbriand", "Jordi-Bonet"))).containsExactly("Jordi-Bonet -> b-vendredi");
        assertThat(lines(search("boisbriand", "6e Avenue"))).containsExactly("6e Avenue -> b-jeudi");
        assertThat(lines(search("boisbriand", "Grande-Côte"))).containsExactlyInAnyOrder(
                "Grande-Côte (nos 140 à 380) -> a-mardi",
                "Grande-Côte (nos 21 à 120) -> b-jeudi",
                "Grande-Côte (nos 383 à 740) -> b-mercredi");
    }

    @Test
    void namesThatStartWithWhatWasTypedComeFirst() throws Exception {
        JsonNode found = search("boisbriand", "bo");
        assertThat(found).isNotEmpty();
        assertThat(found.get(0).get("name").asText()).startsWithIgnoringCase("bo");
    }

    @Test
    void tooShortOrWildcardQueriesFindNothingRatherThanEverything() throws Exception {
        assertThat(search("boisbriand", "b")).isEmpty();
        assertThat(search("boisbriand", "%%")).isEmpty();
        assertThat(search("boisbriand", "__")).isEmpty();
    }

    @Test
    void onlyBoisbriandHasADirectory_andTheCityViewSaysSo() throws Exception {
        assertThat(search("blainville", "seigneurie")).isEmpty();

        JsonNode cities = objectMapper.readTree(mockMvc.perform(get("/api/municipalities"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        cities.forEach(city -> assertThat(city.get("hasStreetDirectory").asBoolean())
                .as(city.get("slug").asText())
                .isEqualTo("boisbriand".equals(city.get("slug").asText())));
    }

    @Test
    void everyStreetLeadsToASectorThatHasCollections() {
        // The foreign key guarantees the sector exists; this guarantees it is
        // not an empty one. A street pointing at a sector with no rules would
        // show its resident an empty week, the failure this project keeps
        // naming as the one that looks like data.
        Integer streets = jdbc.queryForObject("""
                select count(*) from municipality_street s
                join municipality m on m.id = s.municipality_id and m.slug = 'boisbriand'
                """, Integer.class);
        assertThat(streets).isEqualTo(252);

        List<String> withoutCollections = jdbc.queryForList("""
                select s.name from municipality_street s
                join municipality m on m.id = s.municipality_id and m.slug = 'boisbriand'
                where not exists (
                    select 1 from collection_schedule_rule r
                    where r.municipality_id = s.municipality_id and r.sector = s.sector_code)
                """, String.class);
        assertThat(withoutCollections).isEmpty();
    }
}
