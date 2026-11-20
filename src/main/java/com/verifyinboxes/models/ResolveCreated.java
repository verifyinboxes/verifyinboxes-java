package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A catch-all resolution, just made: a job of its own ({@code POST /v1/jobs/{id}/resolve}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ResolveCreated {
    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private String status;
    @JsonProperty("total")
    private long total;
    @JsonProperty("skipped")
    private ResolveSkipped skipped;

    ResolveCreated() {
    }

    /**
     * The resolution's job: wait for it with {@code lists().waitFor}.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * {@code done} when every address was answered at once, else {@code running}.
     *
     * @return the status
     */
    public String status() {
        return status;
    }

    /**
     * The number of addresses sent, each once.
     *
     * @return the total
     */
    public long total() {
        return total;
    }

    /**
     * What it left out.
     *
     * @return the skipped
     */
    public ResolveSkipped skipped() {
        return skipped;
    }

    /**
     * This resolveCreated as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
