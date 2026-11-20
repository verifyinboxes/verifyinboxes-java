package com.verifyinboxes;

import com.verifyinboxes.models.Webhook;
import com.verifyinboxes.models.WebhookCreated;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the API sends events (a list finished, a verdict you bought was overturned): {@code client.webhooks()}. To
 * check a delivery's signature, see {@link Webhooks}.
 */
public final class WebhookService {
    private final Transport t;

    WebhookService(Transport t) {
        this.t = t;
    }

    /**
     * Makes a webhook of every event. See {@link #create(String, List)}.
     *
     * @param url an {@code https://} address that gets the events
     * @return the webhook; its {@code secret()} is shown only now
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public WebhookCreated create(String url) {
        return create(url, null);
    }

    /**
     * Makes a webhook ({@code POST /v1/webhooks}). The answer's {@code secret()} is shown only once: store it.
     *
     * <p>It is not sent again once it may have arrived (a second webhook would be made): it is retried only when the
     * connection was never made, or after a {@code 429}. After a {@link com.verifyinboxes.errors.ConnectionException},
     * look at {@link #list()} before making another.
     *
     * @param url    an {@code https://} address that gets the events
     * @param events {@link Webhooks#EVENT_JOB_COMPLETED}, {@link Webhooks#EVENT_ADDRESS_CONTRADICTED}; {@code null}
     *               for both
     * @return the webhook; its {@code secret()} is shown only now
     * @throws IllegalArgumentException when the URL is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public WebhookCreated create(String url, List<String> events) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("A webhook needs its URL");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        if (events != null && !events.isEmpty()) {
            body.put("events", events);
        }
        Transport.Request r = new Transport.Request("POST", "/v1/webhooks");
        r.json = body;
        r.key = Transport.newKey();
        r.retry = Transport.Retry.UNSENT;
        return t.decode(t.send(r), WebhookCreated.class);
    }

    /**
     * The webhooks, never their secrets ({@code GET /v1/webhooks}).
     *
     * @return the webhooks
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public List<Webhook> list() {
        return t.decodeList(t.send(new Transport.Request("GET", "/v1/webhooks")), Webhook.class);
    }

    /**
     * Removes a webhook ({@code DELETE /v1/webhooks/{id}}). A retry that finds it gone counts as done.
     *
     * @param id the webhook's id
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public void delete(String id) {
        Transport.Request r = new Transport.Request("DELETE", "/v1/webhooks/" + Transport.segment("webhook id", id));
        r.goneIsDone = true;
        t.send(r);
    }

    /**
     * {@link Webhooks#verifySignature(byte[], String, String)}, here too.
     *
     * @param body   the body exactly as it arrived
     * @param header the {@value Webhooks#SIGNATURE_HEADER} header, or {@code null}
     * @param secret the 64 characters {@link #create} returned
     * @return {@code true} only for a valid signature
     */
    public boolean verifySignature(byte[] body, String header, String secret) {
        return Webhooks.verifySignature(body, header, secret);
    }

    /**
     * {@link Webhooks#verifySignature(String, String, String)}, here too.
     *
     * @param body   the body exactly as it arrived, as text
     * @param header the {@value Webhooks#SIGNATURE_HEADER} header, or {@code null}
     * @param secret the 64 characters {@link #create} returned
     * @return {@code true} only for a valid signature
     */
    public boolean verifySignature(String body, String header, String secret) {
        return Webhooks.verifySignature(body, header, secret);
    }
}
