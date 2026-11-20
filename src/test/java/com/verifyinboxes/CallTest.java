package com.verifyinboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** The future of an asynchronous call, at the moment its result and its cancellation meet. */
class CallTest {
    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aResultThatArrivesAsTheCallIsCancelledIsKept() throws Exception {
        // The body has returned its result; before the call records it, cancel() interrupts the runner and waits. The
        // hook holds the runner there until the interrupt has come, so that the two always meet in that order.
        CountDownLatch returned = new CountDownLatch(1);
        Call<String> call = new Call<>(() -> "a result naming its job", true, () -> {
            returned.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.park(this);
            }
        });
        Thread runner = new Thread(call);
        runner.start();
        assertTrue(returned.await(5, TimeUnit.SECONDS));
        AtomicReference<Boolean> cancelled = new AtomicReference<>();
        Thread cancelling = new Thread(() -> cancelled.set(call.cancel(true)));
        cancelling.start();
        cancelling.join(5000);
        assertFalse(cancelling.isAlive(), "cancel() did not return");
        assertEquals(Boolean.FALSE, cancelled.get(), "cancel() said it cancelled a call that had its result");
        assertFalse(call.isCancelled());
        assertEquals("a result naming its job", call.get(1, TimeUnit.SECONDS));
        runner.join(5000);
        assertFalse(runner.isAlive());
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aResultRecordedButNotYetCompletedIsKeptWhenCancelled() throws Exception {
        // The second window: the runner has let go of the lock (the body is over) but has not completed the future
        // yet. A cancel that comes then must wait for that completion, not take the call for one not started.
        AtomicReference<Call<String>> self = new AtomicReference<>();
        CountDownLatch recorded = new CountDownLatch(1);
        Call<String> call = new Call<>(() -> "a result naming its job", true, null, () -> {
            recorded.countDown();
            while (!self.get().stopRequested()) {
                LockSupport.parkNanos(this, 1_000_000);
            }
        });
        self.set(call);
        Thread runner = new Thread(call);
        runner.start();
        assertTrue(recorded.await(5, TimeUnit.SECONDS));
        assertFalse(call.cancel(true), "cancel() said it cancelled a call that had its result");
        assertFalse(call.isCancelled());
        assertEquals("a result naming its job", call.get(1, TimeUnit.SECONDS));
        runner.join(5000);
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aRaceOfResultAndCancelNeverLosesTheResult() throws Exception {
        // A guard beside the two tests above: for a second and a half, a cancel at a random moment around the end of
        // a call that makes a job. Whenever the body has returned, the result is kept and cancel() says false.
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1500);
        int rounds = 0;
        while (System.nanoTime() < until) {
            rounds++;
            java.util.concurrent.atomic.AtomicBoolean returned = new java.util.concurrent.atomic.AtomicBoolean();
            Call<String> call = new Call<>(() -> {
                returned.set(true);
                return "result";
            }, true);
            int spin = java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 200);
            AtomicReference<Boolean> cancelled = new AtomicReference<>();
            Thread canceller = new Thread(() -> {
                while (!returned.get()) {
                    Thread.onSpinWait();
                }
                for (int i = 0; i < spin; i++) {
                    Thread.onSpinWait();
                }
                cancelled.set(call.cancel(true));
            });
            canceller.start();
            Thread runner = new Thread(call);
            runner.start();
            runner.join();
            canceller.join();
            assertEquals(Boolean.FALSE, cancelled.get(), "round " + rounds + ": the body had returned, and cancel() won");
            assertEquals("result", call.getNow(null), "round " + rounds);
        }
        assertTrue(rounds > 100, "only " + rounds + " rounds");
    }

    @Test
    @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aCallThatMakesNoJobIsCancelledWithoutWaiting() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        Call<String> call = new Call<>(() -> {
            started.countDown();
            while (true) {
                try {
                    never.await();
                    return "unreachable";
                } catch (InterruptedException e) {
                    // a body that will not stop: cancel() must not wait for it
                }
            }
        }, false, null);
        Thread runner = new Thread(call);
        runner.setDaemon(true);
        runner.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(call.cancel(true));
        assertTrue(call.isCancelled());
        never.countDown();
        runner.join(5000);
    }
}
