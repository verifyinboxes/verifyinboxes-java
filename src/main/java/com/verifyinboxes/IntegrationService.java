package com.verifyinboxes;

import com.verifyinboxes.models.Integration;
import com.verifyinboxes.models.IntegrationCreated;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The addresses to paste into Instantly or Smartlead, which then report what became of your mail:
 * {@code client.integrations()}.
 */
public final class IntegrationService {
    private final Transport t;

    IntegrationService(Transport t) {
        this.t = t;
    }

    /**
     * Makes an integration. See {@link #create(String, String, String)}.
     *
     * @param provider {@code instantly} or {@code smartlead}
     * @return the integration; its {@code url()} is shown only now
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public IntegrationCreated create(String provider) {
        return create(provider, null, null);
    }

    /**
     * Makes an integration with a name of yours. See {@link #create(String, String, String)}.
     *
     * @param provider {@code instantly} or {@code smartlead}
     * @param label    your name for it, or {@code null}
     * @return the integration; its {@code url()} is shown only now
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public IntegrationCreated create(String provider, String label) {
        return create(provider, label, null);
    }

    /**
     * Makes an integration ({@code POST /v1/integrations}): an address ({@code url()}, shown only once) to paste into
     * the provider. It is not sent again once it may have arrived: after a
     * {@link com.verifyinboxes.errors.ConnectionException}, look at {@link #list()} before making another.
     *
     * @param provider      {@code instantly} or {@code smartlead}
     * @param label         your name for it, or {@code null}
     * @param signingSecret the provider's webhook signing secret, when it signs what it sends, or {@code null}
     * @return the integration; its {@code url()} is shown only now
     * @throws IllegalArgumentException when the provider is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public IntegrationCreated create(String provider, String label, String signingSecret) {
        if (provider == null || provider.isEmpty()) {
            throw new IllegalArgumentException("An integration needs its provider: instantly or smartlead");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provider", provider);
        if (label != null) {
            body.put("label", label);
        }
        if (signingSecret != null) {
            body.put("signing_secret", signingSecret);
        }
        Transport.Request r = new Transport.Request("POST", "/v1/integrations");
        r.json = body;
        r.key = Transport.newKey();
        r.retry = Transport.Retry.UNSENT;
        return t.decode(t.send(r), IntegrationCreated.class);
    }

    /**
     * The integrations ({@code GET /v1/integrations}).
     *
     * @return the integrations
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public List<Integration> list() {
        return t.decodeList(t.send(new Transport.Request("GET", "/v1/integrations")), Integration.class);
    }

    /**
     * Removes an integration ({@code DELETE /v1/integrations/{id}}). A retry that finds it gone counts as done.
     *
     * @param id the integration's id
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public void delete(String id) {
        Transport.Request r = new Transport.Request("DELETE", "/v1/integrations/" + Transport.segment("integration id", id));
        r.goneIsDone = true;
        t.send(r);
    }
}
