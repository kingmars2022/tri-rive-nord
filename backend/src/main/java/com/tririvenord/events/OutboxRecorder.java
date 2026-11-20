package com.tririvenord.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Records an event in the same transaction as the write that caused it.
 *
 * <p>There is no publishing here, and that is the point. A broker call inside
 * a database transaction is the bug this pattern exists to avoid: the commit
 * and the publish can each fail independently, so residents get told about a
 * notice that was rolled back, or never told about one that exists.
 */
@Component
public class OutboxRecorder {
    private final OutboxMapper mapper;
    private final ObjectMapper objectMapper;
    private final NotificationOutbox inbox;
    private final boolean eventsEnabled;

    public OutboxRecorder(OutboxMapper mapper, ObjectMapper objectMapper, NotificationOutbox inbox,
                          @org.springframework.beans.factory.annotation.Value("${app.events.enabled:true}")
                          boolean eventsEnabled) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.inbox = inbox;
        this.eventsEnabled = eventsEnabled;
    }

    public void recordNoticeEvent(String action, Long noticeId, String actor, Object before, Object after) {
        String eventId = UUID.randomUUID().toString();
        NoticeEvent event = new NoticeEvent(eventId, action, noticeId, actor, null, before, after);

        try {
            mapper.insert(eventId, "notice", noticeId, "notice." + action,
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            // Deliberately fatal, and therefore rolls back the business write
            // too. A notice change that silently loses its event is worse than
            // a notice change that visibly failed: the first is discovered
            // later, by someone wondering why the audit trail has a hole.
            throw new IllegalStateException("Could not serialize the notice event", e);
        }

        // Without a broker (EVENTS_ENABLED=false, as on the free deployment)
        // nothing relays the event, so the consumer that fills residents'
        // inboxes never runs - and the settings page promises those inboxes.
        // Fill them here instead, in the same transaction, under the same
        // event id: if events are switched on later and this row is relayed,
        // the unique key on (user_id, event_id) makes the second delivery a
        // no-op.
        if (!eventsEnabled && "created".equals(action)) {
            inbox.queueForActiveNotice(eventId, noticeId);
        }
    }
}
