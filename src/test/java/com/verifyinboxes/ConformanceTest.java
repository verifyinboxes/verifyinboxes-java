package com.verifyinboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.verifyinboxes.errors.ApiException;
import com.verifyinboxes.errors.ApiTimeoutException;
import com.verifyinboxes.errors.AuthenticationException;
import com.verifyinboxes.errors.ConflictException;
import com.verifyinboxes.errors.ConnectionException;
import com.verifyinboxes.errors.InsufficientCreditsException;
import com.verifyinboxes.errors.InvalidRequestException;
import com.verifyinboxes.errors.MissingApiKeyException;
import com.verifyinboxes.errors.NotFoundException;
import com.verifyinboxes.errors.PermissionDeniedException;
import com.verifyinboxes.errors.RateLimitException;
import com.verifyinboxes.errors.ServerException;
import com.verifyinboxes.errors.VerifyInboxesException;
import com.verifyinboxes.errors.WaitTimeoutException;
import com.verifyinboxes.models.FeedbackEvent;
import com.verifyinboxes.models.ResultRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The conformance scenarios: what every official VerifyInboxes library must send and do, replayed here against a fake
 * API.
 *
 * <p>{@code src/test/resources/conformance/scenarios.json} is shared, byte for byte, by the Python, JavaScript, Go and
 * Java libraries. Each scenario names a call ({@code call.op} and its args, by the Python library's names, mapped below
 * to this library's methods), the exchanges the server expects and answers (in order unless {@code unordered}), the
 * seconds the client must sleep ({@code sleeps}; absent: not checked) and the outcome: {@code returns} (compared as a
 * subset, all the way down: an object's keys must be there with those values, an array has exactly those elements) or
 * {@code raises} (the exception's kind, and its status, code, request id, retry_after and job_ids when given). A
 * request is checked for its method, path (as escaped on the wire), query, body (JSON compared so that true, 1 and 1.0
 * differ, or the ordered multipart parts), the headers named, its Idempotency-Key ({@code "new"}: present and unseen,
 * {@code "same"}: the one of the exchange listed just before it, {@code {"equals": ...}}, absent: none) and, always,
 * the bearer credential and the User-Agent. Every scenario runs twice, through the blocking client and through
 * {@code async()}, on a clock that moves only when the client sleeps and on a connection already used once, as a
 * long-lived client's requests are.
 *
 * <p>Each signature vector says whether {@link Webhooks#verifySignature} must accept a webhook signature.
 */
class ConformanceTest {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern VISIBLE_ASCII = Pattern.compile("[!-~]{1,255}");

    static JsonNode scenarioFile() {
        try (InputStream in = ConformanceTest.class.getResourceAsStream("/conformance/scenarios.json")) {
            assertNotNull(in, "conformance/scenarios.json is not on the test classpath");
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @TestFactory
    Stream<DynamicNode> scenarios() {
        JsonNode file = scenarioFile();
        assertEquals(1, file.path("format").asInt());
        assertTrue(file.path("scenarios").size() >= 50, "too few scenarios");
        Set<String> names = new HashSet<>();
        List<DynamicNode> tests = new ArrayList<>();
        for (JsonNode sc : file.path("scenarios")) {
            String name = sc.path("name").asText();
            assertTrue(names.add(name), "two scenarios named " + name);
            tests.add(DynamicContainer.dynamicContainer(name, Stream.of(
                    DynamicTest.dynamicTest("blocking", () -> runScenario(file, sc, false)),
                    DynamicTest.dynamicTest("async", () -> runScenario(file, sc, true)))));
        }
        return tests.stream();
    }

    @TestFactory
    Stream<DynamicTest> signatureVectors() {
        JsonNode vectors = scenarioFile().path("signatures");
        assertTrue(vectors.size() >= 20, "too few signature vectors");
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode v : vectors) {
            tests.add(DynamicTest.dynamicTest(v.path("name").asText(), () -> {
                String body = v.path("body").asText();
                String header = v.path("header").asText();
                String secret = v.path("secret").asText();
                Duration tolerance = Duration.ofSeconds(v.path("tolerance").asLong());
                Instant now = Instant.ofEpochSecond(v.path("now").asLong());
                boolean want = v.path("valid").asBoolean();
                assertEquals(want, Webhooks.verifySignature(body.getBytes(StandardCharsets.UTF_8), header, secret, tolerance, now));
                assertEquals(want, Webhooks.verifySignature(body, header, secret, tolerance, now));
            }));
        }
        return tests.stream();
    }

    // ---- running a scenario -----------------------------------------------------------------------------------------

    private static void runScenario(JsonNode file, JsonNode sc, boolean async) throws Exception {
        List<JsonNode> exchanges = new ArrayList<>();
        sc.path("exchanges").forEach(exchanges::add);
        String credential = sc.path("client").path("api_key").asText(file.path("api_key").asText());
        PaperClock clock = new PaperClock();
        Path dir = Files.createTempDirectory("verifyinboxes-conformance-");
        try (FakeApi api = new FakeApi(exchanges, sc.path("unordered").asBoolean(false), 0, false)) {
            HttpClient http = HttpClient.newHttpClient();
            warm(http, api);
            VerifyInboxesClient.Builder builder = VerifyInboxesClient.builder()
                    .apiKey(credential).baseUrl(api.url()).httpClient(http).sleeper(clock).clock(clock::nanos);
            if (sc.path("client").has("max_retries")) {
                builder.maxRetries(sc.path("client").path("max_retries").asInt());
            }
            try (VerifyInboxesClient client = builder.build()) {
                JsonNode outcome = sc.path("outcome");
                JsonNode args = sc.path("call").path("args");
                String op = sc.path("call").path("op").asText();
                Object got;
                try {
                    got = async ? runAsync(client.async(), op, args, dir) : run(client, op, args, dir);
                } catch (Throwable error) {
                    if (!outcome.has("raises")) {
                        throw new AssertionError("it threw " + error, error);
                    }
                    checkRaised(outcome.get("raises"), error);
                    checkFile(outcome, args, dir);
                    checkRequests(sc, credential, api);
                    checkSleeps(sc, clock);
                    return;
                }
                if (outcome.has("raises")) {
                    fail("it returned " + got + ", and threw nothing");
                }
                assertTrue(outcome.has("returns"), "the scenario has no outcome");
                String problem = subset(wire(got), outcome.get("returns"), "returns");
                if (problem != null) {
                    fail(problem);
                }
                checkRequests(sc, credential, api);
                checkSleeps(sc, clock);
            }
        } finally {
            deleteTree(dir);
        }
    }

    /** One request to api on http, its answer read, so that the next request reuses that connection. */
    static void warm(HttpClient http, FakeApi api) throws IOException, InterruptedException {
        HttpResponse<Void> resp = http.send(HttpRequest.newBuilder(URI.create(api.url() + FakeApi.WARM_PATH))
                .version(HttpClient.Version.HTTP_1_1).build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(204, resp.statusCode());
    }

    /** What a call returned, as the API names it. */
    static JsonNode wire(Object got) {
        if (got == null) {
            return NullNode.getInstance();
        }
        if (got instanceof Check) {
            Check check = (Check) got;
            if (check.isPending()) {
                return JSON.createObjectNode().put("status", "pending").put("job_id", check.jobId());
            }
            return JSON.valueToTree(check.result());
        }
        return JSON.valueToTree(got);
    }

    private static Duration seconds(JsonNode args, String name) {
        if (!args.has(name)) {
            return null;
        }
        return Duration.ofNanos((long) (args.get(name).asDouble() * 1e9));
    }

    private static String str(JsonNode args, String name) {
        return args.has(name) && !args.get(name).isNull() ? args.get(name).asText() : null;
    }

    private static List<String> strings(JsonNode args, String name) {
        if (!args.has(name)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        args.get(name).forEach(v -> out.add(v.asText()));
        return out;
    }

    private static VerifyOptions verifyOptions(JsonNode args) {
        VerifyOptions options = VerifyOptions.defaults();
        if (args.path("force_recheck").asBoolean(false)) {
            options = options.forceRecheck(true);
        }
        if (args.has("timeout")) {
            options = options.timeout(seconds(args, "timeout"));
        }
        if (args.has("idempotency_key")) {
            options = options.idempotencyKey(str(args, "idempotency_key"));
        }
        return options;
    }

    private static CreateListParams createParams(JsonNode args) {
        CreateListParams params = args.has("emails") ? CreateListParams.emails(strings(args, "emails"))
                : CreateListParams.csv(str(args, "csv"));
        if (args.has("email_column")) {
            params = params.emailColumn(str(args, "email_column"));
        }
        if (args.has("filename")) {
            params = params.filename(str(args, "filename"));
        }
        if (args.has("idempotency_key")) {
            params = params.idempotencyKey(str(args, "idempotency_key"));
        }
        return params;
    }

    private static ResultsParams resultsParams(JsonNode args) {
        ResultsParams params = ResultsParams.defaults();
        if (args.has("page_size")) {
            params = params.pageSize(args.get("page_size").asInt());
        }
        if (args.path("only_safe_to_send").asBoolean(false)) {
            params = params.onlySafeToSend(true);
        }
        if (args.has("verdicts")) {
            params = params.verdicts(strings(args, "verdicts"));
        }
        if (args.path("partial").asBoolean(false)) {
            params = params.partial(true);
        }
        return params;
    }

    private static DownloadParams downloadParams(JsonNode args) {
        DownloadParams params = DownloadParams.defaults();
        if (args.path("only_safe_to_send").asBoolean(false)) {
            params = params.onlySafeToSend(true);
        }
        if (args.has("verdicts")) {
            params = params.verdicts(strings(args, "verdicts"));
        }
        if (args.path("partial").asBoolean(false)) {
            params = params.partial(true);
        }
        return params;
    }

    private static List<FeedbackEvent> events(JsonNode args) {
        return JSON.convertValue(args.get("events"), new TypeReference<List<FeedbackEvent>>() { });
    }

    private static Path destination(JsonNode args, Path dir) throws IOException {
        Path path = dir.resolve("results.csv");
        Files.write(path, args.path("before").asText().getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static Map<String, Object> fileOf(Path path) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("file", new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        return out;
    }

    private static Map<String, Object> rowsOf(Iterable<ResultRow> rows) {
        List<String> emails = new ArrayList<>();
        for (ResultRow row : rows) {
            emails.add(row.email());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", emails);
        return out;
    }

    /** Calls op on the blocking client with the scenario's arguments, under this library's names. */
    private static Object run(VerifyInboxesClient c, String op, JsonNode a, Path dir) throws IOException {
        switch (op) {
            case "account.get":
                return c.account().get();
            case "verify":
                if (a.has("wait") && !a.get("wait").asBoolean()) {
                    return c.checks().start(str(a, "email"), verifyOptions(a));
                }
                return c.verify(str(a, "email"), verifyOptions(a));
            case "checks.get":
                return c.checks().get(str(a, "job_id"));
            case "checks.wait":
                return a.has("timeout") ? c.checks().waitFor(str(a, "job_id"), seconds(a, "timeout"))
                        : c.checks().waitFor(str(a, "job_id"));
            case "verify_many":
                return a.has("timeout") ? c.verifyMany(strings(a, "emails"), seconds(a, "timeout"))
                        : c.verifyMany(strings(a, "emails"));
            case "lists.create":
                return c.lists().create(createParams(a));
            case "lists.get":
                return c.lists().get(str(a, "id"));
            case "lists.wait":
                return a.has("timeout") ? c.lists().waitFor(str(a, "id"), seconds(a, "timeout"))
                        : c.lists().waitFor(str(a, "id"));
            case "lists.results":
                return rowsOf(c.lists().results(str(a, "id"), resultsParams(a)));
            case "lists.download": {
                Path path = destination(a, dir);
                c.lists().download(str(a, "id"), path, downloadParams(a));
                return fileOf(path);
            }
            case "lists.resolve":
                return c.lists().resolve(str(a, "id"), strings(a, "emails"), str(a, "idempotency_key"));
            case "domains.get":
                return c.domains().get(str(a, "domain"));
            case "suppressions.list":
                return c.suppressions().list();
            case "suppressions.add":
                return c.suppressions().add(strings(a, "emails"), strings(a, "domains"));
            case "suppressions.remove":
                return c.suppressions().remove(strings(a, "emails"), strings(a, "domains"));
            case "webhooks.create":
                return c.webhooks().create(str(a, "url"), strings(a, "events"));
            case "webhooks.list":
                return c.webhooks().list();
            case "webhooks.delete":
                c.webhooks().delete(str(a, "id"));
                return null;
            case "notices.list":
                return c.notices().list(str(a, "after"), a.has("limit") ? a.get("limit").asInt() : null);
            case "feedback.report":
                return c.feedback().report(events(a), str(a, "idempotency_key"));
            case "integrations.create":
                return c.integrations().create(str(a, "provider"), str(a, "label"), str(a, "signing_secret"));
            case "integrations.list":
                return c.integrations().list();
            case "integrations.delete":
                c.integrations().delete(str(a, "id"));
                return null;
            default:
                throw new AssertionError("no such op in this runner: " + op);
        }
    }

    /** Calls op through {@code async()}, and waits for its future. */
    private static Object runAsync(VerifyInboxesAsyncClient c, String op, JsonNode a, Path dir) throws Throwable {
        CompletableFuture<?> future;
        Path path = null;
        switch (op) {
            case "account.get":
                future = c.account().get();
                break;
            case "verify":
                future = a.has("wait") && !a.get("wait").asBoolean()
                        ? c.checks().start(str(a, "email"), verifyOptions(a)) : c.verify(str(a, "email"), verifyOptions(a));
                break;
            case "checks.get":
                future = c.checks().get(str(a, "job_id"));
                break;
            case "checks.wait":
                future = a.has("timeout") ? c.checks().waitFor(str(a, "job_id"), seconds(a, "timeout"))
                        : c.checks().waitFor(str(a, "job_id"));
                break;
            case "verify_many":
                future = a.has("timeout") ? c.verifyMany(strings(a, "emails"), seconds(a, "timeout"))
                        : c.verifyMany(strings(a, "emails"));
                break;
            case "lists.create":
                future = c.lists().create(createParams(a));
                break;
            case "lists.get":
                future = c.lists().get(str(a, "id"));
                break;
            case "lists.wait":
                future = a.has("timeout") ? c.lists().waitFor(str(a, "id"), seconds(a, "timeout"))
                        : c.lists().waitFor(str(a, "id"));
                break;
            case "lists.results":
                future = c.lists().results(str(a, "id"), resultsParams(a)).thenApply(ConformanceTest::rowsOf);
                break;
            case "lists.download":
                path = destination(a, dir);
                future = c.lists().download(str(a, "id"), path, downloadParams(a));
                break;
            case "lists.resolve":
                future = c.lists().resolve(str(a, "id"), strings(a, "emails"), str(a, "idempotency_key"));
                break;
            case "domains.get":
                future = c.domains().get(str(a, "domain"));
                break;
            case "suppressions.list":
                future = c.suppressions().list();
                break;
            case "suppressions.add":
                future = c.suppressions().add(strings(a, "emails"), strings(a, "domains"));
                break;
            case "suppressions.remove":
                future = c.suppressions().remove(strings(a, "emails"), strings(a, "domains"));
                break;
            case "webhooks.create":
                future = c.webhooks().create(str(a, "url"), strings(a, "events"));
                break;
            case "webhooks.list":
                future = c.webhooks().list();
                break;
            case "webhooks.delete":
                future = c.webhooks().delete(str(a, "id"));
                break;
            case "notices.list":
                future = c.notices().list(str(a, "after"), a.has("limit") ? a.get("limit").asInt() : null);
                break;
            case "feedback.report":
                future = c.feedback().report(events(a), str(a, "idempotency_key"));
                break;
            case "integrations.create":
                future = c.integrations().create(str(a, "provider"), str(a, "label"), str(a, "signing_secret"));
                break;
            case "integrations.list":
                future = c.integrations().list();
                break;
            case "integrations.delete":
                future = c.integrations().delete(str(a, "id"));
                break;
            default:
                throw new AssertionError("no such op in this runner: " + op);
        }
        Object got;
        try {
            got = future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
        return path != null ? fileOf(path) : got;
    }

    // ---- checking what happened -------------------------------------------------------------------------------------

    /** Where got does not hold want, all the way down; null when it does. */
    static String subset(JsonNode got, JsonNode want, String where) {
        if (want.isNumber()) {
            if (!got.isNumber() || got.decimalValue().compareTo(want.decimalValue()) != 0) {
                return where + ": " + got + ", not " + want;
            }
            if (want.isIntegralNumber() && !got.isIntegralNumber()) {
                return where + ": " + got + " is not whole";
            }
            return null;
        }
        if (want.isArray()) {
            if (!got.isArray()) {
                return where + ": " + got + " is not an array";
            }
            if (got.size() != want.size()) {
                return where + ": " + got.size() + " elements, not " + want.size();
            }
            for (int i = 0; i < want.size(); i++) {
                String problem = subset(got.get(i), want.get(i), where + "[" + i + "]");
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        if (want.isObject()) {
            if (!got.isObject()) {
                return where + ": " + got + " is not an object";
            }
            Iterator<Map.Entry<String, JsonNode>> fields = want.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                if (!got.has(f.getKey())) {
                    return where + ": no " + f.getKey();
                }
                String problem = subset(got.get(f.getKey()), f.getValue(), where + "." + f.getKey());
                if (problem != null) {
                    return problem;
                }
            }
            return null;
        }
        return got.equals(want) ? null : where + ": " + got + ", not " + want;
    }

    private static void checkRequests(JsonNode sc, String credential, FakeApi api) {
        for (FakeApi.Recorded r : api.unexpected()) {
            fail("a request the scenario does not have: " + r);
        }
        List<FakeApi.Recorded> matched = api.matched();
        List<String> keys = new ArrayList<>();
        JsonNode exchanges = sc.path("exchanges");
        for (int i = 0; i < exchanges.size(); i++) {
            JsonNode want = exchanges.get(i).path("request");
            String where = "exchange " + i + " (" + want.path("method").asText() + " " + want.path("path").asText() + ")";
            FakeApi.Recorded got = matched.get(i);
            assertNotNull(got, where + " was never requested");
            assertEquals(want.path("method").asText(), got.method, where);
            assertEquals(want.path("path").asText(), got.path, where);
            assertEquals("HTTP/1.1", got.version, where + ": the protocol");
            if (want.has("query")) {
                Map<String, String> wantQuery = new LinkedHashMap<>();
                want.get("query").fields().forEachRemaining(f -> wantQuery.put(f.getKey(), f.getValue().asText()));
                assertEquals(wantQuery, parseQuery(got.rawQuery), where + ": the query");
            } else {
                assertEquals("", got.rawQuery, where + ": a query");
            }
            if (want.has("json")) {
                assertNotNull(got.json, where + ": the body is not JSON: " + got.bodyText());
                assertEquals(want.get("json"), got.json, where + ": the body");
            } else if (want.has("multipart")) {
                assertEquals(FakeApi.partsOf(want.get("multipart")), got.multipart, where + ": the parts");
            } else {
                assertEquals(0, got.body.length, where + ": a body " + got.bodyText());
            }
            want.path("headers").fields().forEachRemaining(h ->
                    assertEquals(h.getValue().asText(), got.header(h.getKey()), where + ": " + h.getKey()));
            assertEquals("Bearer " + credential, got.header("authorization"), where + ": the credential sent");
            String ua = got.header("user-agent");
            assertTrue(ua != null && ua.startsWith("verifyinboxes-java/" + Version.SDK), where + ": User-Agent " + ua);
            List<String> sent = got.headers.get("idempotency-key");
            String key = sent == null ? null : sent.get(0);
            assertTrue(sent == null || sent.size() == 1, where + ": more than one Idempotency-Key");
            JsonNode rule = want.get("idempotency_key");
            if (rule == null) {
                assertEquals(null, key, where + ": an Idempotency-Key");
            } else if (rule.isTextual() && rule.asText().equals("new")) {
                assertTrue(key != null && VISIBLE_ASCII.matcher(key).matches(), where + ": Idempotency-Key " + key);
                assertFalse(keys.contains(key), where + ": an Idempotency-Key used earlier");
            } else if (rule.isTextual() && rule.asText().equals("same")) {
                assertTrue(i > 0 && key != null && key.equals(keys.get(i - 1)),
                        where + ": not the same Idempotency-Key as the exchange before");
            } else {
                assertEquals(rule.path("equals").asText(), key, where + ": Idempotency-Key");
            }
            keys.add(key);
        }
    }

    /** A query as an exact map: each name once. */
    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            assertFalse(out.containsKey(name), name + " more than once in the query");
            out.put(name, value);
        }
        return out;
    }

    private static void checkSleeps(JsonNode sc, PaperClock clock) {
        if (!sc.has("sleeps")) {
            return;
        }
        JsonNode want = sc.get("sleeps");
        List<Duration> slept = clock.slept();
        assertEquals(want.size(), slept.size(), "slept " + slept + ", not " + want);
        for (int i = 0; i < want.size(); i++) {
            double got = slept.get(i).toNanos() / 1e9;
            JsonNode w = want.get(i);
            if (w.isNumber()) {
                assertEquals(w.asDouble(), got, 1e-6, "sleep " + i);
            } else {
                double min = w.path("min").asDouble();
                double max = w.path("max").asDouble();
                assertTrue(got >= min - 1e-9 && got <= max + 1e-9, "sleep " + i + ": " + got + "s, not in [" + min + ", " + max + "]");
            }
        }
    }

    /** The class each kind of error is, exactly. */
    private static final Map<String, Class<?>> KINDS = new LinkedHashMap<>();

    static {
        KINDS.put("invalid_request", InvalidRequestException.class);
        KINDS.put("authentication", AuthenticationException.class);
        KINDS.put("insufficient_credits", InsufficientCreditsException.class);
        KINDS.put("permission", PermissionDeniedException.class);
        KINDS.put("not_found", NotFoundException.class);
        KINDS.put("conflict", ConflictException.class);
        KINDS.put("rate_limit", RateLimitException.class);
        KINDS.put("server", ServerException.class);
        KINDS.put("api", ApiException.class);
        KINDS.put("connection", ConnectionException.class);
        KINDS.put("timeout", ApiTimeoutException.class);
        KINDS.put("wait_timeout", WaitTimeoutException.class);
        KINDS.put("missing_api_key", MissingApiKeyException.class);
        KINDS.put("sdk", VerifyInboxesException.class);
    }

    private static void checkRaised(JsonNode want, Throwable error) {
        String kind = want.path("kind").asText();
        Class<?> type = KINDS.get(kind);
        assertNotNull(type, "no such kind: " + kind);
        assertEquals(type, error.getClass(), "threw " + error + ", not of the kind " + kind);
        VerifyInboxesException e = (VerifyInboxesException) error;
        ApiException api = e instanceof ApiException ? (ApiException) e : null;
        if (want.has("status")) {
            assertNotNull(api, "no status: " + e);
            assertEquals(want.get("status").asInt(), api.statusCode(), "status");
        }
        if (want.has("code")) {
            assertNotNull(api, "no code: " + e);
            assertEquals(want.get("code").isNull() ? null : want.get("code").asText(), api.code(), "code");
        }
        if (want.has("request_id")) {
            assertEquals(want.get("request_id").asText(), e.requestId(), "request id");
        }
        if (want.has("retry_after")) {
            assertNotNull(api, "no retry_after: " + e);
            assertEquals(Duration.ofNanos((long) (want.get("retry_after").asDouble() * 1e9)), api.retryAfter(), "retry after");
        }
        if (want.has("job_ids")) {
            List<String> ids = new ArrayList<>();
            want.get("job_ids").forEach(id -> ids.add(id.asText()));
            assertEquals(ids, e.jobIds(), "job ids");
            assertEquals(ids, VerifyInboxesException.jobIdsOf(new RuntimeException(e)), "job ids, through a cause");
        }
        if (want.path("message_has_request_id").asBoolean(false)) {
            String suffix = "(request id " + want.path("request_id").asText() + ")";
            assertTrue(e.getMessage().endsWith(suffix), "the message does not end with its request id: " + e.getMessage());
        }
    }

    private static void checkFile(JsonNode outcome, JsonNode args, Path dir) throws IOException {
        if (!outcome.path("file").asText().equals("unchanged")) {
            return;
        }
        assertEquals(args.path("before").asText(), new String(Files.readAllBytes(dir.resolve("results.csv")), StandardCharsets.UTF_8),
                "the file was changed");
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names = files.map(p -> p.getFileName().toString()).collect(Collectors.toList());
            assertEquals(List.of("results.csv"), names, "a part file was left behind");
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> all = walk.sorted((x, y) -> y.getNameCount() - x.getNameCount()).collect(Collectors.toList());
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        }
    }

    // ---- the comparison itself --------------------------------------------------------------------------------------

    @Test
    void comparesJsonByType() throws IOException {
        assertEquals(JSON.readTree("{\"a\":[1,{\"b\":\"x\"}],\"c\":null}"), JSON.readTree("{\"a\": [1, {\"b\": \"x\"}], \"c\": null}"));
        String[][] differ = {
            {"1", "true"}, {"true", "1"}, {"1", "1.0"}, {"0", "false"}, {"{\"a\":[1]}", "{\"a\":[true]}"}, {"[1.0]", "[1]"},
            {"{\"a\":1}", "{\"a\":1,\"b\":2}"}, {"\"1\"", "1"},
        };
        for (String[] pair : differ) {
            assertFalse(JSON.readTree(pair[0]).equals(JSON.readTree(pair[1])), pair[0] + " equals " + pair[1]);
        }
        assertEquals(JSON.readTree("1.0"), JSON.readTree("1.00"));
        assertNotNull(subset(JSON.readTree("1.5"), JSON.readTree("1"), "x"));
        assertNotNull(subset(JSON.readTree("1.0"), JSON.readTree("1"), "x"));
        assertEquals(null, subset(JSON.readTree("2"), JSON.readTree("2"), "x"));
        assertEquals(null, subset(JSON.readTree("{\"a\":1,\"b\":[2]}"), JSON.readTree("{\"a\":1}"), "x"));
        assertNotNull(subset(JSON.readTree("[1,2]"), JSON.readTree("[1]"), "x"));
    }
}
