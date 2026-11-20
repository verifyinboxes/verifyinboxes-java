package com.verifyinboxes;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifyinboxes.errors.VerifyInboxesException;
import com.verifyinboxes.errors.WaitTimeoutException;
import com.verifyinboxes.models.Job;
import com.verifyinboxes.models.JobCreated;
import com.verifyinboxes.models.ResolveCreated;
import com.verifyinboxes.models.ResultRow;
import com.verifyinboxes.models.ResultsPage;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Lists of addresses: made from a CSV or a list of addresses, waited for, read, downloaded and resolved:
 * {@code client.lists()}.
 */
public final class ListService {
    /** The pauses between asks for a list: 2, 4, 8, 16, then every 30 seconds. */
    private static final Duration[] POLLS = {
        Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8), Duration.ofSeconds(16), Duration.ofSeconds(30),
    };

    private final Transport t;

    ListService(Transport t) {
        this.t = t;
    }

    /**
     * Starts a list ({@code POST /v1/jobs}) from a list of addresses, a CSV file, a CSV's text or a stream. Wait for
     * it with {@link #waitFor(String)}.
     *
     * <p>When its answer is lost it is sent again, with the same {@code Idempotency-Key}: the API answers the retry
     * with the same list, so it is made and charged once (a stream that began to be sent is not sent again).
     *
     * <p>When the thread is interrupted it stops at once, even when the request has already reached the API: the list
     * may then exist without its id in the {@link com.verifyinboxes.errors.CancelledException}. (On Java 11 to 15 the
     * JDK cannot cut off a request it has begun: the file or stream stops being read, but what was already handed to
     * the connection may still arrive.) If you may interrupt
     * it, pass your own {@code idempotencyKey}, and afterwards make the same call with the same key (within 24 hours):
     * the API answers with what the first request answered (the list it made), or, if that one never ran, makes the
     * list now, once.
     *
     * @param params what to make the list of
     * @return the list, just made
     * @throws IllegalArgumentException when params is {@code null}
     * @throws java.io.UncheckedIOException when the file cannot be read
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public JobCreated create(CreateListParams params) {
        return create(params, false);
    }

    /** create; finishOnCancel: once sent, the POST is not cut off by an interrupt (verifyMany's list). */
    JobCreated create(CreateListParams params, boolean finishOnCancel) {
        if (params == null) {
            throw new IllegalArgumentException("Give what to make the list of");
        }
        Transport.Request r = new Transport.Request("POST", "/v1/jobs");
        r.key = params.key() != null ? params.key() : Transport.newKey();
        r.finishOnCancel = finishOnCancel;
        if (params.emailList() != null) {
            r.json = Collections.singletonMap("emails", params.emailList());
        } else {
            r.upload = params.upload();
            r.longTimeout = true;
        }
        return t.decode(t.send(r), JobCreated.class);
    }

    /**
     * A list as it is now ({@code GET /v1/jobs/{id}}).
     *
     * @param id the list's id
     * @return the list
     * @throws IllegalArgumentException when the id is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Job get(String id) {
        return CheckService.getJob(t, id);
    }

    /**
     * Waits for a list to finish, however long it takes, and returns it. See {@link #waitFor(String, Duration)}.
     *
     * @param id the list's id
     * @return the list, finished
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails; it names the list
     */
    public Job waitFor(String id) {
        return waitList(id, t.deadline(null));
    }

    /**
     * Waits for a list to finish and returns it. It asks at once, then after 2, 4, 8, 16 and then every 30 seconds.
     * After the timeout a {@link WaitTimeoutException} names the list: it carries on. Every exception names the list
     * ({@code jobIds()}). A few hundred addresses take minutes, Microsoft-hosted ones longer.
     *
     * @param id      the list's id
     * @param timeout how long to wait, from the call; above zero
     * @return the list, finished
     * @throws WaitTimeoutException when it was still running after the timeout: it carries on
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails; it names the list
     */
    public Job waitFor(String id, Duration timeout) {
        return waitList(id, t.deadline(VerifyOptions.positive(timeout, "timeout")));
    }

    /** Asks at once, then polls a list, backing off; every exception names the list. */
    Job waitList(String id, Transport.Deadline deadline) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("The job id is empty");
        }
        List<String> ids = List.of(id);
        try {
            Job job = CheckService.getJob(t, id);
            for (int asked = 0; CheckService.active(job); asked++) {
                if (deadline.passed()) {
                    throw new WaitTimeoutException("list", job.status(), deadline.timeout, ids);
                }
                t.pause(deadline.pause(POLLS[Math.min(asked, POLLS.length - 1)]));
                job = CheckService.getJob(t, id);
            }
            return job;
        } catch (RuntimeException e) {
            throw Transport.withJobIds(e, ids, false);
        }
    }

    /**
     * Every row of a finished list, in order, read 500 at a time as you iterate. See
     * {@link #results(String, ResultsParams)}.
     *
     * @param id the list's id
     * @return the rows, read as they are iterated
     */
    public Iterable<ResultRow> results(String id) {
        return results(id, ResultsParams.defaults());
    }

    /**
     * Every row of a list, in order, read a page at a time as you iterate: nothing is asked until then, and each
     * iteration starts again from the first page.
     *
     * <pre>{@code
     * for (ResultRow row : client.lists().results(id)) {
     *     System.out.println(row.row() + " " + row.email() + " " + row.verdict());
     * }
     * }</pre>
     *
     * <p>It follows each page's cursor until there is none, whatever a page holds (a page may hold fewer rows than
     * asked, even none, before the last), so every row comes once and in order. A page that fails is asked again with
     * the same cursor (the client's retries); one that still fails throws from {@code hasNext()}. A page that sends
     * back the very cursor it was asked with would make the walk go round forever: its rows are given, then
     * {@code hasNext()} throws a {@link VerifyInboxesException}. One iterator is for one thread; the iterable may be
     * shared.
     *
     * @param id     the list's id
     * @param params which rows, and how many to a request
     * @return the rows, read as they are iterated
     * @throws IllegalArgumentException when the id is empty
     */
    public Iterable<ResultRow> results(String id, ResultsParams params) {
        String path = CheckService.jobPath(id, "/results");
        ResultsParams p = params == null ? ResultsParams.defaults() : params;
        return () -> new Rows(id, path, p);
    }

    /** One walk through the pages of a list's results. */
    private final class Rows implements Iterator<ResultRow> {
        private final String id;
        private final String path;
        private final ResultsParams params;
        private List<ResultRow> items = Collections.emptyList();
        private int index;
        private String cursor;
        private boolean started;
        private boolean done;
        private RuntimeException pending;
        private RuntimeException failed;

        Rows(String id, String path, ResultsParams params) {
            this.id = id;
            this.path = path;
            this.params = params;
        }

        @Override
        public boolean hasNext() {
            while (index >= items.size()) {
                if (failed != null) {
                    throw failed;
                }
                if (done) {
                    return false;
                }
                if (pending != null) {
                    failed = pending;
                    throw failed;
                }
                if (started && cursor == null) {
                    done = true;
                    return false;
                }
                String sent = cursor;
                ResultsPage page;
                try {
                    page = page(path, params, sent);
                } catch (RuntimeException e) {
                    failed = e;
                    throw e;
                }
                started = true;
                if (sent != null && sent.equals(page.nextCursor())) {
                    pending = Transport.unexpected(null, List.of(id), "The API returned the same cursor twice (" + sent
                            + ") while listing the results of " + id);
                }
                items = page.data();
                index = 0;
                cursor = page.nextCursor();
            }
            return true;
        }

        @Override
        public ResultRow next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return items.get(index++);
        }
    }

    /**
     * One page of results, for keeping your own cursor: {@code null} for the first page, then each page's
     * {@code nextCursor()} until it is {@code null}. A page may hold fewer rows than asked, even none, before the last
     * one.
     *
     * @param id     the list's id
     * @param cursor the cursor of the page, or {@code null} for the first
     * @return the page
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public ResultsPage resultsPage(String id, String cursor) {
        return resultsPage(id, cursor, ResultsParams.defaults());
    }

    /**
     * One page of results, for keeping your own cursor. See {@link #resultsPage(String, String)}.
     *
     * @param id     the list's id
     * @param cursor the cursor of the page, or {@code null} for the first
     * @param params which rows, and how many
     * @return the page
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public ResultsPage resultsPage(String id, String cursor, ResultsParams params) {
        return page(CheckService.jobPath(id, "/results"), params == null ? ResultsParams.defaults() : params, cursor);
    }

    private ResultsPage page(String path, ResultsParams params, String cursor) {
        Transport.Request r = new Transport.Request("GET", path);
        r.query("limit", String.valueOf(params.size()));
        params.addFilters(r);
        if (cursor != null) {
            r.query("cursor", cursor);
        }
        Transport.Response<byte[]> a = t.send(r);
        JsonNode tree = t.json(a);
        if (!tree.path("data").isArray()) {
            throw Transport.unexpected(a.requestId, null, "The API answered a page of results without its rows");
        }
        return t.convert(tree, ResultsPage.class, a.requestId);
    }

    private Transport.Request download(String id, DownloadParams params) {
        Transport.Request r = new Transport.Request("GET", CheckService.jobPath(id, "/download"));
        (params == null ? DownloadParams.defaults() : params).addFilters(r);
        r.longTimeout = true;
        r.accept = "text/csv";
        return r;
    }

    /**
     * Writes the CSV you sent, with the results appended, to a file. See
     * {@link #download(String, Path, DownloadParams)}.
     *
     * @param id          the list's id
     * @param destination the file to write
     * @return the bytes written
     * @throws java.io.UncheckedIOException when the file cannot be written
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public long download(String id, Path destination) {
        return download(id, destination, DownloadParams.defaults());
    }

    /**
     * Writes the CSV you sent, with the results appended ({@code GET /v1/jobs/{id}/download}), to a file, and returns
     * the bytes written. It is written to a part file beside it ({@code <name>.part-<random>}), renamed over it once
     * whole: a failed download leaves the file there was untouched and no part file behind, and one cut short is made
     * again from the start.
     *
     * @param id          the list's id
     * @param destination the file to write
     * @param params      which rows
     * @return the bytes written
     * @throws java.io.UncheckedIOException when the file cannot be written
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public long download(String id, Path destination, DownloadParams params) {
        if (destination == null) {
            throw new IllegalArgumentException("Give the file to write");
        }
        return t.send(download(id, params), () -> new Downloads.ToFile(destination)).body;
    }

    /**
     * Writes the CSV you sent, with the results appended, to a stream. See
     * {@link #download(String, OutputStream, DownloadParams)}.
     *
     * @param id          the list's id
     * @param destination where to write it; not closed
     * @return the bytes written
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public long download(String id, OutputStream destination) {
        return download(id, destination, DownloadParams.defaults());
    }

    /**
     * Writes the CSV you sent, with the results appended, to a stream, and returns the bytes written. It is sent
     * again only until the stream has been given its first byte; after that a failure is thrown, and the stream holds
     * what came. The stream is written one buffer at a time from the HTTP client's threads; once the call has ended no
     * write starts, though one the stream was blocked in finishes when the stream lets it. It is not closed.
     *
     * @param id          the list's id
     * @param destination where to write it
     * @param params      which rows
     * @return the bytes written
     * @throws java.io.UncheckedIOException when the stream cannot be written
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public long download(String id, OutputStream destination, DownloadParams params) {
        if (destination == null) {
            throw new IllegalArgumentException("Give the stream to write");
        }
        return t.send(download(id, params), () -> new Downloads.ToStream(destination)).body;
    }

    /**
     * Sends a finished job's {@code catch_all} and {@code risky} addresses to be settled by a test email. See
     * {@link #resolve(String, List, String)}.
     *
     * @param id the list's (or check's) id
     * @return the resolution, a job of its own
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public ResolveCreated resolve(String id) {
        return resolve(id, null, null);
    }

    /**
     * Sends some of a finished job's {@code catch_all} and {@code risky} addresses to be settled by a test email. See
     * {@link #resolve(String, List, String)}.
     *
     * @param id     the list's (or check's) id
     * @param emails only these addresses, or {@code null} for all of them
     * @return the resolution, a job of its own
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public ResolveCreated resolve(String id, List<String> emails) {
        return resolve(id, emails, null);
    }

    /**
     * Sends a finished job's {@code catch_all} and {@code risky} addresses (all of them, or those listed) to be
     * settled by a test email ({@code POST /v1/jobs/{id}/resolve}): most are settled within about 3 days of sending,
     * some take up to 7. It is a job of its own: wait for it with {@link #waitFor(String)}.
     *
     * <p>When the thread is interrupted it stops at once, even when the request has already reached the API: as for
     * {@link #create}, pass your own key if you may interrupt it, and make the same call with the same key afterwards.
     *
     * @param id             the list's (or check's) id
     * @param emails         only these addresses, or {@code null} for all of them
     * @param idempotencyKey your own Idempotency-Key, or {@code null} for a new one
     * @return the resolution, a job of its own
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public ResolveCreated resolve(String id, List<String> emails, String idempotencyKey) {
        Transport.Request r = new Transport.Request("POST", CheckService.jobPath(id, "/resolve"));
        r.key = idempotencyKey != null ? Transport.checkKey(idempotencyKey) : Transport.newKey();
        if (emails != null) {
            Map<String, Object> body = Collections.singletonMap("emails", emails);
            r.json = body;
        }
        return t.decode(t.send(r), ResolveCreated.class);
    }
}
