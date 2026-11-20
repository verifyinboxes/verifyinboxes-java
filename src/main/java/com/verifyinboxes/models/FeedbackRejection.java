package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An event that was refused.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FeedbackRejection {
    @JsonProperty("index")
    private int index;
    @JsonProperty("error")
    private String error;

    FeedbackRejection() {
    }

    /**
     * The event's position in what you sent, from 0.
     *
     * @return the index
     */
    public int index() {
        return index;
    }

    /**
     * Why it was refused.
     *
     * @return the error
     */
    public String error() {
        return error;
    }

    /**
     * This feedbackRejection as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
