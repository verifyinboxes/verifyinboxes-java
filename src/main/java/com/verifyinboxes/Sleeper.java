package com.verifyinboxes;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * How the client waits: before a retry, between the polls of a check or a list, between the starts of
 * {@code verifyMany}'s checks. The tests replace it ({@code Builder.sleeper}); it must throw
 * {@link InterruptedException} when the thread is interrupted, as {@link Thread#sleep} does.
 */
interface Sleeper {
    /** The real one. */
    Sleeper REAL = duration -> {
        long nanos = duration.toNanos();
        if (nanos > 0) {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } else if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    };

    /**
     * Waits.
     *
     * @param duration how long
     * @throws InterruptedException when the thread is interrupted
     */
    void sleep(Duration duration) throws InterruptedException;
}
