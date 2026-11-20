package com.tririvenord.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each town's curbside hours and name, as the city switcher receives them.
 *
 * <p>The home page used to tell every town Blainville's hours. The expected
 * values are each town's own printed rule (cited in V35); Rosemere prints
 * none, and must get none rather than a neighbour's.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class TownRulesIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, JsonNode> towns() throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/municipalities"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<String, JsonNode> bySlug = new HashMap<>();
        body.forEach(town -> bySlug.put(town.get("slug").asText(), town));
        return bySlug;
    }

    private static String hours(JsonNode town) {
        return town.get("binsOutFrom").asText("-") + " " + town.get("binsOutBy").asText("-") + " "
                + town.get("binsInBy").asText("-");
    }

    @Test
    void eachTownHasItsOwnCurbsideHours() throws Exception {
        Map<String, JsonNode> towns = towns();

        assertThat(hours(towns.get("blainville"))).isEqualTo("20:00 06:00 20:00");
        assertThat(hours(towns.get("sainte-therese"))).isEqualTo("18:00 07:00 20:00");
        assertThat(hours(towns.get("boisbriand"))).isEqualTo("18:00 07:00 22:00");
        assertThat(towns.get("rosemere").get("binsOutFrom").isNull()).isTrue();
        assertThat(towns.get("rosemere").get("binsInBy").isNull()).isTrue();
    }

    @Test
    void townsAreNamedAsTheySpellThemselves() throws Exception {
        Map<String, JsonNode> towns = towns();

        assertThat(towns.get("sainte-therese").get("name").asText()).isEqualTo("Sainte-Thérèse");
        assertThat(towns.get("rosemere").get("name").asText()).isEqualTo("Rosemère");
    }
}
