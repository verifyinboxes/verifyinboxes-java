package com.verifyinboxes.examples;

import com.verifyinboxes.VerifyInboxesClient;
import com.verifyinboxes.errors.VerifyInboxesException;
import com.verifyinboxes.models.Result;
import java.util.Arrays;
import java.util.List;

/**
 * Checks one address, then several at once.
 *
 * <pre>
 * export VERIFYINBOXES_API_KEY="bv_..."
 * java -cp ... com.verifyinboxes.examples.Quickstart ann@example.com bob@example.com
 * </pre>
 */
public final class Quickstart {
    private Quickstart() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: Quickstart ADDRESS [ADDRESS ...]");
            System.exit(2);
        }
        int code;
        try (VerifyInboxesClient client = VerifyInboxesClient.fromEnv()) { // reads VERIFYINBOXES_API_KEY
            code = run(client, Arrays.asList(args));
        } catch (VerifyInboxesException e) {
            System.err.println(e.getMessage());
            code = 1;
        }
        System.exit(code);
    }

    static int run(VerifyInboxesClient client, List<String> addresses) {
        try {
            Result result = client.verify(addresses.get(0));
            System.out.printf("%s: %s (%s), safe to send: %b%n", result.email(), result.verdict(), result.reason(),
                    result.safeToSend());

            // Several addresses: verifyMany, never a loop of verify. Up to 10 are checked together; more go as one list.
            List<String> rest = addresses.subList(1, addresses.size());
            if (!rest.isEmpty()) {
                for (Result each : client.verifyMany(rest)) {
                    System.out.printf("%s: %s%n", each.email(), each.verdict());
                }
            }
            return 0;
        } catch (VerifyInboxesException e) {
            System.err.println(e.getMessage()); // an API error's message ends with its request id
            if (!e.jobIds().isEmpty()) {
                // Each job carries on and its credits are held: wait for it again with client.checks().waitFor(id)
                // (a list's with client.lists().waitFor).
                System.err.println("jobs made: " + String.join(", ", e.jobIds()));
            }
            return 1;
        }
    }
}
