package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The rows a resolution did not send, and why.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ResolveSkipped {
    @JsonProperty("ineligible")
    private long ineligible;
    @JsonProperty("excluded")
    private long excluded;
    @JsonProperty("already_requested")
    private long alreadyRequested;
    @JsonProperty("duplicate")
    private long duplicate;

    ResolveSkipped() {
    }

    /**
     * Rows of a verdict other than {@code catch_all} or {@code risky}, or listed addresses not in the job.
     *
     * @return the ineligible
     */
    public long ineligible() {
        return ineligible;
    }

    /**
     * Rows of a top-level domain the service never sends to, or of a suppressed address.
     *
     * @return the excluded
     */
    public long excluded() {
        return excluded;
    }

    /**
     * Rows an earlier resolution of this job took.
     *
     * @return the already requested
     */
    public long alreadyRequested() {
        return alreadyRequested;
    }

    /**
     * Rows of an address this resolution already sends.
     *
     * @return the duplicate
     */
    public long duplicate() {
        return duplicate;
    }

    /**
     * This resolveSkipped as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
