package com.verifyinboxes;

import com.verifyinboxes.models.Account;

/** The organisation the credential belongs to: {@code client.account()}. */
public final class AccountService {
    private final Transport t;

    AccountService(Transport t) {
        this.t = t;
    }

    /**
     * The organisation's name, balance and switches ({@code GET /v1/account}).
     *
     * @return the account
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Account get() {
        return t.decode(t.send(new Transport.Request("GET", "/v1/account")), Account.class);
    }
}
