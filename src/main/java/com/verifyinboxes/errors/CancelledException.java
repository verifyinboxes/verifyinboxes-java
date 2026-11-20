package com.verifyinboxes.errors;

import java.util.List;

/**
 * The call was stopped: its thread was interrupted, or (through {@code client.async()}) its future was cancelled.
 * The thread's interrupt status is kept, so whatever runs next still sees it.
 *
 * <p>Nothing is started, sent again or waited for once a call is stopped, but a request that starts a check (each
 * check of {@code verify} and {@code verifyMany}) or {@code verifyMany}'s list, when it was already sent, is let
 * finish (within the client's timeout), so that the job it made is known: {@link #jobIds()} names every job the call
 * made. They carry on, and their credits are held: wait for them again with {@code checks().waitFor} or
 * {@code lists().waitFor}.
 */
public final class CancelledException extends VerifyInboxesException {
    private static final long serialVersionUID = 1L;

    /**
     * A call that was stopped.
     *
     * @param cause  the {@link InterruptedException}, or what the call was doing when it stopped; may be {@code null}
     * @param jobIds the jobs the call had made, or {@code null}
     */
    public CancelledException(Throwable cause, List<String> jobIds) {
        super(describe(jobIds), cause, null, jobIds);
    }

    private static String describe(List<String> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return "The call was interrupted";
        }
        if (jobIds.size() == 1) {
            return "The call was interrupted; the job " + jobIds.get(0)
                    + " carries on, and its credits are held: wait for it again with checks().waitFor or lists().waitFor";
        }
        return "The call was interrupted; the jobs " + String.join(", ", jobIds)
                + " carry on, and their credits are held: wait for them again with checks().waitFor or lists().waitFor";
    }
}
