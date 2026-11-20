package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The verdict you bought.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NoticePrevious {
    @JsonProperty("verdict")
    private String verdict;
    @JsonProperty("reason")
    private String reason;
    @JsonProperty("score")
    private Double score;
    @JsonProperty("safe_to_send")
    private boolean safeToSend;
    @JsonProperty("verified_at")
    private String verifiedAt;

    NoticePrevious() {
    }

    /**
     * The verdict you bought.
     *
     * @return the verdict
     */
    public String verdict() {
        return verdict;
    }

    /**
     * Why it was given.
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Its {@code catch_all} score; {@code null} for another verdict.
     *
     * @return the score
     */
    public Double score() {
        return score;
    }

    /**
     * What it said.
     *
     * @return the safe to send
     */
    public boolean safeToSend() {
        return safeToSend;
    }

    /**
     * When it was given, ISO 8601.
     *
     * @return the verified at
     */
    public String verifiedAt() {
        return verifiedAt;
    }

    /**
     * This noticePrevious as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
