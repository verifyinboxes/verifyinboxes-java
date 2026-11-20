package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An integration, just made ({@code POST /v1/integrations}). {@link #url()} holds its token and is shown only
 * here: paste it into the provider. It is left out of {@link #toString()}, and kept in JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class IntegrationCreated {
    @JsonProperty("id")
    private String id;
    @JsonProperty("provider")
    private String provider;
    @JsonProperty("label")
    private String label;
    @JsonProperty("url")
    private String url;
    @JsonProperty("created_at")
    private String createdAt;

    IntegrationCreated() {
    }

    /**
     * The integration's id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * {@code instantly} or {@code smartlead}.
     *
     * @return the provider
     */
    public String provider() {
        return provider;
    }

    /**
     * Your name for it; {@code null} when none.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * The address to paste into the provider. It holds the integration's token, and is shown only here.
     *
     * @return the url
     */
    public String url() {
        return url;
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
     * This integrationCreated as the API sent it, in JSON, without its {@code url}.
     *
     * @return the JSON, without its {@code url}
     */
    @Override
    public String toString() {
        return ModelJson.write(this, "url");
    }
}
