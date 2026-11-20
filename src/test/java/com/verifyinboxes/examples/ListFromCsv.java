package com.verifyinboxes.examples;

import com.verifyinboxes.CreateListParams;
import com.verifyinboxes.VerifyInboxesClient;
import com.verifyinboxes.models.Job;
import com.verifyinboxes.models.JobCreated;
import com.verifyinboxes.models.ResultRow;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verifies a CSV of addresses as a list: starts the list, waits for it, reads the results and downloads the CSV with
 * them.
 *
 * <pre>
 * export VERIFYINBOXES_API_KEY="bv_..."
 * java -cp ... com.verifyinboxes.examples.ListFromCsv prospects.csv email
 * </pre>
 */
public final class ListFromCsv {
    private ListFromCsv() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: ListFromCsv FILE.csv [EMAIL_COLUMN]");
            System.exit(2);
        }
        String column = args.length > 1 ? args[1] : "email";
        try {
            run(Path.of(args[0]), column);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage()); // an API error's message ends with its request id
            System.exit(1);
        }
    }

    static void run(Path file, String column) {
        try (VerifyInboxesClient client = VerifyInboxesClient.fromEnv()) {
            JobCreated created = client.lists().create(CreateListParams.file(file).emailColumn(column));
            System.out.printf("list %s: %d addresses%n", created.id(), created.total());

            // A few hundred addresses take minutes, Microsoft-hosted ones longer. If this stops, the list carries on:
            // wait for it again with client.lists().waitFor(created.id()).
            Job job = client.lists().waitFor(created.id());
            System.out.printf("%s: %d of %d done%n", job.status(), job.done(), job.total());

            Map<String, Integer> byVerdict = new TreeMap<>();
            int safe = 0;
            for (ResultRow row : client.lists().results(created.id())) {
                byVerdict.merge(row.verdict(), 1, Integer::sum);
                if (row.safeToSend()) {
                    safe++;
                }
            }
            System.out.printf("%d safe to send; by verdict: %s%n", safe, byVerdict);

            long written = client.lists().download(created.id(), Path.of("results.csv"));
            System.out.printf("saved results.csv (%d bytes)%n", written);
        }
    }
}
