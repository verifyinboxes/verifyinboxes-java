package com.verifyinboxes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The client's sleep and clock for a test: each sleep is written down and moves the clock, and nothing waits. An
 * interrupted thread's sleep throws, as {@link Thread#sleep} does.
 */
final class PaperClock implements Sleeper {
    private final List<Duration> slept = new ArrayList<>();
    private long nanos = 1_000_000_000_000L;

    @Override
    public void sleep(Duration duration) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        synchronized (this) {
            slept.add(duration);
            nanos += duration.toNanos();
        }
    }

    synchronized long nanos() {
        return nanos;
    }

    synchronized List<Duration> slept() {
        return new ArrayList<>(slept);
    }
}
