package com.verifyinboxes.errors;

import java.time.Duration;
import java.util.List;

/**
 * 429: too many requests or lists at once. It is sent again on its own after its {@code Retry-After},
 * unless that is over 60 seconds: then it is thrown at once, with the wait in {@link #retryAfter()}.
 */
public final class RateLimitException extends ApiException {
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
    public RateLimitException(int statusCode, String code, String apiMessage, String requestId, Duration retryAfter, String body,
            List<String> jobIds) {
        super(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
    }
}
