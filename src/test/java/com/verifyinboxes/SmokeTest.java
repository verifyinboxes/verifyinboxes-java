package com.verifyinboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.verifyinboxes.models.Account;
import com.verifyinboxes.models.Job;
import com.verifyinboxes.models.JobCreated;
import com.verifyinboxes.models.Result;
import com.verifyinboxes.models.ResultRow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A smoke test against the real API, run weekly by {@code .github/workflows/smoke.yml}.
 *
 * <p>Skipped unless {@code VERIFYINBOXES_SMOKE_API_KEY} holds a key (it spends a few credits).
 * {@code VERIFYINBOXES_BASE_URL} points it elsewhere than production.
 */
class SmokeTest {
    @Test
    void againstTheLiveApi() {
        String key = System.getenv("VERIFYINBOXES_SMOKE_API_KEY");
        assumeTrue(key != null && !key.trim().isEmpty(), "VERIFYINBOXES_SMOKE_API_KEY is not set");
        try (VerifyInboxesClient client = VerifyInboxesClient.builder().apiKey(key).build()) {
            Account account = client.account().get();
            assertFalse(account.name().isEmpty());

            Result result = client.verify("verify@verifyinboxes.com");
            assertNotEquals("invalid", result.verdict(), result.toString());
            assertFalse(result.jobId() == null || result.jobId().isEmpty(), result.toString());

            List<String> addresses = List.of("verify@verifyinboxes.com", "support@verifyinboxes.com");
            JobCreated created = client.lists().create(CreateListParams.emails(addresses));
            Job job = client.lists().waitFor(created.id(), Duration.ofMinutes(15));
            assertEquals("done", job.status());
            List<String> emails = new ArrayList<>();
            for (ResultRow row : client.lists().results(created.id())) {
                emails.add(row.email());
            }
            Collections.sort(emails);
            assertEquals(List.of("support@verifyinboxes.com", "verify@verifyinboxes.com"), emails);
        }
    }
}
