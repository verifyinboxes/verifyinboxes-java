package com.verifyinboxes;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifyinboxes.errors.CancelledException;
import com.verifyinboxes.errors.WaitTimeoutException;
import com.verifyinboxes.models.Job;
import com.verifyinboxes.models.Result;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One check by its job: started, as it is now, or waited for: {@code client.checks()}. {@code client.verify} starts
 * a check and waits for it.
 */
public final class CheckService {
    /** How long a check is waited for unless told: 60 seconds. */
    static final Duration CHECK_TIMEOUT = Duration.ofSeconds(60);
    /** A check's job is asked every 2 seconds. */
    static final Duration POLL = Duration.ofSeconds(2);

    private final Transport t;

    CheckService(Transport t) {
        this.t = t;
    }

    /**
     * Checks one address without waiting: the result when the API has it within about 5 seconds, or the check's job
     * ({@link Check#isPending()}), to wait for with {@link #waitFor(String)}.
     *
     * @param email the address
     * @return the check
     * @throws IllegalArgumentException when the address is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Check start(String email) {
        return start(email, VerifyOptions.defaults());
    }

    /**
     * Checks one address without waiting. The options' timeout is not used.
     *
     * <p>When the thread is interrupted once its request was sent, the request is let finish, so that the
     * {@link CancelledException} names the check it made.
     *
     * @param email   the address
     * @param options whether to check again rather than answer from the cache, and your own Idempotency-Key
     * @return the check
     * @throws IllegalArgumentException when the address is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Check start(String email, VerifyOptions options) {
        return start(email, options == null ? VerifyOptions.defaults() : options, null);
    }

    /** POST /v1/verify; once sent, it is let finish even if the thread is interrupted: made is told its job. */
    Check start(String email, VerifyOptions options, Consumer<String> made) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("The address is empty");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        if (options.isForceRecheck()) {
            body.put("force_recheck", true);
        }
        Transport.Request r = new Transport.Request("POST", "/v1/verify");
        r.json = body;
        r.key = options.getIdempotencyKey() != null ? options.getIdempotencyKey() : Transport.newKey();
        r.finishOnCancel = true;
        Transport.Response<byte[]> a = t.send(r);
        Check check = checkOf(a);
        if (check.jobId() != null && made != null) {
            made.accept(check.jobId());
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new CancelledException(null, check.jobId() == null ? null : List.of(check.jobId()));
        }
        return check;
    }

    /** The answer to POST /v1/verify: a result, or a check still running (202). */
    private Check checkOf(Transport.Response<byte[]> a) {
        JsonNode tree = t.json(a);
        if (a.status == 202) {
            JsonNode id = tree.path("job_id");
            if (!id.isTextual() || id.asText().isEmpty()) {
                throw Transport.unexpected(a.requestId, null, "The API answered 202 to a check without its job_id");
            }
            return new Check(id.asText(), null);
        }
        Result result = t.convert(tree, Result.class, a.requestId);
        return new Check(result.jobId(), result);
    }

    /**
     * A check's job as it is now ({@code GET /v1/jobs/{id}}); its {@code result()} once it is done.
     *
     * @param jobId the check's job
     * @return the job
     * @throws IllegalArgumentException when the id is empty
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public Job get(String jobId) {
        return getJob(t, jobId);
    }

    /**
     * Waits up to 60 seconds for a check that is still running (a pending {@link Check}, or one a
     * {@link WaitTimeoutException} named) and returns its result. See {@link #waitFor(String, Duration)}.
     *
     * @param jobId the check's job
     * @return the result
     * @throws WaitTimeoutException when it was still running after 60 seconds: it carries on
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails; it names the check
     */
    public Result waitFor(String jobId) {
        return waitFor(jobId, CHECK_TIMEOUT);
    }

    /**
     * Waits for a check that is still running and returns its result. It asks at once, then every 2 seconds; after
     * the timeout a {@link WaitTimeoutException} is thrown: the check carries on. Every exception names the check
     * ({@code jobIds()}).
     *
     * @param jobId   the check's job
     * @param timeout how long to wait, from the call; above zero
     * @return the result
     * @throws WaitTimeoutException when it was still running after the timeout: it carries on
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails; it names the check
     */
    public Result waitFor(String jobId, Duration timeout) {
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("The job id is empty");
        }
        return waitCheck(jobId, t.deadline(VerifyOptions.positive(timeout, "timeout")), true);
    }

    /** Polls a check's job until it is done, every 2 seconds; every exception names the job. */
    Result waitCheck(String jobId, Transport.Deadline deadline, boolean askFirst) {
        List<String> ids = List.of(jobId);
        try {
            String status = "pending";
            if (askFirst) {
                Job job = getJob(t, jobId);
                if (!active(job)) {
                    return resultOf(job, ids);
                }
                status = job.status();
            }
            while (true) {
                if (deadline.passed()) {
                    throw new WaitTimeoutException("check", status, deadline.timeout, ids);
                }
                t.pause(deadline.pause(POLL));
                Job job = getJob(t, jobId);
                if (!active(job)) {
                    return resultOf(job, ids);
                }
                status = job.status();
            }
        } catch (RuntimeException e) {
            throw Transport.withJobIds(e, ids, false);
        }
    }

    private Result resultOf(Job job, List<String> ids) {
        if (job.result() == null) {
            throw Transport.unexpected(null, ids, "The check " + job.id() + " ended " + job.status() + " without a result");
        }
        return t.withJobId(job.result(), job.id());
    }

    /** Whether a job is still being worked on. */
    static boolean active(Job job) {
        return "queued".equals(job.status()) || "running".equals(job.status());
    }

    /** GET /v1/jobs/{id}. */
    static Job getJob(Transport t, String id) {
        Transport.Response<byte[]> a = t.send(new Transport.Request("GET", jobPath(id, "")));
        Job job = t.decode(a, Job.class);
        if (job.id() == null || job.id().isEmpty()) {
            throw Transport.unexpected(a.requestId, null, "The API answered a job without its id");
        }
        return job;
    }

    static String jobPath(String id, String tail) {
        return "/v1/jobs/" + Transport.segment("job id", id) + tail;
    }
}
