package com.verifyinboxes.errors;

import java.util.List;

/**
 * The request got no answer in time: an attempt ran out of the client's {@code timeout} (60 seconds unless set; at
 * least 10 minutes for an upload or a download). It is a {@link ConnectionException}, and is sent again as one is.
 */
public final class ApiTimeoutException extends ConnectionException {
    private static final long serialVersionUID = 1L;

    /**
     * A request that got no answer in time.
     *
     * @param message which request, and how long it was given
     * @param cause   the exception underneath, or {@code null}
     * @param jobIds  the jobs the call had made, or {@code null}
     */
    public ApiTimeoutException(String message, Throwable cause, List<String> jobIds) {
        super(message, cause, jobIds);
    }
}
