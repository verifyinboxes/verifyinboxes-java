package com.verifyinboxes;

import com.verifyinboxes.models.Account;
import com.verifyinboxes.models.Domain;
import com.verifyinboxes.models.FeedbackEvent;
import com.verifyinboxes.models.FeedbackResult;
import com.verifyinboxes.models.Integration;
import com.verifyinboxes.models.IntegrationCreated;
import com.verifyinboxes.models.Job;
import com.verifyinboxes.models.JobCreated;
import com.verifyinboxes.models.NoticePage;
import com.verifyinboxes.models.ResolveCreated;
import com.verifyinboxes.models.Result;
import com.verifyinboxes.models.ResultRow;
import com.verifyinboxes.models.ResultsPage;
import com.verifyinboxes.models.SuppressionList;
import com.verifyinboxes.models.Webhook;
import com.verifyinboxes.models.WebhookCreated;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * The calls of {@link VerifyInboxesClient}, each returning a {@link CompletableFuture} instead of blocking:
 * {@code client.async()}. Each call runs on the client's executor (by default its own pool of daemon threads, named
 * {@code verifyinboxes-<n>}), with the client's settings, retries included.
 *
 * <pre>{@code
 * client.async().verify("ann@example.com")
 *       .thenAccept(result -> System.out.println(result.verdict()));
 * }</pre>
 *
 * <p>A call that fails completes its future with the exception the blocking call would have thrown
 * ({@code join()} wraps it in a {@link java.util.concurrent.CompletionException}).
 *
 * <p><b>Cancelling.</b> {@code future.cancel(true)} stops the call as an interrupt stops a blocking one: nothing more
 * is started, sent again or waited for. For the calls that can make a job ({@code verify}, {@code verifyMany},
 * {@code checks().start}) it <i>blocks</i> until the call has stopped: a request that starts a check, or
 * {@code verifyMany}'s list, already sent, is let finish (within the client's timeout), so that the job it made is
 * known. The future is then cancelled; the cause of its {@link java.util.concurrent.CancellationException} names the
 * jobs the call made: {@code VerifyInboxesException.jobIdsOf(e)}. If the call had its result first, the future keeps
 * it, and {@code cancel} returns {@code false}. Every other call is cancelled at once, without waiting. A future
 * completed from outside ({@code orTimeout}, {@code complete}) stops its call too, without waiting, and without
 * naming its jobs: give the call its own timeout instead ({@link VerifyOptions#timeout},
 * {@code verifyMany(emails, timeout)}).
 */
public final class VerifyInboxesAsyncClient {
    private final VerifyInboxesClient client;
    private final Executor executor;
    private final AccountCalls account = new AccountCalls();
    private final CheckCalls checks = new CheckCalls();
    private final ListCalls lists = new ListCalls();
    private final DomainCalls domains = new DomainCalls();
    private final SuppressionCalls suppressions = new SuppressionCalls();
    private final WebhookCalls webhooks = new WebhookCalls();
    private final NoticeCalls notices = new NoticeCalls();
    private final FeedbackCalls feedback = new FeedbackCalls();
    private final IntegrationCalls integrations = new IntegrationCalls();

    VerifyInboxesAsyncClient(VerifyInboxesClient client, Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    /** Runs body on the executor, as the future of its result: a call that makes no job, cancelled at once. */
    private <T> CompletableFuture<T> run(Supplier<T> body) {
        return run(body, false);
    }

    /**
     * Runs body on the executor, as the future of its result. namesJobs: the call can make a job (a check, verifyMany's
     * list), and its cancel waits until it has stopped, so that the exception names it.
     */
    private <T> CompletableFuture<T> run(Supplier<T> body, boolean namesJobs) {
        Call<T> call = new Call<>(body, namesJobs);
        try {
            client.transport().checkOpen();
            executor.execute(call);
        } catch (IllegalStateException e) {
            call.abandon(e);
        } catch (RejectedExecutionException e) {
            call.abandon(new IllegalStateException("The client is closed, or its executor refused the call", e));
        }
        return call;
    }

    /**
     * The organisation the credential belongs to.
     *
     * @return the account calls
     */
    public AccountCalls account() {
        return account;
    }

    /**
     * One check by its job.
     *
     * @return the check calls
     */
    public CheckCalls checks() {
        return checks;
    }

    /**
     * Lists of addresses.
     *
     * @return the list calls
     */
    public ListCalls lists() {
        return lists;
    }

    /**
     * What is known about a domain.
     *
     * @return the domain calls
     */
    public DomainCalls domains() {
        return domains;
    }

    /**
     * The addresses and domains never to check.
     *
     * @return the suppression calls
     */
    public SuppressionCalls suppressions() {
        return suppressions;
    }

    /**
     * Where the API sends events.
     *
     * @return the webhook calls
     */
    public WebhookCalls webhooks() {
        return webhooks;
    }

    /**
     * The verdicts you bought, overturned since.
     *
     * @return the notice calls
     */
    public NoticeCalls notices() {
        return notices;
    }

    /**
     * What became of the mail you sent.
     *
     * @return the feedback calls
     */
    public FeedbackCalls feedback() {
        return feedback;
    }

    /**
     * The addresses that let Instantly or Smartlead report what became of your mail.
     *
     * @return the integration calls
     */
    public IntegrationCalls integrations() {
        return integrations;
    }

    /**
     * {@link VerifyInboxesClient#verify(String)}, later.
     *
     * @param email the address
     * @return the result, later
     */
    public CompletableFuture<Result> verify(String email) {
        return run(() -> client.verify(email), true);
    }

    /**
     * {@link VerifyInboxesClient#verify(String, VerifyOptions)}, later.
     *
     * @param email   the address
     * @param options the check's options
     * @return the result, later
     */
    public CompletableFuture<Result> verify(String email, VerifyOptions options) {
        return run(() -> client.verify(email, options), true);
    }

    /**
     * {@link VerifyInboxesClient#verifyMany(List)}, later.
     *
     * @param emails the addresses
     * @return the results, in the order of the addresses, later
     */
    public CompletableFuture<List<Result>> verifyMany(List<String> emails) {
        return run(() -> client.verifyMany(emails), true);
    }

    /**
     * {@link VerifyInboxesClient#verifyMany(List, Duration)}, later.
     *
     * @param emails  the addresses
     * @param timeout how long to wait for every result, from the call
     * @return the results, in the order of the addresses, later
     */
    public CompletableFuture<List<Result>> verifyMany(List<String> emails, Duration timeout) {
        return run(() -> client.verifyMany(emails, timeout), true);
    }

    /** The calls of {@link AccountService}, later. */
    public final class AccountCalls {
        private AccountCalls() {
        }

        /**
         * {@link AccountService#get()}, later.
         *
         * @return the account, later
         */
        public CompletableFuture<Account> get() {
            return run(() -> client.account().get());
        }
    }

    /** The calls of {@link CheckService}, later. */
    public final class CheckCalls {
        private CheckCalls() {
        }

        /**
         * {@link CheckService#start(String)}, later.
         *
         * @param email the address
         * @return the check, later
         */
        public CompletableFuture<Check> start(String email) {
            return run(() -> client.checks().start(email), true);
        }

        /**
         * {@link CheckService#start(String, VerifyOptions)}, later.
         *
         * @param email   the address
         * @param options the check's options
         * @return the check, later
         */
        public CompletableFuture<Check> start(String email, VerifyOptions options) {
            return run(() -> client.checks().start(email, options), true);
        }

        /**
         * {@link CheckService#get(String)}, later.
         *
         * @param jobId the check's job
         * @return the job, later
         */
        public CompletableFuture<Job> get(String jobId) {
            return run(() -> client.checks().get(jobId));
        }

        /**
         * {@link CheckService#waitFor(String)}, later.
         *
         * @param jobId the check's job
         * @return the result, later
         */
        public CompletableFuture<Result> waitFor(String jobId) {
            return run(() -> client.checks().waitFor(jobId));
        }

        /**
         * {@link CheckService#waitFor(String, Duration)}, later.
         *
         * @param jobId   the check's job
         * @param timeout how long to wait, from the call
         * @return the result, later
         */
        public CompletableFuture<Result> waitFor(String jobId, Duration timeout) {
            return run(() -> client.checks().waitFor(jobId, timeout));
        }
    }

    /** The calls of {@link ListService}, later. */
    public final class ListCalls {
        private ListCalls() {
        }

        /**
         * {@link ListService#create(CreateListParams)}, later.
         *
         * @param params what to make the list of
         * @return the list, just made, later
         */
        public CompletableFuture<JobCreated> create(CreateListParams params) {
            return run(() -> client.lists().create(params));
        }

        /**
         * {@link ListService#get(String)}, later.
         *
         * @param id the list's id
         * @return the list, later
         */
        public CompletableFuture<Job> get(String id) {
            return run(() -> client.lists().get(id));
        }

        /**
         * {@link ListService#waitFor(String)}, later.
         *
         * @param id the list's id
         * @return the list, finished, later
         */
        public CompletableFuture<Job> waitFor(String id) {
            return run(() -> client.lists().waitFor(id));
        }

        /**
         * {@link ListService#waitFor(String, Duration)}, later.
         *
         * @param id      the list's id
         * @param timeout how long to wait, from the call
         * @return the list, finished, later
         */
        public CompletableFuture<Job> waitFor(String id, Duration timeout) {
            return run(() -> client.lists().waitFor(id, timeout));
        }

        /**
         * Every row of a list, in order, read whole: {@link ListService#results(String)}, later.
         *
         * @param id the list's id
         * @return every row, later
         */
        public CompletableFuture<List<ResultRow>> results(String id) {
            return results(id, ResultsParams.defaults());
        }

        /**
         * Every row of a list, in order, read whole: {@link ListService#results(String, ResultsParams)}, later. For a
         * list too large to hold, read it a page at a time with {@link #resultsPage}.
         *
         * @param id     the list's id
         * @param params which rows, and how many to a request
         * @return every row, later
         */
        public CompletableFuture<List<ResultRow>> results(String id, ResultsParams params) {
            return run(() -> {
                List<ResultRow> rows = new ArrayList<>();
                for (ResultRow row : client.lists().results(id, params)) {
                    rows.add(row);
                }
                return Collections.unmodifiableList(rows);
            });
        }

        /**
         * {@link ListService#resultsPage(String, String)}, later.
         *
         * @param id     the list's id
         * @param cursor the cursor of the page, or {@code null} for the first
         * @return the page, later
         */
        public CompletableFuture<ResultsPage> resultsPage(String id, String cursor) {
            return run(() -> client.lists().resultsPage(id, cursor));
        }

        /**
         * {@link ListService#resultsPage(String, String, ResultsParams)}, later.
         *
         * @param id     the list's id
         * @param cursor the cursor of the page, or {@code null} for the first
         * @param params which rows, and how many
         * @return the page, later
         */
        public CompletableFuture<ResultsPage> resultsPage(String id, String cursor, ResultsParams params) {
            return run(() -> client.lists().resultsPage(id, cursor, params));
        }

        /**
         * {@link ListService#download(String, Path)}, later.
         *
         * @param id          the list's id
         * @param destination the file to write
         * @return the bytes written, later
         */
        public CompletableFuture<Long> download(String id, Path destination) {
            return run(() -> client.lists().download(id, destination));
        }

        /**
         * {@link ListService#download(String, Path, DownloadParams)}, later.
         *
         * @param id          the list's id
         * @param destination the file to write
         * @param params      which rows
         * @return the bytes written, later
         */
        public CompletableFuture<Long> download(String id, Path destination, DownloadParams params) {
            return run(() -> client.lists().download(id, destination, params));
        }

        /**
         * {@link ListService#download(String, OutputStream)}, later.
         *
         * @param id          the list's id
         * @param destination where to write it; not closed
         * @return the bytes written, later
         */
        public CompletableFuture<Long> download(String id, OutputStream destination) {
            return run(() -> client.lists().download(id, destination));
        }

        /**
         * {@link ListService#download(String, OutputStream, DownloadParams)}, later.
         *
         * @param id          the list's id
         * @param destination where to write it; not closed
         * @param params      which rows
         * @return the bytes written, later
         */
        public CompletableFuture<Long> download(String id, OutputStream destination, DownloadParams params) {
            return run(() -> client.lists().download(id, destination, params));
        }

        /**
         * {@link ListService#resolve(String)}, later.
         *
         * @param id the list's (or check's) id
         * @return the resolution, later
         */
        public CompletableFuture<ResolveCreated> resolve(String id) {
            return run(() -> client.lists().resolve(id));
        }

        /**
         * {@link ListService#resolve(String, List)}, later.
         *
         * @param id     the list's (or check's) id
         * @param emails only these addresses, or {@code null} for all of them
         * @return the resolution, later
         */
        public CompletableFuture<ResolveCreated> resolve(String id, List<String> emails) {
            return run(() -> client.lists().resolve(id, emails));
        }

        /**
         * {@link ListService#resolve(String, List, String)}, later.
         *
         * @param id             the list's (or check's) id
         * @param emails         only these addresses, or {@code null} for all of them
         * @param idempotencyKey your own Idempotency-Key, or {@code null} for a new one
         * @return the resolution, later
         */
        public CompletableFuture<ResolveCreated> resolve(String id, List<String> emails, String idempotencyKey) {
            return run(() -> client.lists().resolve(id, emails, idempotencyKey));
        }
    }

    /** The calls of {@link DomainService}, later. */
    public final class DomainCalls {
        private DomainCalls() {
        }

        /**
         * {@link DomainService#get(String)}, later.
         *
         * @param domain the domain
         * @return what is known about it, later
         */
        public CompletableFuture<Domain> get(String domain) {
            return run(() -> client.domains().get(domain));
        }
    }

    /** The calls of {@link SuppressionService}, later. */
    public final class SuppressionCalls {
        private SuppressionCalls() {
        }

        /**
         * {@link SuppressionService#list()}, later.
         *
         * @return the list, later
         */
        public CompletableFuture<SuppressionList> list() {
            return run(() -> client.suppressions().list());
        }

        /**
         * {@link SuppressionService#add(Collection, Collection)}, later.
         *
         * @param emails  addresses, or {@code null}
         * @param domains domains, or {@code null}
         * @return how many were added, later
         */
        public CompletableFuture<Integer> add(Collection<String> emails, Collection<String> domains) {
            return run(() -> client.suppressions().add(emails, domains));
        }

        /**
         * {@link SuppressionService#remove(Collection, Collection)}, later.
         *
         * @param emails  addresses, or {@code null}
         * @param domains domains, or {@code null}
         * @return how many were removed, later
         */
        public CompletableFuture<Integer> remove(Collection<String> emails, Collection<String> domains) {
            return run(() -> client.suppressions().remove(emails, domains));
        }
    }

    /** The calls of {@link WebhookService}, later. */
    public final class WebhookCalls {
        private WebhookCalls() {
        }

        /**
         * {@link WebhookService#create(String)}, later.
         *
         * @param url an {@code https://} address that gets the events
         * @return the webhook, later; its secret is shown only then
         */
        public CompletableFuture<WebhookCreated> create(String url) {
            return run(() -> client.webhooks().create(url));
        }

        /**
         * {@link WebhookService#create(String, List)}, later.
         *
         * @param url    an {@code https://} address that gets the events
         * @param events the events, or {@code null} for both
         * @return the webhook, later; its secret is shown only then
         */
        public CompletableFuture<WebhookCreated> create(String url, List<String> events) {
            return run(() -> client.webhooks().create(url, events));
        }

        /**
         * {@link WebhookService#list()}, later.
         *
         * @return the webhooks, later
         */
        public CompletableFuture<List<Webhook>> list() {
            return run(() -> client.webhooks().list());
        }

        /**
         * {@link WebhookService#delete(String)}, later.
         *
         * @param id the webhook's id
         * @return nothing, once done
         */
        public CompletableFuture<Void> delete(String id) {
            return run(() -> {
                client.webhooks().delete(id);
                return null;
            });
        }
    }

    /** The calls of {@link NoticeService}, later. */
    public final class NoticeCalls {
        private NoticeCalls() {
        }

        /**
         * {@link NoticeService#list()}, later.
         *
         * @return a page of notices, later
         */
        public CompletableFuture<NoticePage> list() {
            return run(() -> client.notices().list());
        }

        /**
         * {@link NoticeService#list(String)}, later.
         *
         * @param after the {@code nextAfter()} of the page before, or {@code null}
         * @return a page of notices, later
         */
        public CompletableFuture<NoticePage> list(String after) {
            return run(() -> client.notices().list(after));
        }

        /**
         * {@link NoticeService#list(String, Integer)}, later.
         *
         * @param after the {@code nextAfter()} of the page before, or {@code null}
         * @param limit at most this many, or {@code null} for 100
         * @return a page of notices, later
         */
        public CompletableFuture<NoticePage> list(String after, Integer limit) {
            return run(() -> client.notices().list(after, limit));
        }
    }

    /** The calls of {@link FeedbackService}, later. */
    public final class FeedbackCalls {
        private FeedbackCalls() {
        }

        /**
         * {@link FeedbackService#report(List)}, later.
         *
         * @param events what happened
         * @return what became of the events, later
         */
        public CompletableFuture<FeedbackResult> report(List<FeedbackEvent> events) {
            return run(() -> client.feedback().report(events));
        }

        /**
         * {@link FeedbackService#report(List, String)}, later.
         *
         * @param events         what happened
         * @param idempotencyKey your own Idempotency-Key, or {@code null} for a new one
         * @return what became of the events, later
         */
        public CompletableFuture<FeedbackResult> report(List<FeedbackEvent> events, String idempotencyKey) {
            return run(() -> client.feedback().report(events, idempotencyKey));
        }
    }

    /** The calls of {@link IntegrationService}, later. */
    public final class IntegrationCalls {
        private IntegrationCalls() {
        }

        /**
         * {@link IntegrationService#create(String)}, later.
         *
         * @param provider {@code instantly} or {@code smartlead}
         * @return the integration, later; its URL is shown only then
         */
        public CompletableFuture<IntegrationCreated> create(String provider) {
            return run(() -> client.integrations().create(provider));
        }

        /**
         * {@link IntegrationService#create(String, String)}, later.
         *
         * @param provider {@code instantly} or {@code smartlead}
         * @param label    your name for it, or {@code null}
         * @return the integration, later; its URL is shown only then
         */
        public CompletableFuture<IntegrationCreated> create(String provider, String label) {
            return run(() -> client.integrations().create(provider, label));
        }

        /**
         * {@link IntegrationService#create(String, String, String)}, later.
         *
         * @param provider      {@code instantly} or {@code smartlead}
         * @param label         your name for it, or {@code null}
         * @param signingSecret the provider's webhook signing secret, or {@code null}
         * @return the integration, later; its URL is shown only then
         */
        public CompletableFuture<IntegrationCreated> create(String provider, String label, String signingSecret) {
            return run(() -> client.integrations().create(provider, label, signingSecret));
        }

        /**
         * {@link IntegrationService#list()}, later.
         *
         * @return the integrations, later
         */
        public CompletableFuture<List<Integration>> list() {
            return run(() -> client.integrations().list());
        }

        /**
         * {@link IntegrationService#delete(String)}, later.
         *
         * @param id the integration's id
         * @return nothing, once done
         */
        public CompletableFuture<Void> delete(String id) {
            return run(() -> {
                client.integrations().delete(id);
                return null;
            });
        }
    }
}
