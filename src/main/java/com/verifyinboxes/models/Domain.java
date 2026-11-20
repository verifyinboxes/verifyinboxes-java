package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * What is known about a domain ({@code GET /v1/domains/{domain}}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Domain {
    @JsonProperty("domain")
    private String domain;
    @JsonProperty("provider")
    private String provider;
    @JsonProperty("gateway")
    private boolean gateway;
    @JsonProperty("accepts_mail")
    private boolean acceptsMail;
    @JsonProperty("mx_hosts")
    private List<MxHost> mxHosts;
    @JsonProperty("catch_all")
    private Boolean catchAll;
    @JsonProperty("catch_all_checked_at")
    private String catchAllCheckedAt;
    @JsonProperty("email_pattern")
    private String emailPattern;
    @JsonProperty("pattern_confidence")
    private Double patternConfidence;
    @JsonProperty("registered_at")
    private String registeredAt;

    Domain() {
    }

    /**
     * The domain, as asked.
     *
     * @return the domain
     */
    public String domain() {
        return domain;
    }

    /**
     * Its mail provider, from its MX hosts; {@code null} when the domain receives no mail.
     *
     * @return the provider
     */
    public String provider() {
        return provider;
    }

    /**
     * Whether its mail goes through a security gateway.
     *
     * @return the gateway
     */
    public boolean gateway() {
        return gateway;
    }

    /**
     * {@code false} for a domain that does not exist, has a null MX or no records, or whose MX hosts all do
     * not exist ({@link #mxHosts()} still lists them).
     *
     * @return the accepts mail
     */
    public boolean acceptsMail() {
        return acceptsMail;
    }

    /**
     * Its MX hosts, by ascending preference.
     *
     * @return the mx hosts, never {@code null}; the list cannot be changed
     */
    public List<MxHost> mxHosts() {
        return mxHosts == null ? Collections.emptyList() : Collections.unmodifiableList(mxHosts);
    }

    /**
     * Whether it accepts every address; {@code null} unless tested in the last 7 days.
     *
     * @return the catch all
     */
    public Boolean catchAll() {
        return catchAll;
    }

    /**
     * When the catch-all test was last done, however long ago; {@code null} if never.
     *
     * @return the catch all checked at
     */
    public String catchAllCheckedAt() {
        return catchAllCheckedAt;
    }

    /**
     * The usual shape of the domain's addresses ({@code first.last}), when known; else {@code null}.
     *
     * @return the email pattern
     */
    public String emailPattern() {
        return emailPattern;
    }

    /**
     * The share of the domain's known addresses that fit {@link #emailPattern()}; {@code null} when
     * none is known.
     *
     * @return the pattern confidence
     */
    public Double patternConfidence() {
        return patternConfidence;
    }

    /**
     * When the domain was registered, when known; else {@code null}.
     *
     * @return the registered at
     */
    public String registeredAt() {
        return registeredAt;
    }

    /**
     * This domain as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
