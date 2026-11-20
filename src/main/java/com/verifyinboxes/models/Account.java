package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The organisation the credential belongs to ({@code GET /v1/account}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Account {
    @JsonProperty("name")
    private String name;
    @JsonProperty("balance")
    private long balance;
    @JsonProperty("reviewed")
    private boolean reviewed;
    @JsonProperty("test_emails")
    private boolean testEmails;
    @JsonProperty("safe_to_send_threshold")
    private double safeToSendThreshold;

    Account() {
    }

    /**
     * The organisation's name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The credits left; below zero after a refund of credits already spent.
     *
     * @return the balance
     */
    public long balance() {
        return balance;
    }

    /**
     * Whether what the account reports as feedback counts toward everyone's results.
     *
     * @return the reviewed
     */
    public boolean reviewed() {
        return reviewed;
    }

    /**
     * Whether the account may resolve catch-all addresses ({@code lists().resolve}).
     *
     * @return the test emails
     */
    public boolean testEmails() {
        return testEmails;
    }

    /**
     * The score from which a {@code catch_all} address is safe to send to.
     *
     * @return the safe to send threshold
     */
    public double safeToSendThreshold() {
        return safeToSendThreshold;
    }

    /**
     * This account as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
