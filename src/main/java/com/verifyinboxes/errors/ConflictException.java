package com.verifyinboxes.errors;

import java.time.Duration;
import java.util.List;

/**
 * 409: {@code not_ready} (the list has not finished), {@code idempotency_conflict} (the key was used for
 * another request). An {@code idempotency_in_progress} is sent again on its own, and is thrown only when the retries
 * ran out.
 */
public final class ConflictException extends ApiException {
    private static final long serialVersionUID = 1L;

    /**
     * An error answer of this kind. {@link ApiException#of} makes the one of a status.
     *
     * @param statusCode the HTTP status
     * @param code       the API's error code, or {@code null} when the answer was not the API's
     * @param apiMessage what the API said, or {@code null}
     * @param requestId  the answer's {@code X-Request-Id}, or {@code null}
     * @param retryAfter the answer's {@code Retry-After}, or {@code null}
     * @param body       the answer's body as it came, or {@code null}
     * @param jobIds     the jobs the call had made, or {@code null}
     */
    public ConflictException(int statusCode, String code, String apiMessage, String requestId, Duration retryAfter, String body,
            List<String> jobIds) {
        super(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
    }
}
