package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An address that lets Instantly or Smartlead report what became of your mail.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Integration {
    @JsonProperty("id")
    private String id;
    @JsonProperty("provider")
    private String provider;
    @JsonProperty("label")
    private String label;
    @JsonProperty("token_prefix")
    private String tokenPrefix;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("last_event_at")
    private String lastEventAt;
    @JsonProperty("last_bounce_at")
    private String lastBounceAt;
    @JsonProperty("last_error")
    private String lastError;
    @JsonProperty("last_error_at")
    private String lastErrorAt;
    @JsonProperty("signed")
    private boolean signed;

    Integration() {
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
     * The first 12 characters of its URL's token, to tell integrations apart.
     *
     * @return the token prefix
     */
    public String tokenPrefix() {
        return tokenPrefix;
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
     * The last delivery it accepted, recorded or ignored; {@code null} when none.
     *
     * @return the last event at
     */
    public String lastEventAt() {
        return lastEventAt;
    }

    /**
     * The last new bounce it reported; {@code null} when none.
     *
     * @return the last bounce at
     */
    public String lastBounceAt() {
        return lastBounceAt;
    }

    /**
     * The last real problem with a delivery ({@code malformed}, {@code no_lead_email}, {@code invalid_email},
     * {@code ambiguous_lead}, {@code bad_event_id}, {@code missing_signature}, {@code bad_signature}); {@code null}
     * when there has been none.
     *
     * @return the last error
     */
    public String lastError() {
        return lastError;
    }

    /**
     * When that was; {@code null} when there has been none.
     *
     * @return the last error at
     */
    public String lastErrorAt() {
        return lastErrorAt;
    }

    /**
     * Whether it has a signing secret.
     *
     * @return the signed
     */
    public boolean signed() {
        return signed;
    }

    /**
     * This integration as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
