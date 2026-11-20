package com.tririvenord.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saving a guide card the way the admin console does.
 *
 * <p>The console sends no {@code itemKey}, and the API required one, so every
 * "add" and every "save" from the console was refused with 400 - found by a
 * simulated administrator, missed by the console's own tests because they
 * mock the API. The bodies below are the console's payload, field for field.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AdminSortingConsoleIntegrationTest {
    private static final String MARKER = "test://admin-console-card";

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

    @AfterEach
    void cleanUp() {
        String items = "(select id from sorting_item where source_url = '" + MARKER + "')";
        jdbc.update("delete from sorting_item_keyword where item_id in " + items);
        jdbc.update("delete from sorting_item_example where item_id in " + items);
        jdbc.update("delete from sorting_item_translation where item_id in " + items);
        jdbc.update("delete from sorting_item where source_url = ?", MARKER);
    }

    private String adminToken() throws Exception {
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", IntegrationEnvironment.ADMIN_EMAIL,
                                "password", IntegrationEnvironment.ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString());
        return login.get("token").asText();
    }

    /** What AdminView.saveSortingItem sends: no itemKey. */
    private String consolePayload(String englishName, String englishInstruction) throws Exception {
        Map<String, Object> fr = Map.of("name", "Bougie parfumee", "instruction", "Au bac gris.", "examples", List.of());
        Map<String, Object> en = Map.of("name", englishName, "instruction", englishInstruction, "examples", List.of());
        Map<String, Object> zh = Map.of("name", "香薰蜡烛", "instruction", "放入灰桶。", "examples", List.of());
        return objectMapper.writeValueAsString(Map.of(
                "destinationType", "garbage", "binColor", "grey", "sourceUrl", MARKER,
                "fr", fr, "en", en, "zh", zh,
                "keywordsFr", List.of("bougie"), "keywordsEn", List.of("candle"), "keywordsZh", List.of("蜡烛")));
    }

    private JsonNode send(org.springframework.test.web.servlet.RequestBuilder request, int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request)
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void theConsoleCanAddACard_andItGetsAKeyFromItsEnglishName() throws Exception {
        String token = adminToken();
        JsonNode created = send(post("/api/admin/sorting-items").param("municipality", "rosemere")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(consolePayload("Scented candle", "In the grey bin.")), 201);

        assertThat(jdbc.queryForObject("select item_key from sorting_item where id = ?", String.class,
                created.get("id").asLong())).isEqualTo("scented-candle");

        // The same name again gets a key of its own rather than a conflict.
        JsonNode again = send(post("/api/admin/sorting-items").param("municipality", "rosemere")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(consolePayload("Scented candle", "In the grey bin.")), 201);
        assertThat(jdbc.queryForObject("select item_key from sorting_item where id = ?", String.class,
                again.get("id").asLong())).isEqualTo("scented-candle-2");
    }

    @Test
    void theConsoleCanSaveAnEdit_andTheKeyStays() throws Exception {
        String token = adminToken();
        JsonNode created = send(post("/api/admin/sorting-items").param("municipality", "rosemere")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(consolePayload("Scented candle", "In the grey bin.")), 201);
        long id = created.get("id").asLong();

        JsonNode edited = send(put("/api/admin/sorting-items/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(consolePayload("Scented candle (renamed)", "Still in the grey bin.")), 200);

        assertThat(edited.get("en").get("instruction").asText()).isEqualTo("Still in the grey bin.");
        assertThat(jdbc.queryForObject("select item_key from sorting_item where id = ?", String.class, id))
                .isEqualTo("scented-candle");
    }
}
