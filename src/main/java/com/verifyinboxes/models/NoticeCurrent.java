package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The verdict the address was verified again to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NoticeCurrent {
    @JsonProperty("verdict")
    private String verdict;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("verified_at")
    private String verifiedAt;

    NoticeCurrent() {
    }

    /**
     * {@code valid} or {@code invalid}.
     *
     * @return the verdict
     */
    public String verdict() {
        return verdict;
    }

    /**
     * Why.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }

    /**
     * When that finished, ISO 8601.
     *
     * @return the verified at
     */
    public String verifiedAt() {
        return verifiedAt;
    }

    /**
     * This noticeCurrent as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
