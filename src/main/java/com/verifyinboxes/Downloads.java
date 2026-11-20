package com.verifyinboxes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.locks.ReentrantLock;

/** Where a download's bytes go: a file, written whole or not at all, or the caller's stream. */
final class Downloads {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Downloads() {
    }

    /**
     * Writes the bytes that come to write, one buffer at a time, until stopped. Each write happens under the lock,
     * after a look at {@code stopped}; {@link #stop} never waits for a write the destination is blocked in (a stream
     * whose reader is the one stopping the call), and no write starts after it.
     */
    private abstract static class Writer implements HttpResponse.BodySubscriber<Long> {
        private final ReentrantLock lock = new ReentrantLock();
        private final CompletableFuture<Long> result = new CompletableFuture<>();
        private volatile Flow.Subscription subscription;
        /** The bytes given to the destination (counted before each write). */
        volatile long written;
        private volatile boolean stopped;

        abstract void write(ByteBuffer buffer) throws IOException;

        @Override
        public CompletionStage<Long> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            try {
                lock.lock();
                try {
                    for (ByteBuffer b : items) {
                        if (stopped) {
                            subscription.cancel();
                            return;
                        }
                        write(b);
                    }
                } finally {
                    lock.unlock();
                }
                subscription.request(1);
            } catch (IOException e) {
                subscription.cancel();
                result.completeExceptionally(new Transport.LocalFailure(e));
            }
        }

        @Override
        public void onError(Throwable t) {
            result.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            result.complete(written);
        }

        /**
         * No write starts after this returns. A write the destination is blocked in is not waited for: it ends when
         * the destination lets it, and none follows.
         */
        void stop() {
            stopped = true;
            Flow.Subscription s = subscription;
            if (s != null) {
                s.cancel();
            }
            // Taking the lock, when it is free, makes sure no write that looked at stopped before it was set is still
            // to come; when a write holds it, that write is the last.
            if (lock.tryLock()) {
                lock.unlock();
            }
        }
    }

    /**
     * A download to a file: written to {@code <path>.part-<random>} beside it, renamed over it once whole; on any
     * failure the part file is removed and the file there is untouched. Each attempt has its own part file.
     */
    static final class ToFile implements Transport.Sink<Long> {
        private final Path destination;
        private final Path part;
        private final FileChannel channel;
        private final Writer writer;

        ToFile(Path destination) {
            this.destination = destination.toAbsolutePath();
            Path dir = this.destination.getParent();
            byte[] random = new byte[6];
            RANDOM.nextBytes(random);
            StringBuilder name = new StringBuilder(this.destination.getFileName().toString()).append(".part-");
            for (byte b : random) {
                name.append(String.format("%02x", b));
            }
            this.part = dir == null ? Path.of(name.toString()) : dir.resolve(name.toString());
            try {
                this.channel = FileChannel.open(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write beside " + this.destination + ": " + e.getMessage(), e);
            }
            this.writer = new Writer() {
                @Override
                void write(ByteBuffer buffer) throws IOException {
                    long n = written;
                    while (buffer.hasRemaining()) {
                        n += channel.write(buffer);
                    }
                    written = n;
                }
            };
        }

        @Override
        public HttpResponse.BodySubscriber<Long> subscriber() {
            return writer;
        }

        @Override
        public Long finish(Long body) {
            writer.stop();
            try {
                channel.force(true);
                channel.close();
                try {
                    Files.move(part, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return body;
            } catch (IOException e) {
                abort();
                throw new Transport.LocalFailure(e);
            }
        }

        @Override
        public void abort() {
            writer.stop();
            try {
                channel.close();
            } catch (IOException ignored) {
                // removed below
            }
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // nothing more can be done
            }
        }
    }

    /** A download to the caller's stream: sent again only until the stream has its first byte. */
    static final class ToStream implements Transport.Sink<Long> {
        private final Writer writer;

        ToStream(OutputStream out) {
            this.writer = new Writer() {
                @Override
                void write(ByteBuffer buffer) throws IOException {
                    int n = buffer.remaining();
                    // Counted before the write: a stream given bytes is never given them again from the start.
                    written = written + n;
                    if (buffer.hasArray()) {
                        out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), n);
                        buffer.position(buffer.limit());
                    } else {
                        byte[] bytes = new byte[n];
                        buffer.get(bytes);
                        out.write(bytes);
                    }
                }
            };
        }

        @Override
        public HttpResponse.BodySubscriber<Long> subscriber() {
            return writer;
        }

        @Override
        public Long finish(Long body) {
            writer.stop();
            return body;
        }

        @Override
        public void abort() {
            writer.stop();
        }

        @Override
        public boolean resendable() {
            return writer.written == 0;
        }
    }
}
