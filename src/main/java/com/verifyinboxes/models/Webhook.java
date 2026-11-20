package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * Where the API sends events.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Webhook {
    @JsonProperty("id")
    private String id;
    @JsonProperty("url")
    private String url;
    @JsonProperty("events")
    private List<String> events;
    @JsonProperty("created_at")
    private String createdAt;

    Webhook() {
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
     * The events sent: {@code job.completed}, {@code address.contradicted}.
     *
     * @return the events, never {@code null}; the list cannot be changed
     */
    public List<String> events() {
        return events == null ? Collections.emptyList() : Collections.unmodifiableList(events);
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
     * This webhook as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
