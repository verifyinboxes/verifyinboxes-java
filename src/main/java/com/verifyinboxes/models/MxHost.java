package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One of a domain's mail servers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class MxHost {
    @JsonProperty("host")
    private String host;
    @JsonProperty("preference")
    private int preference;

    MxHost() {
    }

    /**
     * The server's name.
     *
     * @return the host
     */
    public String host() {
        return host;
    }

    /**
     * Its MX preference: the lowest first.
     *
     * @return the preference
     */
    public int preference() {
        return preference;
    }

    /**
     * This mxHost as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
