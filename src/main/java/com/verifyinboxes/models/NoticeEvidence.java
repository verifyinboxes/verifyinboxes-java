package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What contradicted the verdict. Never whose outreach it was, unless your own ({@link #reportedByYou()}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NoticeEvidence {
    @JsonProperty("kind")
    private String kind;
    @JsonProperty("dsn_code")
    private String dsnCode;
    @JsonProperty("observed_at")
    private String observedAt;
    @JsonProperty("reported_by_you")
    private boolean reportedByYou;

    NoticeEvidence() {
    }

    /**
     * {@code hard_bounce}, {@code bounce} (a bounce that came with no status code), {@code reply} or
     * {@code delivered}.
     *
     * @return the kind
     */
    public String kind() {
        return kind;
    }

    /**
     * The bounce's status code, such as {@code 5.1.1}; else {@code null}.
     *
     * @return the dsn code
     */
    public String dsnCode() {
        return dsnCode;
    }

    /**
     * The UTC day it happened ({@code YYYY-MM-DD}), never the time.
     *
     * @return the observed at
     */
    public String observedAt() {
        return observedAt;
    }

    /**
     * Whether it is an outcome your account reported.
     *
     * @return the reported by you
     */
    public boolean reportedByYou() {
        return reportedByYou;
    }

    /**
     * This noticeEvidence as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
