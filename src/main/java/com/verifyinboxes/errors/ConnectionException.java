package com.verifyinboxes.errors;

import java.util.List;

/**
 * The request got no answer: the connection could not be made, or it was closed or reset before the whole answer
 * came. {@link ApiTimeoutException}, a subclass, is an attempt that ran out of time. {@link #getCause()} says why.
 *
 * <p>A request the API may have run is sent again only when that is safe; what is thrown is the last attempt's.
 */
public class ConnectionException extends VerifyInboxesException {
    private static final long serialVersionUID = 1L;

    /**
     * A request that got no answer.
     *
     * @param message which request, and why
     * @param cause   the exception underneath, or {@code null}
     * @param jobIds  the jobs the call had made, or {@code null}
     */
    public ConnectionException(String message, Throwable cause, List<String> jobIds) {
        super(message, cause, null, jobIds);
    }
}
