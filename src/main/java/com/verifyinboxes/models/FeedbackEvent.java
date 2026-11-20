package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What became of mail you sent to an address, to report with {@code client.feedback().report(events)}.
 *
 * <pre>{@code
 * FeedbackEvent bounce = FeedbackEvent.of("ann@example.com", "hard_bounce", "2026-11-04T10:00:00Z")
 *         .withDsnCode("5.1.1")
 *         .withEventId("b-1");
 * }</pre>
 *
 * <p>An event is immutable: each {@code with...} method returns a copy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class FeedbackEvent {
    @JsonProperty("email")
    private String email;
    @JsonProperty("type")
    private String type;
    @JsonProperty("observed_at")
    private String observedAt;
    @JsonProperty("event_id")
    private String eventId;
    @JsonProperty("dsn_code")
    private String dsnCode;
    @JsonProperty("diagnostic")
    private String diagnostic;

    FeedbackEvent() {
    }

    private FeedbackEvent copy() {
        FeedbackEvent c = new FeedbackEvent();
        c.email = email;
        c.type = type;
        c.observedAt = observedAt;
        c.eventId = eventId;
        c.dsnCode = dsnCode;
        c.diagnostic = diagnostic;
        return c;
    }

    /**
     * An event.
     *
     * @param email      the address the mail was sent to
     * @param type       {@code sent}, {@code delivered}, {@code reply}, {@code auto_reply}, {@code hard_bounce},
     *                   {@code soft_bounce} or {@code bounce}
     * @param observedAt when it happened, ISO 8601 (such as {@code 2026-11-04T10:00:00Z})
     * @return the event
     */
    public static FeedbackEvent of(String email, String type, String observedAt) {
        FeedbackEvent e = new FeedbackEvent();
        e.email = email;
        e.type = type;
        e.observedAt = observedAt;
        return e;
    }

    /**
     * Gives the event your own id: the same id sent again counts once.
     *
     * @param eventId your id of the event
     * @return a copy of this event with it
     */
    public FeedbackEvent withEventId(String eventId) {
        FeedbackEvent c = copy();
        c.eventId = eventId;
        return c;
    }

    /**
     * Gives a bounce its status code.
     *
     * @param dsnCode the bounce's status code, such as {@code 5.1.1}
     * @return a copy of this event with it
     */
    public FeedbackEvent withDsnCode(String dsnCode) {
        FeedbackEvent c = copy();
        c.dsnCode = dsnCode;
        return c;
    }

    /**
     * Gives a bounce its diagnostic text.
     *
     * @param diagnostic the bounce's diagnostic text
     * @return a copy of this event with it
     */
    public FeedbackEvent withDiagnostic(String diagnostic) {
        FeedbackEvent c = copy();
        c.diagnostic = diagnostic;
        return c;
    }

    /**
     * The address the mail was sent to.
     *
     * @return the address
     */
    public String email() {
        return email;
    }

    /**
     * What happened: {@code sent}, {@code delivered}, {@code reply}, {@code auto_reply}, {@code hard_bounce},
     * {@code soft_bounce} or {@code bounce}.
     *
     * @return the type
     */
    public String type() {
        return type;
    }

    /**
     * When it happened, ISO 8601.
     *
     * @return the time
     */
    public String observedAt() {
        return observedAt;
    }

    /**
     * Your own id of the event.
     *
     * @return the id, or {@code null}
     */
    public String eventId() {
        return eventId;
    }

    /**
     * A bounce's status code.
     *
     * @return the code, or {@code null}
     */
    public String dsnCode() {
        return dsnCode;
    }

    /**
     * A bounce's diagnostic text.
     *
     * @return the text, or {@code null}
     */
    public String diagnostic() {
        return diagnostic;
    }

    /**
     * This event, in JSON, as it is sent.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
