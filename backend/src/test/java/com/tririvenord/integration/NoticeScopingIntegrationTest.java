package com.tririvenord.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tririvenord.notice.SpecialNotice;
import com.tririvenord.notice.SpecialNoticeService;
import com.tririvenord.notice.dto.NoticeRequest;
import com.tririvenord.notification.ResidentNotificationMapper;
import com.tririvenord.push.PushSubscription;
import com.tririvenord.push.PushSubscriptionMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A city notice reaches that city and no other.
 *
 * <p>Before V27 a notice had no city. The public list showed every city's
 * notices to everyone, and a new notice went to the inbox and phone of every
 * resident with reminders on, in all four cities - invisible while only
 * Blainville was loaded. Each path a notice travels is checked here with a
 * resident in each of two cities, created by this test rather than borrowed
 * from whichever test registered one first.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class NoticeScopingIntegrationTest {
    private static final String MARKER = "notice-scoping-test";
    private static final String BLAINVILLE_RESIDENT = "notice-scoping-blainville@test.local";
    private static final String STE_THERESE_RESIDENT = "notice-scoping-ste-therese@test.local";

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
    private SpecialNoticeService notices;
    @Autowired
    private ResidentNotificationMapper notifications;
    @Autowired
    private PushSubscriptionMapper pushSubscriptions;
    @Autowired
    private JdbcTemplate jdbc;

    private long blainville;
    private long sainteTherese;

    @BeforeEach
    void twoResidentsInTwoCities() {
        cleanUp();
        blainville = cityId("blainville");
        sainteTherese = cityId("sainte-therese");
        resident(BLAINVILLE_RESIDENT, blainville, "north");
        resident(STE_THERESE_RESIDENT, sainteTherese, "a");
    }

    /**
     * Notices first, then residents. Another test class's context can still
     * be relaying outbox events while this one runs, and its consumer fans a
     * notice created here out to these residents; the outbox rows go first so
     * nothing new is relayed, and a resident is deleted again if a fan-out
     * landed between its two deletes.
     */
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from outbox_event where aggregate_id in "
                + "(select id from special_notice where source_url = ?)", MARKER);
        jdbc.update("delete from resident_notification where notice_id in "
                + "(select id from special_notice where source_url = ?)", MARKER);
        jdbc.update("delete from special_notice where source_url = ?", MARKER);
        for (String email : List.of(BLAINVILLE_RESIDENT, STE_THERESE_RESIDENT)) {
            jdbc.update("delete from push_subscription where user_id = (select id from app_user where email = ?)", email);
            jdbc.update("delete from user_preference where user_id = (select id from app_user where email = ?)", email);
            for (int attempt = 1; ; attempt++) {
                jdbc.update("delete from resident_notification where user_id = (select id from app_user where email = ?)", email);
                try {
                    jdbc.update("delete from app_user where email = ?", email);
                    break;
                } catch (org.springframework.dao.DataIntegrityViolationException raced) {
                    if (attempt == 5) {
                        throw raced;
                    }
                }
            }
        }
    }

    private long cityId(String slug) {
        return jdbc.queryForObject("select id from municipality where slug = ?", Long.class, slug);
    }

    private void resident(String email, long city, String sector) {
        jdbc.update("insert into app_user (email, password_hash, role) values (?, 'not-a-login', 'USER')", email);
        long user = jdbc.queryForObject("select id from app_user where email = ?", Long.class, email);
        jdbc.update("insert into user_preference (user_id, sector, language_code, reminder_enabled, municipality_id) "
                + "values (?, ?, 'fr', true, ?)", user, sector, city);
        jdbc.update("insert into push_subscription (user_id, endpoint, p256dh, auth) values (?, ?, 'key', 'auth')",
                user, "https://push.example/" + email);
    }

    private NoticeRequest request(String titleFr) {
        return new NoticeRequest(LocalDate.now().minusDays(1), LocalDate.now().plusDays(3),
                titleFr, "Notice", "通知", "Corps", "Body", "正文", MARKER, true);
    }

    private List<String> activeTitles(String slug) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/notices/active").param("municipality", slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> titles = new ArrayList<>();
        body.forEach(n -> titles.add(n.get("titleFr").asText()));
        return titles;
    }

    @Test
    void thePublicListShowsOnlyTheCitysOwnNotices() throws Exception {
        notices.create(sainteTherese, request("Collecte reportee a Sainte-Therese"));

        assertThat(activeTitles("sainte-therese")).contains("Collecte reportee a Sainte-Therese");
        assertThat(activeTitles("blainville")).doesNotContain("Collecte reportee a Sainte-Therese");
    }

    @Test
    void theCachedListIsKeptPerCity() throws Exception {
        // Warm Blainville's entry first: with a key of "today" alone, this
        // would then be served to Sainte-Therese too.
        activeTitles("blainville");
        notices.create(sainteTherese, request("Avis Sainte-Therese"));
        activeTitles("blainville");

        assertThat(activeTitles("sainte-therese")).contains("Avis Sainte-Therese");
        assertThat(activeTitles("blainville")).doesNotContain("Avis Sainte-Therese");
    }

    @Test
    void theInboxFanOutReachesOnlyTheNoticesCity() {
        SpecialNotice notice = notices.create(sainteTherese, request("Avis pour les boites"));

        notifications.fanOutToResidentsWithReminders(notice.id(), UUID.randomUUID().toString());

        assertThat(inboxOf(STE_THERESE_RESIDENT, notice)).isOne();
        assertThat(inboxOf(BLAINVILLE_RESIDENT, notice)).isZero();
    }

    @Test
    void pushGoesOnlyToDevicesInTheNoticesCity() {
        List<String> endpoints = pushSubscriptions.findForResidentsWithReminders(sainteTherese).stream()
                .map(PushSubscription::endpoint)
                .toList();

        assertThat(endpoints)
                .contains("https://push.example/" + STE_THERESE_RESIDENT)
                .doesNotContain("https://push.example/" + BLAINVILLE_RESIDENT);
    }

    @Test
    void theAdminListAndCreateAreForTheSelectedCity() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/admin/notices").param("municipality", "sainte-therese")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("Cree depuis la console"))))
                .andExpect(status().isCreated());

        assertThat(adminTitles("sainte-therese", token)).contains("Cree depuis la console");
        assertThat(adminTitles("blainville", token)).doesNotContain("Cree depuis la console");
        assertThat(jdbc.queryForObject(
                "select municipality_id from special_notice where title_fr = 'Cree depuis la console'", Long.class))
                .isEqualTo(sainteTherese);
    }

    private int inboxOf(String email, SpecialNotice notice) {
        return jdbc.queryForObject("select count(*) from resident_notification where notice_id = ? "
                + "and user_id = (select id from app_user where email = ?)", Integer.class, notice.id(), email);
    }

    private List<String> adminTitles(String slug, String token) throws Exception {
        JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/admin/notices").param("municipality", slug)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> titles = new ArrayList<>();
        body.forEach(n -> titles.add(n.get("titleFr").asText()));
        return titles;
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
}
