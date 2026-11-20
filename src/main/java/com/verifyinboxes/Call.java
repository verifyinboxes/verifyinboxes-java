package com.verifyinboxes;

import com.verifyinboxes.errors.CancelledException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * One call run on an executor, as the future of its result.
 *
 * <p>Stopping it interrupts the thread running it: the call then stops as an interrupted call does (a request that
 * starts a check, already sent, is let finish, and the exception names the jobs it made). For a call that can make a
 * job ({@code namesJobs}: {@code verify}, {@code verifyMany}, {@code checks().start}), {@link #cancel} returns once the
 * call has stopped, and the future's {@link CancellationException} has for its cause what the call threw:
 * {@code VerifyInboxesException.jobIdsOf(e)} reads the jobs it made. Any other call is cancelled at once, as a
 * {@link java.util.concurrent.FutureTask} is: it has no job to name, and waiting could wait on the caller itself (a
 * download into a stream whose reader is the one cancelling). A call completed from outside ({@link #complete},
 * {@link #completeExceptionally}, {@code orTimeout}) is stopped without waiting.
 *
 * <p>For a call that can make a job, completion wins: once its body has returned its result, the call is completed
 * with it even when a cancel came meanwhile (that cancel then returns {@code false}), so that no result, and no job
 * it names, is thrown away. Any other call cancelled while it runs is cancelled at once, as above: the cancel wins,
 * and a result its body returns afterwards is dropped (even one whose work was done: a download renamed into place
 * just then, say). Only a call whose body had already returned when the cancel came completes with its result.
 */
final class Call<T> extends CompletableFuture<T> implements Runnable {
    private final Supplier<T> body;
    private final Object lock = new Object();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private Thread runner;
    private boolean stopRequested;
    /** Whether the body has returned: from then on the call only records its outcome, and a cancel waits for that. */
    private boolean bodyDone;
    /** Whether this call interrupted its runner: that interrupt is cleared once the call is over. */
    private boolean interruptedRunner;

    private final boolean namesJobs;
    /** Run once the body has returned, before its outcome is recorded: a seam for the tests only. */
    private final Runnable afterBody;
    /** Run once the outcome is recorded, before the future is completed: a seam for the tests only. */
    private final Runnable beforeComplete;

    /**
     * @param body      the call
     * @param namesJobs whether it can make a job: then cancel waits for it to stop, so that its exception names them
     */
    Call(Supplier<T> body, boolean namesJobs) {
        this(body, namesJobs, null);
    }

    Call(Supplier<T> body, boolean namesJobs, Runnable afterBody) {
        this(body, namesJobs, afterBody, null);
    }

    Call(Supplier<T> body, boolean namesJobs, Runnable afterBody, Runnable beforeComplete) {
        this.body = body;
        this.namesJobs = namesJobs;
        this.afterBody = afterBody;
        this.beforeComplete = beforeComplete;
    }

    /** Whether a stop or a cancel was asked for: for the tests. */
    boolean stopRequested() {
        synchronized (lock) {
            return stopRequested;
        }
    }

    @Override
    public void run() {
        synchronized (lock) {
            if (isDone() || stopRequested) {
                stopped.countDown();
                return;
            }
            runner = Thread.currentThread();
        }
        T value = null;
        Throwable failure = null;
        try {
            value = body.get();
        } catch (Throwable t) {
            failure = t;
        }
        if (afterBody != null) {
            afterBody.run();
        }
        boolean cancelled;
        boolean interruptedByThisCall;
        synchronized (lock) {
            runner = null;
            bodyDone = true;
            cancelled = stopRequested;
            interruptedByThisCall = interruptedRunner;
        }
        if (interruptedByThisCall) {
            // The interrupt meant for this call is not left on a thread that runs others.
            Thread.interrupted();
        }
        if (beforeComplete != null) {
            beforeComplete.run();
        }
        if (failure == null) {
            // A result that came is never thrown away (nor the job it names) when the call waited out a cancel.
            super.complete(value);
        } else if (cancelled && failure instanceof CancelledException) {
            CancellationException c = new CancellationException("The call was cancelled");
            c.initCause(failure);
            super.completeExceptionally(c);
        } else {
            // What the call ended with on its own (an answer that came is told as it is).
            super.completeExceptionally(failure);
        }
        stopped.countDown();
    }

    /** Asks the call to stop: interrupts the thread running it, or keeps it from starting. Does not wait. */
    void stop() {
        synchronized (lock) {
            stopRequested = true;
            if (runner != null) {
                runner.interrupt();
                interruptedRunner = true;
            }
        }
    }

    /** Whether the call is running now. */
    boolean running() {
        synchronized (lock) {
            return runner != null;
        }
    }

    /** Waits until the call has stopped or ended (or was stopped before it started and has been passed over). */
    void awaitStopped() throws InterruptedException {
        stopped.await();
    }

    /** Ends a call that will never run (its executor refused it). */
    void abandon(Throwable why) {
        synchronized (lock) {
            stopRequested = true;
        }
        super.completeExceptionally(why);
        stopped.countDown();
    }

    /**
     * Stops the call. One that can make a job is waited for until it has stopped: a request that starts a check,
     * already sent, is let finish (within the client's timeout), so that the exception names the job it made; the
     * future is then cancelled, its {@link CancellationException}'s cause what the call threw, unless the call had its
     * result first. Any other call is cancelled at once.
     *
     * @param mayInterruptIfRunning not used: a running call is always interrupted, as that is how it stops
     * @return whether the future is now cancelled: {@code false} when the call completed first
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean wasRunning;
        boolean finishing;
        synchronized (lock) {
            if (isDone()) {
                return isCancelled();
            }
            stopRequested = true;
            wasRunning = runner != null;
            if (wasRunning) {
                runner.interrupt();
                interruptedRunner = true;
            }
            finishing = bodyDone;
        }
        // A call that can make a job is waited for; so is any call whose body is over (it is only completing its
        // future), so that a result that came is never taken for a call that had not started.
        if ((wasRunning && namesJobs) || finishing) {
            boolean interrupted = false;
            while (true) {
                try {
                    stopped.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } else {
            super.cancel(mayInterruptIfRunning);
        }
        return isCancelled();
    }

    /**
     * Completes the future from outside, and stops the call, without waiting for it.
     *
     * @param value the value
     * @return whether this completed the future
     */
    @Override
    public boolean complete(T value) {
        boolean done = super.complete(value);
        if (done) {
            stop();
        }
        return done;
    }

    /**
     * Completes the future exceptionally from outside (as {@code orTimeout} does), and stops the call, without
     * waiting for it.
     *
     * @param ex the exception
     * @return whether this completed the future
     */
    @Override
    public boolean completeExceptionally(Throwable ex) {
        boolean done = super.completeExceptionally(ex);
        if (done) {
            stop();
        }
        return done;
    }
}
