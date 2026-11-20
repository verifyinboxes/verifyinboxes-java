package com.verifyinboxes;

import static com.verifyinboxes.FakeApi.JSON;
import static com.verifyinboxes.FakeApi.answer;
import static com.verifyinboxes.FakeApi.dropped;
import static com.verifyinboxes.FakeApi.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifyinboxes.errors.ApiTimeoutException;
import com.verifyinboxes.errors.AuthenticationException;
import com.verifyinboxes.errors.CancelledException;
import com.verifyinboxes.errors.ConnectionException;
import com.verifyinboxes.errors.MissingApiKeyException;
import com.verifyinboxes.errors.NotFoundException;
import com.verifyinboxes.errors.VerifyInboxesException;
import com.verifyinboxes.errors.WaitTimeoutException;
import com.verifyinboxes.models.IntegrationCreated;
import com.verifyinboxes.models.JobCreated;
import com.verifyinboxes.models.Result;
import com.verifyinboxes.models.ResultRow;
import com.verifyinboxes.models.WebhookCreated;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ClientTest {
    static final String KEY = "bv_testtesttesttesttesttesttesttest";
    static final String JOB_ID = "019a3b71-3c4d-7e5f-8a6b-7c8d9e0f1a2b";

    private final List<AutoCloseable> closing = new ArrayList<>();

    @AfterEach
    void closeAll() throws Exception {
        Collections.reverse(closing);
        for (AutoCloseable c : closing) {
            c.close();
        }
    }

    private FakeApi api(JsonNode... exchanges) {
        return keep(new FakeApi(Arrays.asList(exchanges)));
    }

    private FakeApi api(List<JsonNode> exchanges, boolean unordered, int holdUntil, boolean gated) {
        return keep(new FakeApi(exchanges, unordered, holdUntil, gated));
    }

    private <T extends AutoCloseable> T keep(T closeable) {
        closing.add(closeable);
        return closeable;
    }

    /** A client of api with the made-up key and the given sleeper (null: the real one). */
    private VerifyInboxesClient client(FakeApi api, Sleeper sleeper) {
        VerifyInboxesClient.Builder builder = VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url());
        if (sleeper != null) {
            builder.sleeper(sleeper);
            if (sleeper instanceof PaperClock) {
                builder.clock(((PaperClock) sleeper)::nanos);
            }
        }
        return keep(builder.build());
    }

    static Map<String, Object> resultJson(String email, String verdict) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("email", email);
        r.put("verdict", verdict);
        r.put("score", null);
        r.put("safe_to_send", false);
        r.put("flags", Collections.emptyList());
        r.put("provider", "google");
        r.put("catch_all_domain", false);
        r.put("verified_at", "2026-11-18T10:12:00.123Z");
        r.put("source", "smtp");
        r.put("reason", "mailbox_not_found");
        return r;
    }

    static Map<String, Object> withJob(Map<String, Object> result, String id) {
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("job_id", id);
        return out;
    }

    static String singleJobId(int n) {
        return String.format("019a3b6e-8f0c-7d2e-9a41-5c3e2f1b0b%02d", n);
    }

    static Map<String, Object> pending(String id) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", "pending");
        p.put("job_id", id);
        return p;
    }

    static Map<String, Object> runningJob(String id, String kind) {
        Map<String, Object> j = new LinkedHashMap<>();
        j.put("id", id);
        j.put("kind", kind);
        j.put("status", "running");
        j.put("total", 1);
        j.put("done", 0);
        j.put("created_at", "2026-11-18T10:00:00Z");
        j.put("finished_at", null);
        j.put("counts", null);
        j.put("purged", false);
        return j;
    }

    static Map<String, Object> created(String id, int total) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("status", "running");
        c.put("total", total);
        return c;
    }

    static Map<String, Object> apiError(String code, String message) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("code", code);
        inner.put("message", message);
        return Collections.singletonMap("error", inner);
    }

    static Map<String, String> emailBody(String email) {
        return Collections.singletonMap("email", email);
    }

    static List<String> emails(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add("p" + i + "@example.com");
        }
        return out;
    }

    /** Waits for ok, at most 5 seconds. */
    static void until(String what, BooleanSupplier ok) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (ok.getAsBoolean()) {
                return;
            }
            Thread.sleep(5);
        }
        fail("waited 5 seconds for " + what);
    }

    /** Runs body on a thread of its own, keeping what it threw and whether it ended interrupted. */
    static final class Running {
        final Thread thread;
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final AtomicReference<Object> returned = new AtomicReference<>();
        volatile boolean interruptedAfter;

        Running(java.util.concurrent.Callable<?> body) {
            thread = new Thread(() -> {
                try {
                    returned.set(body.call());
                } catch (Throwable t) {
                    thrown.set(t);
                }
                interruptedAfter = Thread.currentThread().isInterrupted();
            });
            thread.start();
        }

        /** Whether it ended within millis. */
        boolean endsWithin(long millis) throws InterruptedException {
            thread.join(millis);
            return !thread.isAlive();
        }
    }

    // ---- the credential and the settings ----------------------------------------------------------------------------

    @Test
    void missingKeyNamesTheVariable() {
        for (String blank : new String[] {null, "", "   "}) {
            MissingApiKeyException e = assertThrows(MissingApiKeyException.class,
                    () -> VerifyInboxesClient.builder().environment(name -> blank).build());
            assertTrue(e.getMessage().contains("VERIFYINBOXES_API_KEY"), e.getMessage());
            assertTrue(e.getMessage().contains("Developers"), e.getMessage());
            assertTrue(e.getMessage().contains("API keys"), e.getMessage());
        }
        assertTrue(VerifyInboxesException.class.isAssignableFrom(MissingApiKeyException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(VerifyInboxesException.class));
    }

    @Test
    void keyAndBaseUrlFromTheEnvironment() throws Exception {
        FakeApi api = api(on("GET", "/v1/account", answer(200, account())));
        Map<String, String> env = new HashMap<>();
        env.put("VERIFYINBOXES_API_KEY", "  " + KEY + "\n");
        env.put("VERIFYINBOXES_BASE_URL", api.url() + "/");
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().environment(env::get).build());
        assertEquals("acme", client.account().get().name());
        FakeApi.Recorded got = api.requests().get(0);
        assertEquals("Bearer " + KEY, got.header("authorization"));
        assertEquals("/v1/account", got.path);
        assertEquals("application/json", got.header("accept"));
        assertNull(got.header("idempotency-key"));
    }

    static Map<String, Object> account() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", "acme");
        a.put("balance", 9940);
        a.put("reviewed", true);
        a.put("test_emails", false);
        a.put("safe_to_send_threshold", 0.8);
        return a;
    }

    @Test
    void aKeyThatIsNotVisibleAsciiIsRefusedWithoutEchoingIt() {
        for (String bad : new String[] {"bv_secret part", "bv_secret\u0007part", "bv_secrétpart", "bv_secret​part"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> VerifyInboxesClient.builder().apiKey(bad).build());
            assertFalse(e.getMessage().contains("secret"), e.getMessage());
            assertFalse(e.getMessage().contains("part"), e.getMessage());
        }
    }

    @Test
    void settingsThatMakeNoSenseAreRefused() {
        VerifyInboxesClient.Builder b = VerifyInboxesClient.builder().apiKey(KEY);
        assertThrows(IllegalArgumentException.class, () -> b.timeout(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> b.timeout(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> b.maxRetries(-1));
        assertThrows(IllegalArgumentException.class, () -> b.userAgent("my app/1.2"));
        assertThrows(IllegalArgumentException.class, () -> b.userAgent("my-app/é"));
        assertThrows(IllegalArgumentException.class, () -> VerifyInboxesClient.builder().apiKey(KEY).baseUrl("ftp://example.com").build());
        assertThrows(IllegalArgumentException.class, () -> VerifyInboxesClient.builder().apiKey(KEY).baseUrl("example.com").build());
        HttpClient following = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> VerifyInboxesClient.builder().apiKey(KEY).httpClient(following).build());
        assertTrue(e.getMessage().contains("redirect"), e.getMessage());
    }

    @Test
    void toStringHidesSecrets() throws Exception {
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl("https://api.example.com").build());
        String shown = client.toString();
        assertFalse(shown.contains(KEY), shown);
        assertFalse(shown.contains(KEY.substring(0, 7)), shown);
        assertTrue(shown.contains("bv_tes"), shown);
        assertTrue(shown.contains("https://api.example.com"), shown);

        String secret = "6f1d0c9e8b7a6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e";
        Map<String, Object> hook = new LinkedHashMap<>();
        hook.put("id", "019a4f10-2b3c-7d4e-8f5a-6b7c8d9e0f12");
        hook.put("url", "https://hooks.example.com/verifyinboxes");
        hook.put("events", List.of("job.completed"));
        hook.put("secret", secret);
        hook.put("created_at", "2026-11-18T10:00:00Z");
        WebhookCreated created = JSON.convertValue(hook, WebhookCreated.class);
        assertEquals(secret, created.secret());
        assertFalse(created.toString().contains(secret), created.toString());
        assertTrue(created.toString().contains("hooks.example.com"), created.toString());
        assertTrue(JSON.writeValueAsString(created).contains(secret), "the secret is kept in JSON");

        String url = "https://api.verifyinboxes.com/hooks/instantly/whk_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        Map<String, Object> integration = new LinkedHashMap<>();
        integration.put("id", "019a4f22-7c1d-7e2f-9a3b-4c5d6e7f8a90");
        integration.put("provider", "instantly");
        integration.put("label", null);
        integration.put("url", url);
        integration.put("created_at", "2026-11-18T10:00:00Z");
        IntegrationCreated made = JSON.convertValue(integration, IntegrationCreated.class);
        assertEquals(url, made.url());
        assertFalse(made.toString().contains("whk_"), made.toString());
        assertTrue(JSON.writeValueAsString(made).contains(url), "the URL is kept in JSON");
    }

    @Test
    void errorsNeverShowTheKey() {
        FakeApi api = api(on("GET", "/v1/account", answer(401, apiError("unauthorized", "The key is not valid."))));
        AuthenticationException e = assertThrows(AuthenticationException.class, () -> client(api, new PaperClock()).account().get());
        assertFalse(e.getMessage().contains(KEY.substring(3)), e.getMessage());
        assertFalse(e.toString().contains(KEY.substring(3)), e.toString());
        assertTrue(e.getMessage().startsWith("401 unauthorized: The key is not valid."), e.getMessage());
        assertTrue(e.getMessage().endsWith("(request id 019a7e00-0000-7000-8000-0000000000aa)"), e.getMessage());
    }

    @Test
    void unknownFieldsAreIgnoredAndUnknownValuesKept() {
        Map<String, Object> result = withJob(resultJson("ann@example.com", "spam_trap"), JOB_ID);
        result.put("reason", "a_reason_from_later");
        result.put("brand_new_field", Collections.singletonMap("x", 1));
        FakeApi api = api(on("POST", "/v1/verify", answer(200, result)));
        Result got = client(api, new PaperClock()).verify("ann@example.com");
        assertEquals("spam_trap", got.verdict());
        assertEquals("a_reason_from_later", got.reason());
        assertEquals(JOB_ID, got.jobId());
    }

    // ---- what is sent, and sent again --------------------------------------------------------------------------------

    @Test
    void neverHttp2NorAnUpgrade() {
        FakeApi api = api(on("GET", "/v1/account", answer(200, account())));
        HttpClient http2 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url()).httpClient(http2).build());
        client.account().get();
        FakeApi.Recorded got = api.requests().get(0);
        assertEquals("HTTP/1.1", got.version);
        assertNull(got.header("upgrade"));
        assertNull(got.header("http2-settings"));
    }

    @Test
    void streamBodyNotResent() throws Exception {
        // A stream that can be read once is sent once: after its answer is lost, it is not sent again (its file
        // would go empty, under the same key), whatever the retries allow.
        FakeApi api = api(on("POST", "/v1/jobs", dropped()), on("POST", "/v1/jobs", answer(201, created(JOB_ID, 2))));
        PaperClock clock = new PaperClock();
        byte[] csv = "email\nann@example.com\nbob@example.com\n".getBytes(StandardCharsets.UTF_8);
        CountingStream stream = new CountingStream(csv);
        ConnectionException e = assertThrows(ConnectionException.class,
                () -> client(api, clock).lists().create(CreateListParams.stream(stream, "prospects.csv")));
        assertNotNull(e.getMessage());
        assertEquals(1, api.count(), "the stream was sent again");
        assertTrue(clock.slept().isEmpty(), "it waited to send again: " + clock.slept());
        assertFalse(stream.closed, "the caller's stream was closed");
        FakeApi.Recorded got = api.requests().get(0);
        List<Map<String, String>> parts = got.multipart;
        assertEquals(2, parts.size());
        assertEquals("email", parts.get(0).get("value"));
        assertEquals("prospects.csv", parts.get(1).get("filename"));
        assertEquals("text/csv", parts.get(1).get("content_type"));
        assertEquals(new String(csv, StandardCharsets.UTF_8), parts.get(1).get("value"));
    }

    /** A stream that tells whether it was closed. */
    static final class CountingStream extends ByteArrayInputStream {
        volatile boolean closed;

        CountingStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    @Test
    void aFileIsReadAgainForARetry() throws Exception {
        Path dir = Files.createTempDirectory("verifyinboxes-test-");
        try {
            Path file = dir.resolve("prospects.csv");
            String csv = "name,email\nAnn,ann@example.com\nBob,bob@example.com\n";
            Files.write(file, csv.getBytes(StandardCharsets.UTF_8));
            FakeApi api = api(on("POST", "/v1/jobs", dropped()), on("POST", "/v1/jobs", answer(201, created(JOB_ID, 2))));
            PaperClock clock = new PaperClock();
            JobCreated made = client(api, clock).lists().create(CreateListParams.file(file).idempotencyKey("import-1"));
            assertEquals(JOB_ID, made.id());
            List<FakeApi.Recorded> got = api.requests();
            assertEquals(2, got.size());
            for (FakeApi.Recorded r : got) {
                assertEquals("import-1", r.header("idempotency-key"));
                assertEquals(String.valueOf(r.body.length), r.header("content-length"), "a file is sent with its length");
                assertEquals(csv, r.multipart.get(1).get("value"));
                assertEquals("prospects.csv", r.multipart.get(1).get("filename"));
            }
            assertEquals(1, clock.slept().size());
        } finally {
            ConformanceTest.deleteTree(dir);
        }
    }

    @Test
    void createTakesExactlyOneSourceAndChecksItsKey() {
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.emails(null));
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.emails(Arrays.asList("a@example.com", null)));
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.csv(null));
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.file(null));
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.stream(null, "x.csv"));
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.csv("email\n").idempotencyKey("has space"));
        assertThrows(IllegalArgumentException.class, () -> VerifyOptions.defaults().idempotencyKey("x".repeat(256)));
        assertThrows(IllegalArgumentException.class, () -> CreateListParams.emails(List.of("a@example.com")).filename("a.csv"));
    }

    @Test
    void aWebhookIsSentAgainOnlyWhenItsConnectionWasNeverMade() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
        }
        PaperClock clock = new PaperClock();
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl("http://127.0.0.1:" + port)
                .maxRetries(1).sleeper(clock).clock(clock::nanos).build());
        ConnectionException e = assertThrows(ConnectionException.class,
                () -> client.webhooks().create("https://hooks.example.com/verifyinboxes"));
        assertFalse(e instanceof ApiTimeoutException);
        assertEquals(1, clock.slept().size(), "a request that never left was not sent again");
    }

    @Test
    void eachAttemptHasItsTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            List<Socket> held = Collections.synchronizedList(new ArrayList<>());
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        held.add(silent.accept());
                    }
                } catch (IOException e) {
                    // closed
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();
            PaperClock clock = new PaperClock();
            VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY)
                    .baseUrl("http://127.0.0.1:" + silent.getLocalPort()).timeout(Duration.ofMillis(300)).maxRetries(1)
                    .sleeper(clock).clock(clock::nanos).build());
            long start = System.nanoTime();
            ApiTimeoutException e = assertThrows(ApiTimeoutException.class, () -> client.account().get());
            long took = System.nanoTime() - start;
            assertTrue(took < TimeUnit.SECONDS.toNanos(5), "took " + took / 1_000_000 + " ms");
            assertTrue(e.getMessage().contains("GET /v1/account"), e.getMessage());
            assertEquals(1, clock.slept().size(), "a timed-out GET is sent again");
            for (Socket s : held) {
                s.close();
            }
        }
    }

    // ---- downloads ---------------------------------------------------------------------------------------------------

    private static ObjectNode csvAnswer(String text, Integer cutAfter) {
        ObjectNode resp = JSON.createObjectNode();
        resp.put("status", 200);
        resp.putObject("headers").put("x-request-id", "019a7e00-0000-7000-8000-0000000000ab").put("content-type", "text/csv");
        resp.put("text", text);
        if (cutAfter != null) {
            resp.put("cut_after", cutAfter);
        }
        return resp;
    }

    private static List<String> namesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    @Test
    void downloadToPathIsAtomic() throws Exception {
        String path = "/v1/jobs/" + JOB_ID + "/download";
        String csv = "email,verdict\nann@example.com,valid\nbob@example.com,invalid\n";
        Path dir = Files.createTempDirectory("verifyinboxes-test-");
        try {
            Path target = dir.resolve("results.csv");
            Files.write(target, "old\n".getBytes(StandardCharsets.UTF_8));

            // Cut short every time: the file there is untouched, and no part file is left.
            FakeApi cut = api(on("GET", path, csvAnswer(csv, 20)), on("GET", path, csvAnswer(csv, 20)), on("GET", path, csvAnswer(csv, 20)));
            assertThrows(ConnectionException.class, () -> client(cut, new PaperClock()).lists().download(JOB_ID, target));
            assertEquals("old\n", Files.readString(target));
            assertEquals(List.of("results.csv"), namesIn(dir));

            // Refused: the same.
            FakeApi gone = api(on("GET", path, answer(410, apiError("gone", "The list's data was deleted."))));
            NotFoundException e = assertThrows(NotFoundException.class, () -> client(gone, new PaperClock()).lists().download(JOB_ID, target));
            assertEquals("gone", e.code());
            assertEquals("old\n", Files.readString(target));
            assertEquals(List.of("results.csv"), namesIn(dir));

            // Cut once, then whole: made again from the start, and only the whole download replaces the file.
            FakeApi once = api(on("GET", path, csvAnswer(csv, 20)), on("GET", path, csvAnswer(csv, null)));
            long written = client(once, new PaperClock()).lists().download(JOB_ID, target);
            assertEquals(csv.length(), written);
            assertEquals(csv, Files.readString(target));
            assertEquals(List.of("results.csv"), namesIn(dir));
            assertEquals("text/csv", once.requests().get(0).header("accept"));

            // A directory that does not exist: an error, and nothing made.
            Path nowhere = dir.resolve("missing").resolve("results.csv");
            FakeApi fine = api(on("GET", path, csvAnswer(csv, null)));
            assertThrows(java.io.UncheckedIOException.class, () -> client(fine, new PaperClock()).lists().download(JOB_ID, nowhere));
            assertEquals(List.of("results.csv"), namesIn(dir));
        } finally {
            ConformanceTest.deleteTree(dir);
        }
    }

    @Test
    void downloadToAStreamIsRetriedOnlyBeforeItsFirstByte() {
        String path = "/v1/jobs/" + JOB_ID + "/download";
        String csv = "email,verdict\nann@example.com,valid\n";

        FakeApi nothingYet = api(on("GET", path, csvAnswer(csv, 0)), on("GET", path, csvAnswer(csv, null)));
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        assertEquals(csv.length(), client(nothingYet, new PaperClock()).lists().download(JOB_ID, whole));
        assertEquals(csv, whole.toString(StandardCharsets.UTF_8));
        assertEquals(2, nothingYet.count());

        FakeApi someBytes = api(on("GET", path, csvAnswer(csv, 10)), on("GET", path, csvAnswer(csv, null)));
        ByteArrayOutputStream part = new ByteArrayOutputStream();
        assertThrows(ConnectionException.class, () -> client(someBytes, new PaperClock()).lists().download(JOB_ID, part));
        assertEquals(csv.substring(0, 10), part.toString(StandardCharsets.UTF_8));
        assertEquals(1, someBytes.count(), "sent again after the stream had bytes");
    }

    // ---- many addresses ------------------------------------------------------------------------------------------------

    @Test
    void verifyManyRunsTenAtOnce() {
        List<String> emails = emails(10);
        List<JsonNode> exchanges = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            exchanges.add(on("POST", "/v1/verify", answer(200, withJob(resultJson(emails.get(i), "invalid"), singleJobId(i + 1))),
                    emailBody(emails.get(i))));
        }
        FakeApi api = api(exchanges, true, 10, false);
        PaperClock clock = new PaperClock();
        List<Result> results = client(api, clock).verifyMany(emails);
        assertFalse(api.heldOut(), "only " + api.maxInFlight() + " checks were in flight at once");
        assertEquals(10, api.maxInFlight());
        assertEquals(10, results.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(emails.get(i), results.get(i).email());
            assertEquals(singleJobId(i + 1), results.get(i).jobId());
        }
        assertEquals(Collections.nCopies(9, Duration.ofMillis(100)), clock.slept(), "100 ms between starts");
        Set<String> keys = new HashSet<>();
        for (FakeApi.Recorded r : api.requests()) {
            keys.add(r.header("idempotency-key"));
        }
        assertEquals(10, keys.size(), "one key per check");
    }

    @Test
    void verifyManyNamesEveryJobItMadeWhenOneFails() {
        // p3 is still running when the time runs out, p7 is refused (402), the others answer at once.
        List<String> emails = emails(10);
        List<JsonNode> exchanges = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            int n = i + 1;
            ObjectNode resp = answer(200, withJob(resultJson(emails.get(i), "invalid"), singleJobId(n)));
            if (n == 3) {
                resp = answer(202, pending(singleJobId(3)));
            } else if (n == 7) {
                resp = answer(402, apiError("insufficient_credits", "top up"));
            }
            exchanges.add(on("POST", "/v1/verify", resp, emailBody(emails.get(i))));
        }
        for (int i = 0; i < 20; i++) {
            exchanges.add(on("GET", "/v1/jobs/" + singleJobId(3), answer(200, runningJob(singleJobId(3), "single"))));
        }
        FakeApi api = api(exchanges, true, 0, false);
        WaitTimeoutException e = assertThrows(WaitTimeoutException.class,
                () -> client(api, new PaperClock()).verifyMany(emails, Duration.ofSeconds(5)));
        List<String> want = new ArrayList<>();
        for (int n = 1; n <= 10; n++) {
            if (n != 7) {
                want.add(singleJobId(n));
            }
        }
        assertEquals(want, e.jobIds(), "the first error in input order, naming every job the call made");
    }

    @Test
    void verifyManyRefusesBadInputBeforeAnyRequest() {
        FakeApi api = api();
        VerifyInboxesClient client = client(api, new PaperClock());
        List<List<String>> bad = Arrays.asList(null, Collections.emptyList(), Arrays.asList("ann@example.com", ""),
                Arrays.asList("ann@example.com", "  "), Arrays.asList("ann@example.com", null));
        for (List<String> emails : bad) {
            assertThrows(IllegalArgumentException.class, () -> client.verifyMany(emails), String.valueOf(emails));
        }
        assertThrows(IllegalArgumentException.class, () -> client.verify(" "));
        assertThrows(IllegalArgumentException.class, () -> client.verifyMany(List.of("a@example.com"), Duration.ZERO));
        assertEquals(0, api.count());
    }

    @Test
    void startDoesNotWait() {
        FakeApi api = api(on("POST", "/v1/verify", answer(202, pending(JOB_ID))),
                on("POST", "/v1/verify", answer(200, withJob(resultJson("bob@example.com", "valid"), singleJobId(2)))));
        VerifyInboxesClient client = client(api, new PaperClock());
        Check first = client.checks().start("ann@example.com");
        assertTrue(first.isPending());
        assertEquals(JOB_ID, first.jobId());
        assertNull(first.result());
        Check second = client.checks().start("bob@example.com");
        assertFalse(second.isPending());
        assertEquals(singleJobId(2), second.jobId());
        assertEquals("valid", second.result().verdict());
    }

    // ---- results -----------------------------------------------------------------------------------------------------

    static Map<String, Object> page(List<String> emails, int start, String next) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            Map<String, Object> row = resultJson(emails.get(i), "invalid");
            row.put("row", start + i);
            rows.add(row);
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("data", rows);
        p.put("next_cursor", next);
        p.put("complete", true);
        return p;
    }

    @Test
    void resultsReadNothingUntilIteratedThenEveryRowOnce() {
        String path = "/v1/jobs/" + JOB_ID + "/results";
        FakeApi api = api(on("GET", path, answer(200, page(List.of("a@example.com", "b@example.com"), 1, "c1"))),
                on("GET", path, answer(200, page(List.of(), 3, "c2"))),
                on("GET", path, answer(200, page(List.of("c@example.com"), 3, null))));
        VerifyInboxesClient client = client(api, new PaperClock());
        Iterable<ResultRow> rows = client.lists().results(JOB_ID, ResultsParams.defaults().pageSize(2));
        assertEquals(0, api.count(), "asked before iterating");
        List<String> got = new ArrayList<>();
        for (ResultRow row : rows) {
            got.add(row.email() + "#" + row.row());
        }
        assertEquals(List.of("a@example.com#1", "b@example.com#2", "c@example.com#3"), got);
        List<String> queries = api.requests().stream().map(r -> r.rawQuery).collect(Collectors.toList());
        assertEquals(List.of("limit=2", "limit=2&cursor=c1", "limit=2&cursor=c2"), queries);
    }

    @Test
    void resultsStopWhenAPageWithRowsRepeatsTheCursor() {
        String path = "/v1/jobs/" + JOB_ID + "/results";
        FakeApi api = api(on("GET", path, answer(200, page(List.of("a@example.com"), 1, "c1"))),
                on("GET", path, answer(200, page(List.of("b@example.com"), 2, "c1"))));
        Iterator<ResultRow> rows = client(api, new PaperClock()).lists().results(JOB_ID).iterator();
        assertEquals("a@example.com", rows.next().email());
        assertEquals("b@example.com", rows.next().email(), "the rows of the page are given first");
        VerifyInboxesException e = assertThrows(VerifyInboxesException.class, rows::hasNext);
        assertSame(VerifyInboxesException.class, e.getClass());
        assertTrue(e.getMessage().contains("same cursor"), e.getMessage());
        assertEquals(List.of(JOB_ID), e.jobIds());
        assertEquals(2, api.count(), "the same page was asked again");
    }

    // ---- cancellation --------------------------------------------------------------------------------------------------

    @Test
    void interruptedBeforeAnythingIsSent() throws Exception {
        FakeApi api = api();
        VerifyInboxesClient client = client(api, null);
        Running run = new Running(() -> {
            Thread.currentThread().interrupt();
            return client.verify("ann@example.com");
        });
        assertTrue(run.endsWithin(5000));
        assertTrue(run.thrown.get() instanceof CancelledException, String.valueOf(run.thrown.get()));
        assertTrue(run.interruptedAfter, "the interrupt was not kept");
        assertEquals(0, api.count());
    }

    @Test
    void aCheckAlreadySentFinishesWhenInterruptedAndIsNamed() throws Exception {
        FakeApi api = api(List.of(on("POST", "/v1/verify", answer(202, pending(JOB_ID)))), false, 0, true);
        VerifyInboxesClient client = client(api, null);
        Running run = new Running(() -> client.verify("ann@example.com"));
        until("the check to be sent", () -> api.count() == 1);
        run.thread.interrupt();
        assertFalse(run.endsWithin(100), "the check sent was cut off");
        api.release();
        assertTrue(run.endsWithin(5000));
        Throwable thrown = run.thrown.get();
        assertTrue(thrown instanceof CancelledException, String.valueOf(thrown));
        assertEquals(List.of(JOB_ID), ((CancelledException) thrown).jobIds());
        assertTrue(thrown.getMessage().contains(JOB_ID), thrown.getMessage());
        assertTrue(run.interruptedAfter, "the interrupt was not kept");
        assertEquals(1, api.count(), "something was sent after the interruption");
    }

    @Test
    void interruptingAWaitNamesTheCheck() throws Exception {
        FakeApi api = api(on("POST", "/v1/verify", answer(202, pending(JOB_ID))),
                on("GET", "/v1/jobs/" + JOB_ID, answer(200, runningJob(JOB_ID, "single"))));
        Sleeper interruptAtTheSecondPause = new Sleeper() {
            private int pauses;

            @Override
            public void sleep(Duration d) throws InterruptedException {
                if (++pauses == 2) {
                    Thread.currentThread().interrupt();
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        };
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url())
                .sleeper(interruptAtTheSecondPause).build());
        Running run = new Running(() -> client.verify("ann@example.com"));
        assertTrue(run.endsWithin(10_000));
        Throwable thrown = run.thrown.get();
        assertTrue(thrown instanceof CancelledException, String.valueOf(thrown));
        assertEquals(List.of(JOB_ID), ((CancelledException) thrown).jobIds());
        assertTrue(run.interruptedAfter);
        assertEquals(2, api.count(), "polled after the interruption");
    }

    @Test
    void cancellingTheFutureOfVerifyManyLetsItsChecksFinishAndNamesEveryOne() throws Exception {
        // Even checks answer at once (200), odd ones are still running (202): either way the server made a job.
        List<String> emails = emails(3);
        List<JsonNode> exchanges = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            Object body = i % 2 == 1 ? pending(singleJobId(i + 1)) : withJob(resultJson(emails.get(i), "invalid"), singleJobId(i + 1));
            exchanges.add(on("POST", "/v1/verify", answer(i % 2 == 1 ? 202 : 200, body), emailBody(emails.get(i))));
        }
        FakeApi api = api(exchanges, true, 0, true);
        VerifyInboxesClient client = client(api, null);
        CompletableFuture<List<Result>> future = client.async().verifyMany(emails);
        until("3 checks to be sent", () -> api.count() == 3);
        Running cancelling = new Running(() -> future.cancel(true));
        assertFalse(cancelling.endsWithin(100), "cancel() returned before the checks sent had finished");
        api.release();
        assertTrue(cancelling.endsWithin(5000));
        assertEquals(Boolean.TRUE, cancelling.returned.get());
        assertTrue(future.isCancelled());
        CancellationException e = assertThrows(CancellationException.class, future::join);
        assertEquals(List.of(singleJobId(1), singleJobId(2), singleJobId(3)), VerifyInboxesException.jobIdsOf(e));
        Thread.sleep(100);
        assertEquals(3, api.count(), "a check was started or polled after the cancellation");
    }

    @Test
    void aListCreatedOnItsOwnStopsAtOnce() throws Exception {
        FakeApi api = api(List.of(on("POST", "/v1/jobs", answer(201, created(JOB_ID, 1)))), false, 0, true);
        VerifyInboxesClient client = client(api, null);
        Running run = new Running(() -> client.lists().create(CreateListParams.emails(List.of("ann@example.com"))));
        until("the list to be sent", () -> api.count() == 1);
        run.thread.interrupt();
        assertTrue(run.endsWithin(1000), "lists().create did not stop at once");
        assertTrue(run.thrown.get() instanceof CancelledException, String.valueOf(run.thrown.get()));
        assertTrue(((CancelledException) run.thrown.get()).jobIds().isEmpty());
        assertTrue(run.interruptedAfter);
    }

    @Test
    void verifyManyLetsItsListFinishAndNamesIt() throws Exception {
        List<String> emails = emails(11);
        FakeApi api = api(List.of(on("POST", "/v1/jobs", answer(201, created(JOB_ID, 11)),
                Collections.singletonMap("emails", emails))), false, 0, true);
        VerifyInboxesClient client = client(api, null);
        Running run = new Running(() -> client.verifyMany(emails));
        until("the list to be sent", () -> api.count() == 1);
        run.thread.interrupt();
        assertFalse(run.endsWithin(100), "the list sent was cut off");
        api.release();
        assertTrue(run.endsWithin(5000));
        assertTrue(run.thrown.get() instanceof CancelledException, String.valueOf(run.thrown.get()));
        assertEquals(List.of(JOB_ID), ((CancelledException) run.thrown.get()).jobIds());
        assertEquals(1, api.count());
    }

    @Test
    void aFutureCompletedFromOutsideStopsItsCall() throws Exception {
        List<JsonNode> exchanges = new ArrayList<>();
        exchanges.add(on("POST", "/v1/verify", answer(202, pending(JOB_ID))));
        for (int i = 0; i < 200; i++) {
            exchanges.add(on("GET", "/v1/jobs/" + JOB_ID, answer(200, runningJob(JOB_ID, "single"))));
        }
        FakeApi api = api(exchanges, false, 0, false);
        Sleeper quick = d -> Thread.sleep(20);
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url()).sleeper(quick).build());
        CompletableFuture<Result> future = client.async().verify("ann@example.com").orTimeout(300, TimeUnit.MILLISECONDS);
        CompletionException e = assertThrows(CompletionException.class, future::join);
        assertTrue(e.getCause() instanceof java.util.concurrent.TimeoutException, String.valueOf(e.getCause()));
        Thread.sleep(200);
        int seen = api.count();
        Thread.sleep(400);
        assertEquals(seen, api.count(), "the check was still polled after its future was completed");
    }

    @Test
    void aDirectExecutorKeepsTheCallersInterrupt() throws Exception {
        FakeApi api = api();
        VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url()).executor(Runnable::run).build());
        Running run = new Running(() -> {
            Thread.currentThread().interrupt();
            return client.async().account().get();
        });
        assertTrue(run.endsWithin(5000));
        assertTrue(run.interruptedAfter, "the caller's interrupt was cleared");
        CompletableFuture<?> future = (CompletableFuture<?>) run.returned.get();
        CompletionException e = assertThrows(CompletionException.class, future::join);
        assertTrue(e.getCause() instanceof CancelledException, String.valueOf(e.getCause()));
        assertEquals(0, api.count());
    }

    @Test
    @Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void cancellingADownloadToAStreamReturnsAtOnceAndFreesItsThread() throws Exception {
        // The stream's own reader cancels the call, then reads no more: cancel() must not wait for a write the stream
        // is blocked in, and the call's thread must not either.
        String path = "/v1/jobs/" + JOB_ID + "/download";
        StringBuilder csv = new StringBuilder("email,verdict\n");
        for (int i = 0; i < 3000; i++) {
            csv.append('p').append(i).append("@example.com,valid\n");
        }
        FakeApi api = api(on("GET", path, csvAnswer(csv.toString(), null)), on("GET", "/v1/account", answer(200, account())));
        ExecutorService one = Executors.newSingleThreadExecutor();
        java.io.PipedInputStream in = new java.io.PipedInputStream();
        java.io.PipedOutputStream out = new java.io.PipedOutputStream(in);
        try {
            VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url()).executor(one).build());
            CompletableFuture<Long> download = client.async().lists().download(JOB_ID, out);
            assertTrue(in.read(new byte[100]) > 0);
            Running cancelling = new Running(() -> download.cancel(true));
            assertTrue(cancelling.endsWithin(2000), "cancel() waited for a download");
            assertEquals(Boolean.TRUE, cancelling.returned.get());
            assertTrue(download.isCancelled());
            // The executor's one thread is free again, though the stream still holds a write.
            assertEquals("acme", client.async().account().get().get(5, TimeUnit.SECONDS).name());
        } finally {
            in.close();
            one.shutdownNow();
        }
    }

    @Test
    void aStreamIsNotReadAfterItsCallStopped() throws Exception {
        java.util.concurrent.CountDownLatch blocked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        InputStream stream = new InputStream() {
            @Override
            public int read() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int n = reads.incrementAndGet();
                if (n == 2) {
                    blocked.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                }
                byte[] row = (n == 1 ? "email\n" : "p" + n + "@example.com\n").getBytes(StandardCharsets.UTF_8);
                int k = Math.min(len, row.length);
                System.arraycopy(row, 0, b, off, k);
                return k;
            }
        };
        FakeApi api = api(on("POST", "/v1/jobs", answer(201, created(JOB_ID, 1))));
        VerifyInboxesClient client = client(api, null);
        Running run = new Running(() -> client.lists().create(CreateListParams.stream(stream, "prospects.csv")));
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS), "the stream was not read");
            run.thread.interrupt();
            assertTrue(run.endsWithin(2000), "lists().create did not stop at once");
            assertTrue(run.thrown.get() instanceof CancelledException, String.valueOf(run.thrown.get()));
        } finally {
            release.countDown();
        }
        // The read in progress ends; no other follows (on Java 11 to 15 the JDK goes on reading a request it was told
        // to cancel, unless its stream refuses).
        Thread.sleep(500);
        assertEquals(2, reads.get(), "the stream was read after its call had stopped");
    }

    @Test
    void theJdksOwnResendOfEveryMethodIsRefused() {
        String property = "jdk.httpclient.enableAllMethodRetry";
        String before = System.getProperty(property);
        try {
            // As the JDK reads it: true in any case, and the bare flag (an empty value) too.
            for (String on : new String[] {"true", "TRUE", ""}) {
                System.setProperty(property, on);
                IllegalStateException e = assertThrows(IllegalStateException.class,
                        () -> VerifyInboxesClient.builder().apiKey(KEY).build(), "'" + on + "'");
                assertTrue(e.getMessage().contains(property), e.getMessage());
                assertThrows(IllegalStateException.class,
                        () -> VerifyInboxesClient.builder().apiKey(KEY).httpClient(HttpClient.newHttpClient()).build());
            }
            for (String off : new String[] {"false", "no"}) {
                System.setProperty(property, off);
                keep(VerifyInboxesClient.builder().apiKey(KEY).build());
            }
        } finally {
            if (before == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, before);
            }
        }
    }

    @Test
    void theJdksResendSwitchIsReadAsTheJdkReadsIt() throws Exception {
        // The system property, else the JDK's own ${java.home}/conf/net.properties; an empty value is true.
        Path dir = Files.createTempDirectory("verifyinboxes-net-");
        try {
            Path file = dir.resolve("net.properties");
            Path none = dir.resolve("missing.properties");
            assertFalse(VerifyInboxesClient.allMethodRetry(null, none));
            assertTrue(VerifyInboxesClient.allMethodRetry("", none));
            assertTrue(VerifyInboxesClient.allMethodRetry("True", none));
            assertFalse(VerifyInboxesClient.allMethodRetry("yes", none));
            Files.write(file, "# a comment\njdk.httpclient.enableAllMethodRetry\n".getBytes(StandardCharsets.ISO_8859_1));
            assertTrue(VerifyInboxesClient.allMethodRetry(null, file), "a bare key in net.properties is true");
            Files.write(file, "jdk.httpclient.enableAllMethodRetry = true\n".getBytes(StandardCharsets.ISO_8859_1));
            assertTrue(VerifyInboxesClient.allMethodRetry(null, file));
            assertFalse(VerifyInboxesClient.allMethodRetry("false", file), "the system property wins over the file");
            Files.write(file, "jdk.httpclient.enableAllMethodRetry=false\n".getBytes(StandardCharsets.ISO_8859_1));
            assertFalse(VerifyInboxesClient.allMethodRetry(null, file));
            assertTrue(VerifyInboxesClient.allMethodRetry("", file));
        } finally {
            ConformanceTest.deleteTree(dir);
        }
    }

    // ---- the executor --------------------------------------------------------------------------------------------------

    @Test
    void asyncCallsRunOnDaemonThreadsClosedByClose() throws Exception {
        FakeApi api = api(on("GET", "/v1/account", answer(200, account())));
        VerifyInboxesClient client = client(api, new PaperClock());
        assertEquals("acme", client.async().account().get().get(5, TimeUnit.SECONDS).name());
        List<Thread> ours = Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().matches("verifyinboxes-\\d+")).collect(Collectors.toList());
        assertFalse(ours.isEmpty(), "no thread named verifyinboxes-<n>");
        for (Thread t : ours) {
            assertTrue(t.isDaemon(), t.getName() + " is not a daemon thread");
        }
        client.close();
        CompletableFuture<?> after = client.async().account().get();
        CompletionException e = assertThrows(CompletionException.class, after::join);
        assertTrue(e.getCause() instanceof IllegalStateException, String.valueOf(e.getCause()));
        assertThrows(IllegalStateException.class, () -> client.account().get());
    }

    @Test
    void aGivenExecutorRunsTheCallsAndIsNotShutDown() throws Exception {
        List<String> emails = emails(3);
        List<JsonNode> exchanges = new ArrayList<>();
        exchanges.add(on("GET", "/v1/account", answer(200, account())));
        for (int i = 0; i < emails.size(); i++) {
            exchanges.add(on("POST", "/v1/verify", answer(200, withJob(resultJson(emails.get(i), "invalid"), singleJobId(i + 1))),
                    emailBody(emails.get(i))));
        }
        FakeApi api = api(exchanges, true, 0, false);
        ExecutorService one = Executors.newSingleThreadExecutor();
        AtomicInteger ran = new AtomicInteger();
        Executor mine = command -> {
            ran.incrementAndGet();
            one.execute(command);
        };
        try {
            PaperClock clock = new PaperClock();
            VerifyInboxesClient client = keep(VerifyInboxesClient.builder().apiKey(KEY).baseUrl(api.url()).executor(mine)
                    .sleeper(clock).clock(clock::nanos).build());
            assertEquals("acme", client.async().account().get().get(5, TimeUnit.SECONDS).name());
            // One thread of its own is enough: the checks of verifyMany run beside it, not on it.
            assertEquals(3, client.async().verifyMany(emails).get(5, TimeUnit.SECONDS).size());
            assertEquals(2, ran.get());
            client.close();
            assertFalse(one.isShutdown(), "close() shut down an executor it was given");
        } finally {
            one.shutdownNow();
        }
    }
}
