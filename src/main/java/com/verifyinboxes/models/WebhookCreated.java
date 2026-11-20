package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * A webhook, just made ({@code POST /v1/webhooks}). {@link #secret()} signs its deliveries and is shown only here:
 * store it. It is left out of {@link #toString()}, and kept in JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class WebhookCreated {
    @JsonProperty("id")
    private String id;
    @JsonProperty("url")
    private String url;
    @JsonProperty("events")
    private List<String> events;
    @JsonProperty("secret")
    private String secret;
    @JsonProperty("created_at")
    private String createdAt;

    WebhookCreated() {
    }

    /**
     * The webhook's id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Where the events are sent.
     *
     * @return the url
     */
    public String url() {
        return url;
    }

    /**
     * The events sent.
     *
     * @return the events, never {@code null}; the list cannot be changed
     */
    public List<String> events() {
        return events == null ? Collections.emptyList() : Collections.unmodifiableList(events);
    }

    /**
     * The secret that signs the deliveries: give it to {@code Webhooks.verifySignature}. It is shown only here.
     *
     * @return the secret
     */
    public String secret() {
        return secret;
    }

    /**
     * When it was made, ISO 8601.
     *
     * @return the created at
     */
    public String createdAt() {
        return createdAt;
    }

    /**
     * This webhookCreated as the API sent it, in JSON, without its {@code secret}.
     *
     * @return the JSON, without its {@code secret}
     */
    @Override
    public String toString() {
        return ModelJson.write(this, "secret");
    }
}
