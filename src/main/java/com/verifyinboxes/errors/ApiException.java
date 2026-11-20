package com.verifyinboxes.errors;

import java.time.Duration;
import java.util.List;

/**
 * The API answered with an error status (4xx or 5xx).
 *
 * <p>{@link #code()} is the API's stable error code ({@code unauthorized}, {@code not_ready}, {@code consumer_list},
 * ...): match on it, and treat one you do not know by its status. The message reads
 * {@code <status> <code>: <what the API said> (request id <id>)}: show it or log it, never parse it.
 * {@link #requestId()} is the answer's {@code X-Request-Id}: quote it when you write to support. After retries,
 * everything here is the last answer's.
 *
 * <p>Each status has a subclass: {@link InvalidRequestException} (400, 413, 414, 415, 422),
 * {@link AuthenticationException} (401), {@link InsufficientCreditsException} (402), {@link PermissionDeniedException}
 * (403), {@link NotFoundException} (404, 410), {@link ConflictException} (409), {@link RateLimitException} (429) and
 * {@link ServerException} (5xx). Any other status is this class itself.
 */
public class ApiException extends VerifyInboxesException {
    private static final long serialVersionUID = 1L;

    /** The HTTP status. */
    private final int statusCode;
    /** The API's error code, or null. */
    private final String code;
    /** What the API said. */
    private final String apiMessage;
    /** The answer's Retry-After, or null. */
    private final Duration retryAfter;
    /** The answer's body, or null. */
    private final String body;

    /**
     * An error answer. {@link #of} makes the subclass of its status.
     *
     * @param statusCode the HTTP status
     * @param code       the API's error code, or {@code null} when the answer was not the API's (a proxy's page)
     * @param apiMessage what the API said, or {@code null}
     * @param requestId  the answer's {@code X-Request-Id}, or {@code null}
     * @param retryAfter the answer's {@code Retry-After}, or {@code null}
     * @param body       the answer's body as it came, or {@code null}
     * @param jobIds     the jobs the call had made, or {@code null}
     */
    public ApiException(int statusCode, String code, String apiMessage, String requestId, Duration retryAfter, String body,
                        List<String> jobIds) {
        super(describe(statusCode, code, apiMessage, requestId), null, requestId, jobIds);
        this.statusCode = statusCode;
        this.code = code;
        this.apiMessage = apiMessage == null || apiMessage.isEmpty() ? "The API answered " + statusCode : apiMessage;
        this.retryAfter = retryAfter;
        this.body = body;
    }

    /**
     * The exception of an error answer, of the subclass of its status.
     *
     * @param statusCode the HTTP status
     * @param code       the API's error code, or {@code null}
     * @param apiMessage what the API said, or {@code null}
     * @param requestId  the answer's {@code X-Request-Id}, or {@code null}
     * @param retryAfter the answer's {@code Retry-After}, or {@code null}
     * @param body       the answer's body as it came, or {@code null}
     * @param jobIds     the jobs the call had made, or {@code null}
     * @return the exception to throw
     */
    public static ApiException of(int statusCode, String code, String apiMessage, String requestId, Duration retryAfter,
                                  String body, List<String> jobIds) {
        switch (statusCode) {
            case 400:
            case 413:
            case 414:
            case 415:
            case 422:
                return new InvalidRequestException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            case 401:
                return new AuthenticationException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            case 402:
                return new InsufficientCreditsException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            case 403:
                return new PermissionDeniedException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            case 404:
            case 410:
                return new NotFoundException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            case 409:
                return new ConflictException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            case 429:
                return new RateLimitException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
            default:
                if (statusCode >= 500) {
                    return new ServerException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
                }
                return new ApiException(statusCode, code, apiMessage, requestId, retryAfter, body, jobIds);
        }
    }

    private static String describe(int statusCode, String code, String apiMessage, String requestId) {
        StringBuilder text = new StringBuilder().append(statusCode);
        if (code != null && !code.isEmpty()) {
            text.append(' ').append(code);
        }
        text.append(": ").append(apiMessage == null || apiMessage.isEmpty() ? "The API answered " + statusCode : apiMessage);
        if (requestId != null && !requestId.isEmpty()) {
            text.append(" (request id ").append(requestId).append(')');
        }
        return text.toString();
    }

    /**
     * The HTTP status of the answer.
     *
     * @return the status, 400 to 599
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The API's stable error code: match on it.
     *
     * @return the code, or {@code null} when the answer was not the API's (a proxy's error page)
     */
    public String code() {
        return code;
    }

    /**
     * What the API said, for a person: show it or log it, do not parse it.
     *
     * @return the API's message, or {@code The API answered <status>} when the answer was not the API's
     */
    public String apiMessage() {
        return apiMessage;
    }

    /**
     * The answer's {@code Retry-After}: how long the API asked to wait before trying again.
     *
     * @return the wait, or {@code null} when the answer had none
     */
    public Duration retryAfter() {
        return retryAfter;
    }

    /**
     * The answer's body as it came.
     *
     * @return the body as text, or {@code null}
     */
    public String body() {
        return body;
    }
}
