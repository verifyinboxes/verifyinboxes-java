package com.verifyinboxes;

import java.time.Duration;

/**
 * The options of a check: {@code client.verify(email, options)} and {@code client.checks().start(email, options)}.
 *
 * <pre>{@code
 * VerifyOptions options = VerifyOptions.defaults().forceRecheck(true).timeout(Duration.ofSeconds(10));
 * }</pre>
 *
 * <p>Options are immutable: each method returns a copy with its change, so one set can be shared.
 */
public final class VerifyOptions {
    private static final VerifyOptions DEFAULTS = new VerifyOptions(false, null, null);

    private final boolean forceRecheck;
    private final Duration timeout;
    private final String idempotencyKey;

    private VerifyOptions(boolean forceRecheck, Duration timeout, String idempotencyKey) {
        this.forceRecheck = forceRecheck;
        this.timeout = timeout;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * The defaults: answered from the cache when it can be, waited for up to 60 seconds, with a new
     * {@code Idempotency-Key} for the call.
     *
     * @return the defaults
     */
    public static VerifyOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Whether to check again rather than answer from the cache.
     *
     * @param forceRecheck {@code true} to check the address again
     * @return a copy with it
     */
    public VerifyOptions forceRecheck(boolean forceRecheck) {
        return new VerifyOptions(forceRecheck, timeout, idempotencyKey);
    }

    /**
     * How long {@code verify} waits for the result, from the call (default 60 seconds). Past it, a
     * {@link com.verifyinboxes.errors.WaitTimeoutException} names the check's job: the check carries on, and
     * {@code checks().waitFor(jobId)} picks it up. {@code checks().start} does not wait, and does not use it.
     *
     * @param timeout above zero
     * @return a copy with it
     * @throws IllegalArgumentException when it is not above zero
     */
    public VerifyOptions timeout(Duration timeout) {
        return new VerifyOptions(forceRecheck, positive(timeout, "timeout"), idempotencyKey);
    }

    /**
     * Your own {@code Idempotency-Key} for the check (default: a new one for the call). A key names one request, and
     * the API keeps its answer for 24 hours: see the README's "Retries" before reusing one.
     *
     * @param idempotencyKey 1 to 255 visible ASCII characters
     * @return a copy with it
     * @throws IllegalArgumentException when it is not 1 to 255 visible ASCII characters
     */
    public VerifyOptions idempotencyKey(String idempotencyKey) {
        return new VerifyOptions(forceRecheck, timeout, Transport.checkKey(idempotencyKey));
    }

    boolean isForceRecheck() {
        return forceRecheck;
    }

    Duration getTimeout() {
        return timeout;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("The " + name + " must be above zero");
        }
        return value;
    }
}
