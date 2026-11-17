package com.tririvenord.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tririvenord.config.CacheConfig;
import com.tririvenord.events.NotificationOutbox;
import com.tririvenord.push.PushSubscription;
import com.tririvenord.push.PushSubscriptionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A notice belongs to one city, and only that city hears about it.
 *
 * <p>Before notices carried a municipality, a "collection postponed" posted
 * for Blainville went into the inbox of every resident with reminders on, in
 * all four cities, and the public list showed it to everyone. These tests
 * publish through the real admin endpoint and read through the real public
 * one, with a resident in each of two cities created here: relying on
 * residents some other class registered is how the pipeline test once passed
 * or failed depending on run order.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class NoticeMunicipalityScopeIntegrationTest {
    private static final String MARKER = "notice-municipality-scope-test";
    private static final String BLAINVILLE_RESIDENT = "notice-scope-blainville@test.local";
    private static final String SAINTE_THERESE_RESIDENT = "notice-scope-sainte-therese@test.local";

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NotificationOutbox notificationOutbox;

    @Autowired
    private PushSubscriptionMapper pushSubscriptions;

    @Autowired
    private CacheManager cacheManager;

    private String adminToken;

    @BeforeEach
    void twoResidentsInTwoCities() throws Exception {
        cleanUp();
        resident(BLAINVILLE_RESIDENT, "blainville", "north");
        resident(SAINTE_THERESE_RESIDENT, "sainte-therese", "a");
        adminToken = loginAdmin();
        // The public list is cached per city and day; a list another class
        // cached earlier today must not stand in for what this test publishes.
        cacheManager.getCache(CacheConfig.ACTIVE_NOTICES).clear();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from outbox_event where aggregate_id in "
                + "(select id from special_notice where source_url = ?)", MARKER);
        jdbc.update("delete from resident_notification where notice_id in "
                + "(select id from special_notice where source_url = ?)", MARKER);
        jdbc.update("delete from special_notice where source_url = ?", MARKER);
        for (String email : List.of(BLAINVILLE_RESIDENT, SAINTE_THERESE_RESIDENT)) {
            jdbc.update("delete from resident_notification where user_id = (select id from app_user where email = ?)", email);
            jdbc.update("delete from push_subscription where user_id = (select id from app_user where email = ?)", email);
            jdbc.update("delete from user_preference where user_id = (select id from app_user where email = ?)", email);
            jdbc.update("delete from app_user where email = ?", email);
        }
    }

    @Test
    void aNoticeReachesTheInboxOfItsOwnCityAndNoOther() throws Exception {
        long blainvilleNotice = publish("blainville", "Collecte reportee a Blainville");
        long sainteThereseNotice = publish("sainte-therese", "Collecte reportee a Sainte-Therese");

        // What the Kafka consumer calls; the pipeline test covers the broker.
        notificationOutbox.queueForActiveNotice(UUID.randomUUID().toString(), blainvilleNotice);
        notificationOutbox.queueForActiveNotice(UUID.randomUUID().toString(), sainteThereseNotice);

        assertThat(inbox(BLAINVILLE_RESIDENT)).containsExactly(blainvilleNotice);
        assertThat(inbox(SAINTE_THERESE_RESIDENT)).containsExactly(sainteThereseNotice);
    }

    @Test
    void pushGoesToTheSameResidentsTheInboxDoes() {
        subscribe(BLAINVILLE_RESIDENT);
        subscribe(SAINTE_THERESE_RESIDENT);
        long blainville = cityId("blainville");

        List<Long> targets = pushSubscriptions.findForResidentsWithReminders(blainville).stream()
                .map(PushSubscription::userId)
                .toList();

        assertThat(targets).contains(userId(BLAINVILLE_RESIDENT));
        assertThat(targets).doesNotContain(userId(SAINTE_THERESE_RESIDENT));
    }

    @Test
    void thePublicActiveListIsPerCity() throws Exception {
        publish("blainville", "Avis Blainville seulement");
        publish("sainte-therese", "Avis Sainte-Therese seulement");

        // Blainville first, so a cache keyed only by day would hand its list
        // to the Sainte-Therese request that follows.
        assertThat(activeTitles("blainville"))
                .contains("Avis Blainville seulement")
                .doesNotContain("Avis Sainte-Therese seulement");
        assertThat(activeTitles("sainte-therese"))
                .contains("Avis Sainte-Therese seulement")
                .doesNotContain("Avis Blainville seulement");
        assertThat(activeTitles("rosemere"))
                .doesNotContain("Avis Blainville seulement", "Avis Sainte-Therese seulement");
    }

    @Test
    void thePublicListNamesACityOrFails() throws Exception {
        // No fallback to Blainville: a missing or mistyped city answering with
        // one city's notices would look like a real answer.
        mockMvc.perform(get("/api/notices/active")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notices/active?municipality=atlantis")).andExpect(status().isNotFound());
    }

    @Test
    void theAdminListAndEditsAreScopedToTheCityBeingAdministered() throws Exception {
        long notice = publish("blainville", "Avis a modifier");

        assertThat(adminTitles("blainville")).contains("Avis a modifier");
        assertThat(adminTitles("sainte-therese")).doesNotContain("Avis a modifier");

        // An edit form left open across a city switch must not reach back.
        mockMvc.perform(put("/api/admin/notices/" + notice + "?municipality=sainte-therese")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("Modifie depuis la mauvaise ville"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/notices/" + notice + "?municipality=sainte-therese")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        assertThat(adminTitles("blainville")).contains("Avis a modifier");

        mockMvc.perform(put("/api/admin/notices/" + notice + "?municipality=blainville")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("Avis modifie"))))
                .andExpect(status().isOk());
        assertThat(adminTitles("blainville")).contains("Avis modifie");
    }

    @Test
    void creatingANoticeRequiresACity() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("Avis sans ville"))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from special_notice where title_fr = 'Avis sans ville'",
                Integer.class)).isZero();
    }

    // ------------------------------------------------------------- helpers

    private long publish(String city, String titleFr) throws Exception {
        String response = mockMvc.perform(post("/api/admin/notices?municipality=" + city)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(titleFr))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        assertThat(created.get("municipalityId").asLong()).isEqualTo(cityId(city));
        return created.get("id").asLong();
    }

    private Map<String, Object> body(String titleFr) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startsOn", LocalDate.now().minusDays(1).toString());
        body.put("endsOn", LocalDate.now().plusDays(3).toString());
        body.put("titleFr", titleFr);
        body.put("titleEn", "Notice");
        body.put("titleZh", "通知");
        body.put("bodyFr", "Corps");
        body.put("bodyEn", "Body");
        body.put("bodyZh", "正文");
        body.put("sourceUrl", MARKER);
        body.put("active", true);
        return body;
    }

    private List<String> activeTitles(String city) throws Exception {
        return titles(mockMvc.perform(get("/api/notices/active?municipality=" + city))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<String> adminTitles(String city) throws Exception {
        return titles(mockMvc.perform(get("/api/admin/notices?municipality=" + city)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<String> titles(String json) throws Exception {
        List<String> titles = new ArrayList<>();
        objectMapper.readTree(json).forEach(notice -> titles.add(notice.get("titleFr").asText()));
        return titles;
    }

    private List<Long> inbox(String email) {
        return jdbc.queryForList("""
                select n.notice_id from resident_notification n
                join app_user u on u.id = n.user_id
                where u.email = ?
                order by n.notice_id
                """, Long.class, email);
    }

    private void resident(String email, String city, String sector) {
        jdbc.update("insert into app_user (email, password_hash, role) values (?, 'not-a-login', 'USER')", email);
        jdbc.update("""
                insert into user_preference (user_id, sector, language_code, reminder_enabled, municipality_id)
                select u.id, ?, 'fr', true, m.id
                from app_user u, municipality m
                where u.email = ? and m.slug = ?
                """, sector, email, city);
    }

    private void subscribe(String email) {
        jdbc.update("""
                insert into push_subscription (user_id, endpoint, p256dh, auth, language_code)
                values (?, ?, 'key', 'auth', 'fr')
                """, userId(email), "https://push.example/" + MARKER + "/" + email);
    }

    private long userId(String email) {
        return jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
    }

    private long cityId(String slug) {
        return jdbc.queryForObject("select id from municipality where slug = ?", Long.class, slug);
    }

    private String loginAdmin() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", IntegrationEnvironment.ADMIN_EMAIL,
                                "password", IntegrationEnvironment.ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
