package com.verifyinboxes;

import com.verifyinboxes.models.Domain;

/** What is known about a domain: {@code client.domains()}. */
public final class DomainService {
    private final Transport t;

    DomainService(Transport t) {
        this.t = t;
    }

    /**
     * What is known about a domain ({@code GET /v1/domains/{domain}}): its mail provider, MX hosts, whether it accepts
     * every address, its likely address pattern. It costs a credit.
     *
     * @param domain the domain, such as {@code example.com}
     * @return what is known about it
     * @throws IllegalArgumentException when the domain is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Domain get(String domain) {
        return t.decode(t.send(new Transport.Request("GET", "/v1/domains/" + Transport.segment("domain", domain))), Domain.class);
    }
}
