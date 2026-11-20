package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A list, just made ({@code POST /v1/jobs}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class JobCreated {
    @JsonProperty("id")
    private String id;
    @JsonProperty("status")
    private String status;
    @JsonProperty("total")
    private long total;

    JobCreated() {
    }

    /**
     * The list's id: wait for it with {@code lists().waitFor}.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Where it is: {@code running}.
     *
     * @return the status
     */
    public String status() {
        return status;
    }

    /**
     * The number of addresses in it.
     *
     * @return the total
     */
    public long total() {
        return total;
    }

    /**
     * This jobCreated as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
