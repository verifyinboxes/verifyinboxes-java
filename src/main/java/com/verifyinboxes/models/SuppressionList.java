package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * The account's suppression list. Addresses are kept only as a hash, so only their number is told.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SuppressionList {
    @JsonProperty("domains")
    private List<String> domains;
    @JsonProperty("address_count")
    private long addressCount;

    SuppressionList() {
    }

    /**
     * The domains on it.
     *
     * @return the domains, never {@code null}; the list cannot be changed
     */
    public List<String> domains() {
        return domains == null ? Collections.emptyList() : Collections.unmodifiableList(domains);
    }

    /**
     * The number of addresses on it.
     *
     * @return the address count
     */
    public long addressCount() {
        return addressCount;
    }

    /**
     * This suppressionList as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
