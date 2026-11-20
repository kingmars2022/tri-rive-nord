package com.tririvenord.integration;

import com.tririvenord.notice.SpecialNotice;
import com.tririvenord.notice.SpecialNoticeService;
import com.tririvenord.notice.dto.NoticeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The production configuration: no broker (EVENTS_ENABLED=false).
 *
 * <p>The inbox used to be filled only by the Kafka consumer, so on the free
 * deployment a published notice never reached any resident's inbox although
 * the settings page promises it. Found by a simulated resident.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class NoticeInboxWithoutBrokerIntegrationTest {
    private static final String MARKER = "notice-inbox-without-broker-test";
    private static final String WITH_REMINDERS = "inbox-no-broker-on@test.local";
    private static final String WITHOUT_REMINDERS = "inbox-no-broker-off@test.local";
    private static final String OTHER_TOWN = "inbox-no-broker-rosemere@test.local";

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.events.enabled", () -> "false");
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    @Autowired
    private SpecialNoticeService notices;
    @Autowired
    private JdbcTemplate jdbc;

    private void resident(String email, String town, String sector, boolean reminders) {
        jdbc.update("insert into app_user (email, password_hash, role) values (?, 'not-a-login', 'USER') "
                + "on conflict (email) do nothing", email);
        jdbc.update("""
                insert into user_preference (user_id, sector, language_code, reminder_enabled, municipality_id)
                select u.id, ?, 'fr', ?, m.id from app_user u, municipality m
                where u.email = ? and m.slug = ?
                on conflict (user_id) do nothing
                """, sector, reminders, email, town);
    }

    @BeforeEach
    void residents() {
        resident(WITH_REMINDERS, "blainville", "north", true);
        resident(WITHOUT_REMINDERS, "blainville", "north", false);
        resident(OTHER_TOWN, "rosemere", "secteur-1", true);
    }

    @AfterEach
    void cleanUp() {
        String notice = "(select id from special_notice where source_url = '" + MARKER + "')";
        jdbc.update("delete from outbox_event where aggregate_id in " + notice);
        jdbc.update("delete from resident_notification where notice_id in " + notice);
        jdbc.update("delete from special_notice where source_url = ?", MARKER);
        for (String email : new String[]{WITH_REMINDERS, WITHOUT_REMINDERS, OTHER_TOWN}) {
            jdbc.update("delete from resident_notification where user_id = (select id from app_user where email = ?)", email);
            jdbc.update("delete from user_preference where user_id = (select id from app_user where email = ?)", email);
            jdbc.update("delete from app_user where email = ?", email);
        }
    }

    private int inboxCount(String email, long noticeId) {
        return jdbc.queryForObject("select count(*) from resident_notification n join app_user u on u.id = n.user_id "
                + "where u.email = ? and n.notice_id = ?", Integer.class, email, noticeId);
    }

    @Test
    void aPublishedNoticeReachesTheTownsResidentsWithRemindersOn_once() {
        SpecialNotice created = notices.create(1L, new NoticeRequest(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(3),
                "Collecte reportee", "Collection moved", "收集改期",
                "Corps", "Body", "正文", MARKER, true));

        assertThat(inboxCount(WITH_REMINDERS, created.id())).isEqualTo(1);
        assertThat(inboxCount(WITHOUT_REMINDERS, created.id())).isZero();
        assertThat(inboxCount(OTHER_TOWN, created.id())).isZero();

        // Editing it does not deliver it a second time.
        notices.update(created.id(), new NoticeRequest(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(4),
                "Collecte reportee (modifie)", "Collection moved (edited)", "收集改期",
                "Corps", "Body", "正文", MARKER, true));
        assertThat(inboxCount(WITH_REMINDERS, created.id())).isEqualTo(1);
    }
}
