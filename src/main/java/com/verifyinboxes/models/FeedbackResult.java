package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * What became of the events reported ({@code POST /v1/feedback}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class FeedbackResult {
    @JsonProperty("accepted")
    private long accepted;
    @JsonProperty("duplicates")
    private long duplicates;
    @JsonProperty("ignored")
    private long ignored;
    @JsonProperty("rejected")
    private List<FeedbackRejection> rejected;

    FeedbackResult() {
    }

    /**
     * The number of events recorded.
     *
     * @return the accepted
     */
    public long accepted() {
        return accepted;
    }

    /**
     * The number of events already recorded.
     *
     * @return the duplicates
     */
    public long duplicates() {
        return duplicates;
    }

    /**
     * The number of events of an address on the suppression list; nothing about it is stored.
     *
     * @return the ignored
     */
    public long ignored() {
        return ignored;
    }

    /**
     * The events refused, and why.
     *
     * @return the rejected, never {@code null}; the list cannot be changed
     */
    public List<FeedbackRejection> rejected() {
        return rejected == null ? Collections.emptyList() : Collections.unmodifiableList(rejected);
    }

    /**
     * This feedbackResult as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
