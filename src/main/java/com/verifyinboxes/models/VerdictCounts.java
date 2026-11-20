package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How many rows have each verdict.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class VerdictCounts {
    @JsonProperty("valid")
    private long valid;
    @JsonProperty("invalid")
    private long invalid;
    @JsonProperty("catch_all")
    private long catchAll;
    @JsonProperty("risky")
    private long risky;
    @JsonProperty("unknown")
    private long unknown;

    VerdictCounts() {
    }

    /**
     * The number of {@code valid} rows.
     *
     * @return the valid
     */
    public long valid() {
        return valid;
    }

    /**
     * The number of {@code invalid} rows.
     *
     * @return the invalid
     */
    public long invalid() {
        return invalid;
    }

    /**
     * The number of {@code catch_all} rows.
     *
     * @return the catch all
     */
    public long catchAll() {
        return catchAll;
    }

    /**
     * The number of {@code risky} rows.
     *
     * @return the risky
     */
    public long risky() {
        return risky;
    }

    /**
     * The number of {@code unknown} rows.
     *
     * @return the unknown
     */
    public long unknown() {
        return unknown;
    }

    /**
     * This verdictCounts as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
