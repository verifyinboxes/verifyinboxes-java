package com.verifyinboxes.errors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The base of every exception this library throws. Unchecked.
 *
 * <p>Catch it to handle anything that can go wrong with a call, or a subclass to handle one kind: an
 * {@link ApiException} for an error answer of the API, a {@link ConnectionException} when no answer came, a
 * {@link WaitTimeoutException} when a check or a list outlived the time to wait for it, a {@link CancelledException}
 * when the call was interrupted. Thrown as this class itself, it says that the API answered something it should not
 * have (a success that is not JSON, a job without its id, a page of results that sends back the cursor it was asked
 * for).
 *
 * <p><b>Job ids.</b> {@link #jobIds()} names the jobs the call had made when it failed, in the order of its input: a
 * check that outlived its wait, a list being waited for, the checks of {@code verifyMany}, a {@code verify} that was
 * interrupted. Those jobs carry on, and the credits they hold are spent on them: wait for them again with
 * {@code client.checks().waitFor(id)} or {@code client.lists().waitFor(id)} instead of starting again.
 *
 * <p>When the answer of the API is known, {@link #requestId()} is its {@code X-Request-Id}, and the message ends with
 * {@code (request id <id>)}: quote it when you write to support.
 */
public class VerifyInboxesException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The jobs the call had made. */
    private final ArrayList<String> jobIds;
    /** The X-Request-Id of the answer, or null. */
    private final String requestId;

    /**
     * An exception that names no job and no answer.
     *
     * @param message what went wrong
     */
    public VerifyInboxesException(String message) {
        this(message, null, null, null);
    }

    /**
     * An exception with its cause, that names no job and no answer.
     *
     * @param message what went wrong
     * @param cause   the exception underneath, or {@code null}
     */
    public VerifyInboxesException(String message, Throwable cause) {
        this(message, cause, null, null);
    }

    /**
     * An exception with everything it can carry.
     *
     * @param message   what went wrong; when {@code requestId} is given it should end with
     *                  {@code (request id <requestId>)}
     * @param cause     the exception underneath, or {@code null}
     * @param requestId the {@code X-Request-Id} of the answer it is about, or {@code null}
     * @param jobIds    the jobs the call had made, or {@code null} for none
     */
    public VerifyInboxesException(String message, Throwable cause, String requestId, List<String> jobIds) {
        super(message, cause);
        this.requestId = requestId;
        this.jobIds = jobIds == null ? new ArrayList<>() : new ArrayList<>(jobIds);
    }

    /**
     * The jobs the call had made when it failed, as far as it knows, in the order of its input. They carry on: wait
     * for them again with {@code checks().waitFor} or {@code lists().waitFor}.
     *
     * @return the job ids; empty, never {@code null}, when the call made none
     */
    public List<String> jobIds() {
        return Collections.unmodifiableList(jobIds);
    }

    /**
     * The {@code X-Request-Id} of the answer this is about: quote it when you write to support.
     *
     * @return the request id, or {@code null} when no answer came or it had none
     */
    public String requestId() {
        return requestId;
    }

    /**
     * The jobs a failed call had made, whatever wraps its exception: a {@link java.util.concurrent.CompletionException}
     * or an {@link java.util.concurrent.ExecutionException} of a future, the
     * {@link java.util.concurrent.CancellationException} of a cancelled one, or any other cause chain.
     *
     * <pre>{@code
     * try {
     *     results = future.join();
     * } catch (CancellationException | CompletionException e) {
     *     List<String> stillRunning = VerifyInboxesException.jobIdsOf(e);
     * }
     * }</pre>
     *
     * @param error what a call threw, or what wraps it
     * @return the job ids of the first {@link VerifyInboxesException} in its causes that names any; empty when none
     *         does
     */
    public static List<String> jobIdsOf(Throwable error) {
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        for (Throwable t = error; t != null && seen.put(t, Boolean.TRUE) == null; t = t.getCause()) {
            if (t instanceof VerifyInboxesException && !((VerifyInboxesException) t).jobIds.isEmpty()) {
                return ((VerifyInboxesException) t).jobIds();
            }
        }
        return Collections.emptyList();
    }
}
