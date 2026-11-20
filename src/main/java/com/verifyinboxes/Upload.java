package com.verifyinboxes;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A CSV sent as {@code multipart/form-data}: the field {@code email_column}, then the file, typed {@code text/csv}.
 * Its body is made again for every attempt: a {@link Path} is opened again (and sent with its length), a CSV's text
 * is sent again from memory, and a one-way {@link InputStream} is sent once: never again once it began to be read.
 *
 * <p>The body is {@link HttpRequest.BodyPublishers#ofInputStream} of the head, the file and the tail, one after the
 * other: Java 11 has no way to join publishers. Each attempt's stream stops giving bytes once the attempt is over
 * (it is in the attempt's {@code opened}), so that a request that was cancelled is not read on: on Java 11 to 15,
 * cancelling the JDK's request does not stop it from reading its body.
 */
final class Upload {
    private final byte[] head;
    private final byte[] tail;
    private final String contentType;
    private final Path path;
    private final byte[] data;
    private final InputStream stream;
    private final AtomicBoolean started = new AtomicBoolean();

    private Upload(String emailColumn, String filename, Path path, byte[] data, InputStream stream) {
        String boundary = "verifyinboxes-" + Transport.newKey().replace("-", "");
        this.head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"email_column\"\r\n\r\n" + emailColumn + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + quote(filename)
                + "\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        this.tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        this.contentType = "multipart/form-data; boundary=" + boundary;
        this.path = path;
        this.data = data;
        this.stream = stream;
    }

    static Upload of(String emailColumn, String filename, Path path, String csv, InputStream stream) {
        return new Upload(emailColumn, filename, path, csv == null ? null : csv.getBytes(StandardCharsets.UTF_8), stream);
    }

    /** A value made safe inside a quoted Content-Disposition parameter. */
    private static String quote(String value) {
        return value.replace("\\", "\\\\").replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
    }

    String contentType() {
        return contentType;
    }

    /** Whether a failed attempt can be made again: always, but for a one-way stream whose reading began. */
    boolean resendable() {
        return stream == null || !started.get();
    }

    /**
     * The body of one attempt. What it opens, and a switch that stops its stream, are added to opened, to be closed
     * once the attempt is over.
     */
    HttpRequest.BodyPublisher publisher(List<Closeable> opened) {
        AtomicBoolean over = new AtomicBoolean();
        opened.add(() -> over.set(true));
        if (data != null) {
            return HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(
                    () -> joined(new Stoppable(new ByteArrayInputStream(data), over, null, true))),
                    head.length + (long) data.length + tail.length);
        }
        if (path != null) {
            long size;
            try {
                size = Files.size(path);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + path + ": " + e.getMessage(), e);
            }
            HttpRequest.BodyPublisher file = HttpRequest.BodyPublishers.ofInputStream(() -> {
                if (over.get()) {
                    throw new UncheckedIOException(new IOException("The attempt is over"));
                }
                try {
                    InputStream in = Files.newInputStream(path);
                    opened.add(in);
                    return joined(new Stoppable(in, over, null, true));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return HttpRequest.BodyPublishers.fromPublisher(file, head.length + size + tail.length);
        }
        if (started.get()) {
            throw new IllegalStateException("The stream was read once already, and cannot be sent again");
        }
        return HttpRequest.BodyPublishers.ofInputStream(() -> joined(new Stoppable(stream, over, started, false)));
    }

    private InputStream joined(InputStream file) {
        return new java.io.SequenceInputStream(java.util.Collections.enumeration(Arrays.asList(
                new ByteArrayInputStream(head), file, new ByteArrayInputStream(tail))));
    }

    /**
     * The file's bytes for one attempt: no more once the attempt is over (a read then fails, which ends the request);
     * a one-way stream also notes its first read, and is never closed (it is the caller's).
     */
    private static final class Stoppable extends FilterInputStream {
        private final AtomicBoolean over;
        private final AtomicBoolean started;
        private final boolean closes;

        Stoppable(InputStream in, AtomicBoolean over, AtomicBoolean started, boolean closes) {
            super(in);
            this.over = over;
            this.started = started;
            this.closes = closes;
        }

        private void check() throws IOException {
            if (over.get()) {
                throw new IOException("The attempt is over: its body is not read on");
            }
        }

        @Override
        public int read() throws IOException {
            check();
            if (started != null) {
                started.set(true);
            }
            int b = super.read();
            check();
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            check();
            if (started != null) {
                started.set(true);
            }
            int n = super.read(b, off, len);
            check();
            return n;
        }

        @Override
        public void close() throws IOException {
            if (closes) {
                super.close();
            }
        }
    }
}
