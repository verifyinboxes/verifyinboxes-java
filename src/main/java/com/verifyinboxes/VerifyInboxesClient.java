package com.verifyinboxes;

import com.verifyinboxes.errors.CancelledException;
import com.verifyinboxes.errors.MissingApiKeyException;
import com.verifyinboxes.models.Job;
import com.verifyinboxes.models.JobCreated;
import com.verifyinboxes.models.Result;
import com.verifyinboxes.models.ResultRow;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The client of the VerifyInboxes API.
 *
 * <pre>{@code
 * try (VerifyInboxesClient client = VerifyInboxesClient.fromEnv()) {   // reads VERIFYINBOXES_API_KEY
 *     Result result = client.verify("ann@example.com");
 *     System.out.println(result.verdict() + " " + result.safeToSend());
 * }
 * }</pre>
 *
 * <p>A client is safe to share between threads, and keeps its connections open between calls: make one and keep it.
 * Every method blocks until it is done; {@link #async()} has the same calls returning a
 * {@link java.util.concurrent.CompletableFuture}. {@link #close()} lets go of the threads it made.
 *
 * <p>Every failure is an unchecked {@link com.verifyinboxes.errors.VerifyInboxesException}: an
 * {@link com.verifyinboxes.errors.ApiException} for an error answer (its message ends with the answer's request id),
 * a {@link com.verifyinboxes.errors.ConnectionException} when no answer came. A wrong argument (an empty address, a
 * page size of 0) is an {@link IllegalArgumentException}, thrown before anything is sent. An interrupted call throws
 * a {@link CancelledException} and keeps the thread's interrupt status.
 */
public final class VerifyInboxesClient implements AutoCloseable {
    /** The environment variable the API key is read from when the builder is given none. */
    public static final String API_KEY_ENV = "VERIFYINBOXES_API_KEY";
    /** The environment variable the API's address is read from when the builder is given none. */
    public static final String BASE_URL_ENV = "VERIFYINBOXES_BASE_URL";
    /** Where the API is served. */
    public static final String DEFAULT_BASE_URL = "https://api.verifyinboxes.com";
    /** The time one attempt of one request may take, unless the builder says otherwise. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    /** How many times a request that failed in a way worth retrying is sent again, unless the builder says otherwise. */
    public static final int DEFAULT_MAX_RETRIES = 2;

    /** How long verifyMany waits unless told: 10 minutes. */
    static final Duration MANY_TIMEOUT = Duration.ofMinutes(10);
    /** verifyMany checks up to this many addresses one by one, together... */
    static final int SINGLES_AT_MOST = 10;
    /** ... started 100 ms apart. */
    static final Duration SINGLES_APART = Duration.ofMillis(100);

    private final Transport transport;
    private final String baseUrl;
    private final String shownKey;
    private final ExecutorService workers;
    private final AccountService account;
    private final CheckService checks;
    private final ListService lists;
    private final DomainService domains;
    private final SuppressionService suppressions;
    private final WebhookService webhooks;
    private final NoticeService notices;
    private final FeedbackService feedback;
    private final IntegrationService integrations;
    private final VerifyInboxesAsyncClient async;

    private VerifyInboxesClient(Builder b) {
        String key = b.apiKey == null ? "" : b.apiKey.trim();
        if (key.isEmpty()) {
            String fromEnv = b.environment.apply(API_KEY_ENV);
            key = fromEnv == null ? "" : fromEnv.trim();
        }
        if (key.isEmpty()) {
            throw new MissingApiKeyException("No API key: pass VerifyInboxesClient.builder().apiKey(...) or set the "
                    + API_KEY_ENV + " environment variable; an organisation admin makes keys in the VerifyInboxes app "
                    + "under Developers -> API keys");
        }
        if (!Transport.visibleAscii(key)) {
            // A key and a token are visible ASCII: anything else is half of a copy and paste. It is not echoed.
            throw new IllegalArgumentException("The API key holds a space, a control character or a character that is "
                    + "not ASCII: copy it again, whole");
        }
        this.baseUrl = baseUrlOf(b.baseUrl, b.environment);
        if (allMethodRetry()) {
            throw new IllegalStateException(ALL_METHOD_RETRY + " is on (as a system property, or in the JDK's "
                    + "conf/net.properties; an empty value counts as true): with it, the JDK's HttpClient sends any "
                    + "request again by itself when its connection turns out closed, a POST that makes a webhook or an "
                    + "integration included, and only this library may decide that. Set it to false, or remove it, to "
                    + "use this library");
        }
        HttpClient http = b.httpClient;
        if (http == null) {
            Duration connect = b.timeout.compareTo(Duration.ofSeconds(10)) < 0 ? b.timeout : Duration.ofSeconds(10);
            http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(connect).build();
        } else if (http.followRedirects() != HttpClient.Redirect.NEVER) {
            // The API does not redirect, and the credential is never to be sent anywhere else.
            throw new IllegalArgumentException("The HttpClient must not follow redirects: build it with "
                    + "followRedirects(HttpClient.Redirect.NEVER), the default");
        }
        String userAgent = "verifyinboxes-java/" + Version.SDK;
        if (b.userAgent != null && !b.userAgent.isEmpty()) {
            userAgent = b.userAgent + " " + userAgent;
        }
        this.shownKey = key.length() > 6 ? key.substring(0, 6) + "..." : "...";
        this.transport = new Transport(http, baseUrl, key, userAgent, b.timeout, b.maxRetries, b.sleeper, b.clock);
        this.workers = Executors.newCachedThreadPool(new Daemons());
        this.account = new AccountService(transport);
        this.checks = new CheckService(transport);
        this.lists = new ListService(transport);
        this.domains = new DomainService(transport);
        this.suppressions = new SuppressionService(transport);
        this.webhooks = new WebhookService(transport);
        this.notices = new NoticeService(transport);
        this.feedback = new FeedbackService(transport);
        this.integrations = new IntegrationService(transport);
        this.async = new VerifyInboxesAsyncClient(this, b.executor != null ? b.executor : workers);
    }

    /** The JDK's switch that makes its HttpClient resend every method, a POST included, on an expired connection. */
    static final String ALL_METHOD_RETRY = "jdk.httpclient.enableAllMethodRetry";

    /** Whether the JDK's switch is on, in this JVM: its system property, else the JDK's own net.properties. */
    private static boolean allMethodRetry() {
        String system;
        String home;
        try {
            system = System.getProperty(ALL_METHOD_RETRY);
            home = System.getProperty("java.home");
        } catch (SecurityException e) {
            return false;
        }
        return allMethodRetry(system, home == null ? null : Path.of(home, "conf", "net.properties"));
    }

    /**
     * Whether the JDK's switch is on, read as the JDK reads it (its MultiExchange, through NetProperties): the system
     * property, else the file {@code ${java.home}/conf/net.properties}; absent is off, an empty value is on, any
     * other is {@link Boolean#parseBoolean}.
     */
    static boolean allMethodRetry(String systemValue, Path netProperties) {
        String value = systemValue;
        if (value == null && netProperties != null) {
            Properties defaults = new Properties();
            try (InputStream in = Files.newInputStream(netProperties)) {
                defaults.load(in);
                value = defaults.getProperty(ALL_METHOD_RETRY);
            } catch (IOException | RuntimeException e) {
                // no file, or one that cannot be read: the JDK has no default from it either
            }
        }
        if (value == null) {
            return false;
        }
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    private static String baseUrlOf(String given, Function<String, String> environment) {
        String value = given == null ? "" : given.trim();
        if (value.isEmpty()) {
            String fromEnv = environment.apply(BASE_URL_ENV);
            value = fromEnv == null ? "" : fromEnv.trim();
        }
        if (value.isEmpty()) {
            value = DEFAULT_BASE_URL;
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null) {
                return value;
            }
        } catch (URISyntaxException e) {
            // below
        }
        throw new IllegalArgumentException("The base URL is not an http:// or https:// address, such as " + DEFAULT_BASE_URL);
    }

    /**
     * A client with the defaults, the API key of the {@code VERIFYINBOXES_API_KEY} environment variable and the
     * address of {@code VERIFYINBOXES_BASE_URL} (when it is set).
     *
     * @return the client
     * @throws MissingApiKeyException when the variable is not set
     */
    public static VerifyInboxesClient fromEnv() {
        return builder().build();
    }

    /**
     * A builder of a client.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The organisation the credential belongs to.
     *
     * @return the account calls
     */
    public AccountService account() {
        return account;
    }

    /**
     * One check by its job: started without waiting, as it is now, or waited for.
     *
     * @return the check calls
     */
    public CheckService checks() {
        return checks;
    }

    /**
     * Lists of addresses: made, waited for, read, downloaded and resolved.
     *
     * @return the list calls
     */
    public ListService lists() {
        return lists;
    }

    /**
     * What is known about a domain.
     *
     * @return the domain calls
     */
    public DomainService domains() {
        return domains;
    }

    /**
     * The addresses and domains never to check.
     *
     * @return the suppression calls
     */
    public SuppressionService suppressions() {
        return suppressions;
    }

    /**
     * Where the API sends events.
     *
     * @return the webhook calls
     */
    public WebhookService webhooks() {
        return webhooks;
    }

    /**
     * The verdicts you bought, overturned since.
     *
     * @return the notice calls
     */
    public NoticeService notices() {
        return notices;
    }

    /**
     * What became of the mail you sent.
     *
     * @return the feedback calls
     */
    public FeedbackService feedback() {
        return feedback;
    }

    /**
     * The addresses that let Instantly or Smartlead report what became of your mail.
     *
     * @return the integration calls
     */
    public IntegrationService integrations() {
        return integrations;
    }

    /**
     * The same calls, each returning a {@link java.util.concurrent.CompletableFuture} instead of blocking, run on the
     * client's executor.
     *
     * @return the asynchronous calls
     */
    public VerifyInboxesAsyncClient async() {
        return async;
    }

    /**
     * Checks one address and returns its result, waiting up to 60 seconds. See
     * {@link #verify(String, VerifyOptions)}.
     *
     * @param email the address
     * @return the result; its {@code jobId()} names the check's job
     * @throws IllegalArgumentException when the address is empty
     * @throws com.verifyinboxes.errors.WaitTimeoutException when the check was still running after 60 seconds: it
     *                                                       carries on
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Result verify(String email) {
        return verify(email, VerifyOptions.defaults());
    }

    /**
     * Checks one address and returns its result.
     *
     * <p>The API answers within about 5 seconds, or says the check is still running: then it is asked every 2 seconds
     * until done. After the options' timeout (default 60 seconds) a
     * {@link com.verifyinboxes.errors.WaitTimeoutException} names the job: the check carries on, and
     * {@code checks().waitFor(jobId)} picks it up. Every exception thrown while waiting names the check
     * ({@code jobIds()}). When the thread is interrupted, the request that starts the check, if it was already sent,
     * is let finish, so that the {@link CancelledException} names the check it made.
     *
     * <p>For many addresses use {@link #verifyMany} or a list, never a loop of {@code verify}.
     *
     * @param email   the address
     * @param options whether to check again rather than answer from the cache, how long to wait, your own
     *                Idempotency-Key
     * @return the result; its {@code jobId()} names the check's job
     * @throws IllegalArgumentException when the address is empty
     * @throws com.verifyinboxes.errors.WaitTimeoutException when the check was still running after the timeout: it
     *                                                       carries on
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Result verify(String email, VerifyOptions options) {
        VerifyOptions o = options == null ? VerifyOptions.defaults() : options;
        Transport.Deadline deadline = transport.deadline(o.getTimeout() != null ? o.getTimeout() : CheckService.CHECK_TIMEOUT);
        return verify(email, o, deadline, null);
    }

    /** Starts a check and waits for it; made is told its job as soon as the API names it. */
    private Result verify(String email, VerifyOptions options, Transport.Deadline deadline,
                          java.util.function.Consumer<String> made) {
        Check check = checks.start(email, options, made);
        if (!check.isPending()) {
            return check.result();
        }
        return checks.waitCheck(check.jobId(), deadline, false);
    }

    /**
     * Checks several addresses and returns their results in the same order, waiting up to 10 minutes. See
     * {@link #verifyMany(List, Duration)}.
     *
     * @param emails the addresses
     * @return the results, in the order of the addresses
     * @throws IllegalArgumentException when the list is empty, or an address is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails; it names every job the call made
     */
    public List<Result> verifyMany(List<String> emails) {
        return verifyMany(emails, MANY_TIMEOUT);
    }

    /**
     * Checks several addresses and returns their results in the same order.
     *
     * <p>Up to 10 are checked one by one, together, started 100 ms apart. More go as one list (one
     * {@code POST /v1/jobs}), waited for and read back: each result's {@code jobId()} is then the list's. A few
     * hundred addresses take minutes, Microsoft-hosted ones longer: raise the timeout to match, or use a list
     * yourself. Many addresses go through {@code verifyMany} or a list, never a loop of {@link #verify}: past 10
     * running checks, an organisation's checks wait behind other work.
     *
     * <p>An empty list, or an empty address, is refused before any request. Every exception, an interrupt's included,
     * names every job the call made, in input order ({@code jobIds()}): wait for them with {@code checks().waitFor}
     * or {@code lists().waitFor}. When the thread is interrupted, the requests that start checks (or the list) and
     * were already sent are let finish, so that their jobs are named; nothing more is started or waited for.
     *
     * @param emails  the addresses
     * @param timeout how long to wait for every result, from the call; above zero
     * @return the results, in the order of the addresses
     * @throws IllegalArgumentException when the list is empty, an address is empty, or the timeout is not above zero
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails; it names every job the call made
     */
    public List<Result> verifyMany(List<String> emails, Duration timeout) {
        if (emails == null || emails.isEmpty()) {
            throw new IllegalArgumentException("No addresses: give one or more");
        }
        for (int i = 0; i < emails.size(); i++) {
            String email = emails.get(i);
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("emails[" + i + "] is empty");
            }
        }
        Transport.Deadline deadline = transport.deadline(VerifyOptions.positive(timeout, "timeout"));
        List<String> addresses = Collections.unmodifiableList(new ArrayList<>(emails));
        if (addresses.size() > SINGLES_AT_MOST) {
            return manyAsList(addresses, deadline);
        }
        return manySingles(addresses, deadline);
    }

    /** verifyMany of 10 addresses or fewer: one check each, together. */
    private List<Result> manySingles(List<String> emails, Transport.Deadline deadline) {
        transport.checkOpen();
        int n = emails.size();
        AtomicReferenceArray<String> made = new AtomicReferenceArray<>(n);
        AtomicReferenceArray<Result> results = new AtomicReferenceArray<>(n);
        AtomicReferenceArray<RuntimeException> errors = new AtomicReferenceArray<>(n);
        List<Call<Void>> started = new ArrayList<>();
        RuntimeException stoppedBy = null;
        try {
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    transport.pause(SINGLES_APART);
                }
                final int index = i;
                Call<Void> call = new Call<>(() -> {
                    try {
                        results.set(index, verify(emails.get(index), VerifyOptions.defaults(), deadline,
                                id -> made.set(index, id)));
                    } catch (RuntimeException e) {
                        errors.set(index, e);
                    }
                    return null;
                }, true);
                try {
                    workers.execute(call);
                } catch (RejectedExecutionException e) {
                    throw new IllegalStateException("The client is closed", e);
                }
                started.add(call);
            }
        } catch (RuntimeException e) {
            // Interrupted (or closed) between two starts: nothing more is started, and every check started is stopped.
            stoppedBy = e;
        }
        // Every check started ends (by its result, its exception or being stopped) before anything is returned, so
        // that every job id made is known.
        boolean interrupted = stoppedBy instanceof CancelledException;
        if (stoppedBy != null) {
            started.forEach(Call::stop);
        }
        for (Call<Void> call : started) {
            while (true) {
                try {
                    call.awaitStopped();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                    started.forEach(Call::stop);
                }
            }
        }
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (made.get(i) != null) {
                ids.add(made.get(i));
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new CancelledException(stoppedBy, ids);
        }
        if (stoppedBy != null) {
            throw Transport.withJobIds(stoppedBy, ids, true);
        }
        List<Result> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (errors.get(i) != null) {
                // The first exception in input order, naming every job the call made.
                throw Transport.withJobIds(errors.get(i), ids, true);
            }
            out.add(results.get(i));
        }
        return Collections.unmodifiableList(out);
    }

    /** verifyMany of 11 addresses or more: one list, waited for and read back. */
    private List<Result> manyAsList(List<String> emails, Transport.Deadline deadline) {
        JobCreated created = lists.create(CreateListParams.emails(emails), true);
        List<String> ids = List.of(created.id());
        if (Thread.currentThread().isInterrupted()) {
            throw new CancelledException(null, ids);
        }
        try {
            Job job = lists.waitList(created.id(), deadline);
            if (!"done".equals(job.status())) {
                throw Transport.unexpected(null, ids, "The list " + created.id() + " ended " + job.status());
            }
            Map<Integer, ResultRow> rows = new HashMap<>();
            for (ResultRow row : lists.results(created.id())) {
                rows.put(row.row(), row);
            }
            List<Result> out = new ArrayList<>();
            for (int i = 0; i < emails.size(); i++) {
                // Row i is emails[i-1].
                ResultRow row = rows.get(i + 1);
                if (row == null) {
                    throw Transport.unexpected(null, ids, "The list " + created.id() + " has no result for row " + (i + 1)
                            + " (" + emails.get(i) + ")");
                }
                out.add(transport.withJobId(row, created.id()));
            }
            return Collections.unmodifiableList(out);
        } catch (RuntimeException e) {
            throw Transport.withJobIds(e, ids, true);
        }
    }

    /** The transport, for the asynchronous calls. */
    Transport transport() {
        return transport;
    }

    /**
     * Lets go of the threads the client made: no call can be made after this. A call running on them when it is
     * closed finishes. An executor given to the builder is not shut down (it is yours), and the connections of the
     * {@link HttpClient} close when it is no longer used.
     */
    @Override
    public void close() {
        transport.close();
        workers.shutdown();
    }

    /**
     * The client's address, and of its key only the first 6 characters.
     *
     * @return a description of the client
     */
    @Override
    public String toString() {
        return "VerifyInboxesClient{baseUrl=" + baseUrl + ", key=" + shownKey + "}";
    }

    /** The threads of a client: daemon threads named verifyinboxes-1, verifyinboxes-2, ... */
    private static final class Daemons implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "verifyinboxes-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * Builds a {@link VerifyInboxesClient}. Every setting is optional but the API key, which can also come from the
     * {@code VERIFYINBOXES_API_KEY} environment variable.
     */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private HttpClient httpClient;
        private Executor executor;
        private String userAgent;
        private Sleeper sleeper = Sleeper.REAL;
        private LongSupplier clock = System::nanoTime;
        private Function<String, String> environment = System::getenv;

        private Builder() {
        }

        /**
         * The credential: an API key ({@code bv_...}) or a personal access token ({@code vi_at_...}), sent as
         * {@code Authorization: Bearer <key>}. Without it, or with a blank one, the key is read from
         * {@code VERIFYINBOXES_API_KEY}. Surrounding spaces are dropped; a key that is not visible ASCII (a space or a
         * control character inside, an accented letter) is refused by {@link #build()}, as half of a copy and paste,
         * and is never repeated in the exception.
         *
         * @param apiKey the key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * The API's address, {@code http} or {@code https}. Without it, {@code VERIFYINBOXES_BASE_URL}, else
         * {@value VerifyInboxesClient#DEFAULT_BASE_URL}. One trailing slash is dropped.
         *
         * @param baseUrl the address
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The time one attempt of one request may take (default 60 seconds). An upload and a download are given at
         * least 10 minutes, the API's own limit for a whole request. How long {@code verify} waits for a result is
         * {@link VerifyOptions#timeout}'s to say.
         *
         * @param timeout above zero
         * @return this builder
         * @throws IllegalArgumentException when it is not above zero
         */
        public Builder timeout(Duration timeout) {
            this.timeout = VerifyOptions.positive(timeout, "timeout");
            return this;
        }

        /**
         * How many times a request that failed in a way worth retrying is sent again (default 2); 0 never. See the
         * README's "Retries".
         *
         * @param maxRetries 0 or more
         * @return this builder
         * @throws IllegalArgumentException when it is negative
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("The number of retries must be 0 or more");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * The {@link HttpClient} requests are sent with, for a proxy, TLS or an executor of your own. It must not
         * follow redirects (the default): the API does not send one, and the credential is never sent anywhere else.
         * Every request is sent as HTTP/1.1 whatever the client's version, so that only this library ever sends a
         * request again. Default: a client of its own.
         *
         * @param httpClient the client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Where the calls of {@link VerifyInboxesClient#async()} run. Default: a pool of daemon threads of the
         * client's own, named {@code verifyinboxes-<n>}, made as needed and let go by {@link #close()}. An executor
         * given here is not shut down by the client. The checks of {@code verifyMany} always run on the client's own
         * threads, so one thread here is enough.
         *
         * @param executor the executor
         * @return this builder
         */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Puts your product's token, such as {@code my-app/1.2}, in front of the library's own in the
         * {@code User-Agent}: {@code my-app/1.2 verifyinboxes-java/0.1.0}. An empty one adds nothing.
         *
         * @param product visible ASCII with no space
         * @return this builder
         * @throws IllegalArgumentException when it holds a space, a control character or a character that is not
         *                                  ASCII
         */
        public Builder userAgent(String product) {
            if (product != null && !product.isEmpty() && !Transport.visibleAscii(product)) {
                throw new IllegalArgumentException("The User-Agent product must be visible ASCII with no space, such as my-app/1.2");
            }
            this.userAgent = product;
            return this;
        }

        /** The waits between attempts and polls, replaced by the tests only. */
        Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        /** The clock waits are measured on (nanoseconds, as System.nanoTime), replaced by the tests only. */
        Builder clock(LongSupplier clock) {
            this.clock = clock;
            return this;
        }

        /** Where the environment variables are read, replaced by the tests only. */
        Builder environment(Function<String, String> environment) {
            this.environment = environment;
            return this;
        }

        /**
         * The client.
         *
         * @return the client
         * @throws MissingApiKeyException   when there is no API key, given or in the environment
         * @throws IllegalArgumentException when the key, the address or the HTTP client cannot be used
         * @throws IllegalStateException    when {@code jdk.httpclient.enableAllMethodRetry} is on, as the JDK reads it
         *                                  (the system property, else {@code ${java.home}/conf/net.properties}; an
         *                                  empty value is on): with it, the JDK would send a POST again by itself
         */
        public VerifyInboxesClient build() {
            return new VerifyInboxesClient(this);
        }
    }
}
