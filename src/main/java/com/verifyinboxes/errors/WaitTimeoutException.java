package com.verifyinboxes.errors;

import java.time.Duration;
import java.util.List;

/**
 * A check or a list was still running when the time to wait for it ran out. It carries on, and its credits are held:
 * wait for it again by its id ({@link #jobIds()}) with {@code client.checks().waitFor(id)} or
 * {@code client.lists().waitFor(id)}, instead of starting again.
 */
public final class WaitTimeoutException extends VerifyInboxesException {
    private static final long serialVersionUID = 1L;

    /** The time that was given to wait. */
    private final Duration waited;

    /**
     * A wait that ran out.
     *
     * @param what   {@code "check"}, {@code "list"}, or {@code null} for jobs of either kind
     * @param status the job's status when the wait ran out, or {@code null}
     * @param waited the time that was given to wait
     * @param jobIds the jobs still running: every job the call made, in the order of its input
     */
    public WaitTimeoutException(String what, String status, Duration waited, List<String> jobIds) {
        super(describe(what, status, waited, jobIds), null, null, jobIds);
        this.waited = waited;
    }

    private static String describe(String what, String status, Duration waited, List<String> jobIds) {
        String after = waited == null ? "" : " after " + seconds(waited);
        String resume = "check".equals(what) ? "checks().waitFor" : "list".equals(what) ? "lists().waitFor"
                : "checks().waitFor or lists().waitFor";
        if (jobIds != null && jobIds.size() == 1) {
            return "The " + (what == null ? "job" : what) + " " + jobIds.get(0) + " was still "
                    + (status == null || status.isEmpty() ? "running" : status) + after
                    + "; it carries on, and its credits are held: wait for it again with " + resume;
        }
        return "Still running" + after + ": the jobs " + (jobIds == null ? "" : String.join(", ", jobIds))
                + " carry on, and their credits are held: wait for them again with " + resume;
    }

    /** A duration in seconds, as a person reads it: {@code 60 seconds}, {@code 0.5 seconds}. */
    static String seconds(Duration d) {
        long millis = d.toMillis();
        if (millis % 1000 == 0) {
            long s = millis / 1000;
            return s + (s == 1 ? " second" : " seconds");
        }
        return (millis / 1000.0) + " seconds";
    }

    /**
     * The time that was given to wait.
     *
     * @return the timeout of the wait
     */
    public Duration waited() {
        return waited;
    }
}
