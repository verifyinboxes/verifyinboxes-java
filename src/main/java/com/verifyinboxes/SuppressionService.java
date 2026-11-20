package com.verifyinboxes;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifyinboxes.models.SuppressionList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The addresses and domains never to check: a check of one is answered {@code invalid} ({@code suppressed}), free:
 * {@code client.suppressions()}.
 */
public final class SuppressionService {
    private final Transport t;

    SuppressionService(Transport t) {
        this.t = t;
    }

    /**
     * The domains on the list, and how many addresses (kept only as a hash) ({@code GET /v1/suppressions}).
     *
     * @return the list
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public SuppressionList list() {
        return t.decode(t.send(new Transport.Request("GET", "/v1/suppressions")), SuppressionList.class);
    }

    /**
     * Puts addresses and domains on the list, never to be checked again ({@code POST /v1/suppressions}).
     *
     * @param emails  addresses, or {@code null}
     * @param domains domains (every address on them), or {@code null}
     * @return how many were added
     * @throws IllegalArgumentException when both are empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public int add(Collection<String> emails, Collection<String> domains) {
        Transport.Request r = new Transport.Request("POST", "/v1/suppressions");
        r.json = body(emails, domains);
        r.key = Transport.newKey();
        return count(t.send(r), "added");
    }

    /**
     * Takes addresses and domains off the list ({@code DELETE /v1/suppressions}).
     *
     * @param emails  addresses, or {@code null}
     * @param domains domains, or {@code null}
     * @return how many were removed
     * @throws IllegalArgumentException when both are empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public int remove(Collection<String> emails, Collection<String> domains) {
        Transport.Request r = new Transport.Request("DELETE", "/v1/suppressions");
        r.json = body(emails, domains);
        return count(t.send(r), "removed");
    }

    private static Map<String, Object> body(Collection<String> emails, Collection<String> domains) {
        boolean someEmails = emails != null && !emails.isEmpty();
        boolean someDomains = domains != null && !domains.isEmpty();
        if (!someEmails && !someDomains) {
            throw new IllegalArgumentException("Give emails, domains or both");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (someEmails) {
            body.put("emails", new ArrayList<>(emails));
        }
        if (someDomains) {
            body.put("domains", new ArrayList<>(domains));
        }
        return body;
    }

    private int count(Transport.Response<byte[]> a, String field) {
        JsonNode n = t.json(a).path(field);
        if (!n.isIntegralNumber()) {
            throw Transport.unexpected(a.requestId, null, "The API answered without " + field);
        }
        return n.asInt();
    }
}
