package com.verifyinboxes;

import com.verifyinboxes.models.FeedbackEvent;
import com.verifyinboxes.models.FeedbackResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What became of the mail you sent (bounces, deliveries, replies): {@code client.feedback()}. */
public final class FeedbackService {
    private final Transport t;

    FeedbackService(Transport t) {
        this.t = t;
    }

    /**
     * Tells what became of mail you sent. See {@link #report(List, String)}.
     *
     * @param events what happened
     * @return what became of the events
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public FeedbackResult report(List<FeedbackEvent> events) {
        return report(events, null);
    }

    /**
     * Tells what became of mail you sent ({@code POST /v1/feedback}).
     *
     * <pre>{@code
     * client.feedback().report(List.of(
     *         FeedbackEvent.of("ann@example.com", "hard_bounce", "2026-11-04T10:00:00Z").withDsnCode("5.1.1").withEventId("b-1")));
     * }</pre>
     *
     * @param events         what happened
     * @param idempotencyKey your own Idempotency-Key, or {@code null} for a new one
     * @return what became of the events
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public FeedbackResult report(List<FeedbackEvent> events, String idempotencyKey) {
        Transport.Request r = new Transport.Request("POST", "/v1/feedback");
        r.json = Collections.singletonMap("events", events == null ? Collections.emptyList() : new ArrayList<>(events));
        r.key = idempotencyKey != null ? Transport.checkKey(idempotencyKey) : Transport.newKey();
        return t.decode(t.send(r), FeedbackResult.class);
    }
}
