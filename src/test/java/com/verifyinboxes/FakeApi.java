package com.verifyinboxes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A scripted fake of the VerifyInboxes API on 127.0.0.1, for the tests.
 *
 * <p>It is an HTTP/1.1 server of its own on a {@link ServerSocket}, so that it can do what a real network does to a
 * client: close a connection after reading a request, without answering ({@code "drop": true}), or promise a body and
 * send only its first bytes ({@code "cut_after": n}). It serves the exchanges of one conformance scenario (the format
 * is in {@code src/test/resources/conformance}): in order, or, when unordered, each request matched to the first unused
 * exchange with the same method, path and body. It records every request. Connections are kept open between requests,
 * as a real server's are.
 */
final class FakeApi implements AutoCloseable {
    /** What the server answers to a request the scenario does not have: a status no API gives. */
    static final int UNEXPECTED_STATUS = 599;
    /** Answers 204 and is never recorded: asked first, so that the next request goes out on a connection used once. */
    static final String WARM_PATH = "/__warm";

    static final ObjectMapper JSON = new ObjectMapper();

    /** One request as the server read it. */
    static final class Recorded {
        final String method;
        /** The path as it was sent, still escaped, without the query. */
        final String path;
        final String rawQuery;
        final String version;
        final Map<String, List<String>> headers;
        final byte[] body;
        /** The body read as JSON, when it was sent as JSON. */
        final JsonNode json;
        /** The parts of a multipart/form-data body, in order; null when the body is not one. */
        final List<Map<String, String>> multipart;

        Recorded(String method, String target, String version, Map<String, List<String>> headers, byte[] body) {
            this.method = method;
            int q = target.indexOf('?');
            this.path = q < 0 ? target : target.substring(0, q);
            this.rawQuery = q < 0 ? "" : target.substring(q + 1);
            this.version = version;
            this.headers = headers;
            this.body = body;
            String type = header("content-type");
            JsonNode parsed = null;
            if (body.length > 0 && type != null && type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
                try {
                    parsed = JSON.readTree(body);
                } catch (IOException notJson) {
                    parsed = null;
                }
            }
            this.json = parsed;
            this.multipart = parseMultipart(body, type);
        }

        /** The first value of a header, or null. */
        String header(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            String text = bodyText();
            return method + " " + path + (rawQuery.isEmpty() ? "" : "?" + rawQuery) + " "
                    + (text.length() > 200 ? text.substring(0, 200) : text);
        }
    }

    private final List<JsonNode> exchanges;
    private final boolean unordered;
    private final int holdUntil;
    private final boolean gated;
    private final ServerSocket server;
    private final Thread acceptor;
    private final CountDownLatch gate = new CountDownLatch(1);

    private final List<Recorded> requests = new ArrayList<>();
    private final Recorded[] matched;
    private final List<Recorded> unexpected = new ArrayList<>();
    private final List<Socket> sockets = new ArrayList<>();
    private final List<CountDownLatch> waiting = new ArrayList<>();
    private int inFlight;
    private int maxInFlight;
    private boolean heldOut;
    private volatile boolean closed;

    /** A server of the exchanges, answered in order. */
    FakeApi(List<JsonNode> exchanges) {
        this(exchanges, false, 0, false);
    }

    /**
     * @param exchanges the scenario's exchanges
     * @param unordered match each request to the first unused exchange with its method, path and body
     * @param holdUntil hold every answer until this many requests are in flight at once (or 10 seconds pass)
     * @param gated     hold every answer, the request read whole, until {@link #release()} (or 10 seconds pass)
     */
    FakeApi(List<JsonNode> exchanges, boolean unordered, int holdUntil, boolean gated) {
        this.exchanges = new ArrayList<>(exchanges);
        this.unordered = unordered;
        this.holdUntil = holdUntil;
        this.gated = gated;
        this.matched = new Recorded[exchanges.size()];
        try {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        acceptor = new Thread(this::acceptLoop, "fake-api-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** The server's address, for a client's base URL. */
    String url() {
        return "http://127.0.0.1:" + server.getLocalPort();
    }

    /** Lets the answers held by {@code gated} go, and every later one straight away. */
    void release() {
        gate.countDown();
    }

    synchronized List<Recorded> requests() {
        return new ArrayList<>(requests);
    }

    synchronized int count() {
        return requests.size();
    }

    /** The request each exchange was used for (null: never). */
    synchronized List<Recorded> matched() {
        return Arrays.asList(matched.clone());
    }

    /** The requests no exchange was left for. */
    synchronized List<Recorded> unexpected() {
        return new ArrayList<>(unexpected);
    }

    synchronized int maxInFlight() {
        return maxInFlight;
    }

    synchronized boolean heldOut() {
        return heldOut;
    }

    @Override
    public void close() {
        closed = true;
        release();
        try {
            server.close();
        } catch (IOException ignored) {
            // closing anyway
        }
        List<Socket> open;
        synchronized (this) {
            open = new ArrayList<>(sockets);
            for (CountDownLatch w : waiting) {
                w.countDown();
            }
        }
        for (Socket s : open) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }

    // ---- the server -------------------------------------------------------------------------------------------------

    private void acceptLoop() {
        while (!closed) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                return;
            }
            synchronized (this) {
                sockets.add(socket);
            }
            Thread t = new Thread(() -> serve(socket), "fake-api-connection");
            t.setDaemon(true);
            t.start();
        }
    }

    private void serve(Socket socket) {
        try (Socket s = socket) {
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            while (!closed) {
                Recorded rec = readRequest(in);
                if (rec == null) {
                    return;
                }
                if (!answer(rec, out)) {
                    return;
                }
            }
        } catch (IOException e) {
            // the client went away
        }
    }

    /** Answers one request; false when the connection is to close. */
    private boolean answer(Recorded rec, OutputStream out) throws IOException {
        if (rec.path.equals(WARM_PATH)) {
            out.write("HTTP/1.1 204 No Content\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return true;
        }
        JsonNode exchange = take(rec);
        hold();
        try {
            if (gated) {
                awaitQuietly(gate, 10);
            }
            if (exchange == null) {
                byte[] body = "{\"error\":{\"code\":\"unexpected_request\",\"message\":\"not in the scenario\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                write(out, UNEXPECTED_STATUS, Collections.singletonMap("content-type", "application/json"), body, -1);
                return true;
            }
            JsonNode resp = exchange.path("response");
            if (resp.path("drop").asBoolean(false)) {
                // The request was read whole; the connection closes with no answer.
                return false;
            }
            Map<String, String> headers = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = resp.path("headers").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                headers.put(f.getKey().toLowerCase(Locale.ROOT), f.getValue().asText());
            }
            byte[] body = new byte[0];
            if (resp.has("json")) {
                body = JSON.writeValueAsBytes(resp.get("json"));
                headers.putIfAbsent("content-type", "application/json; charset=utf-8");
            } else if (resp.has("text")) {
                body = resp.get("text").asText().getBytes(StandardCharsets.UTF_8);
            }
            int status = resp.path("status").asInt(200);
            if (resp.has("cut_after")) {
                // The headers promise the whole body; only its first bytes come before the connection closes.
                write(out, status, headers, body, resp.get("cut_after").asInt());
                return false;
            }
            write(out, status, headers, body, -1);
            return true;
        } finally {
            synchronized (this) {
                inFlight--;
            }
        }
    }

    private static void write(OutputStream out, int status, Map<String, String> headers, byte[] body, int cutAfter)
            throws IOException {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        if (status != 204) {
            head.append("content-length: ").append(body.length).append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        if (status != 204) {
            out.write(body, 0, cutAfter < 0 ? body.length : Math.min(cutAfter, body.length));
        }
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 409: return "Conflict";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "Status";
        }
    }

    private synchronized JsonNode take(Recorded rec) {
        requests.add(rec);
        int index = -1;
        if (unordered) {
            for (int i = 0; i < exchanges.size(); i++) {
                if (matched[i] == null && sameRequest(rec, exchanges.get(i).path("request"))) {
                    index = i;
                    break;
                }
            }
        } else {
            int used = 0;
            for (Recorded m : matched) {
                if (m != null) {
                    used++;
                }
            }
            if (used < exchanges.size()) {
                index = used;
            }
        }
        if (index < 0) {
            unexpected.add(rec);
            return null;
        }
        matched[index] = rec;
        return exchanges.get(index);
    }

    /** Whether rec is the request want describes: its method, path and body (how unordered matches). */
    static boolean sameRequest(Recorded rec, JsonNode want) {
        if (!rec.method.equals(want.path("method").asText()) || !rec.path.equals(want.path("path").asText())) {
            return false;
        }
        if (want.has("json")) {
            return rec.json != null && rec.json.equals(want.get("json"));
        }
        if (want.has("multipart")) {
            return partsOf(want.get("multipart")).equals(rec.multipart);
        }
        return rec.body.length == 0;
    }

    /** The parts a scenario names, as {@link Recorded#multipart} has them. */
    static List<Map<String, String>> partsOf(JsonNode parts) {
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode p : parts) {
            Map<String, String> part = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = p.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> f = fields.next();
                part.put(f.getKey(), f.getValue().asText());
            }
            out.add(part);
        }
        return out;
    }

    private void hold() {
        CountDownLatch mine;
        synchronized (this) {
            inFlight++;
            maxInFlight = Math.max(maxInFlight, inFlight);
            if (maxInFlight >= holdUntil || heldOut) {
                for (CountDownLatch w : waiting) {
                    w.countDown();
                }
                waiting.clear();
                return;
            }
            mine = new CountDownLatch(1);
            waiting.add(mine);
        }
        if (!awaitQuietly(mine, 10)) {
            synchronized (this) {
                heldOut = true;
                for (CountDownLatch w : waiting) {
                    w.countDown();
                }
                waiting.clear();
            }
        }
    }

    private static boolean awaitQuietly(CountDownLatch latch, int seconds) {
        try {
            return latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- reading a request ------------------------------------------------------------------------------------------

    /** The next request on the connection, read whole; null when the client closed it. */
    private static Recorded readRequest(InputStream in) throws IOException {
        String line = readLine(in);
        while (line != null && line.isEmpty()) {
            line = readLine(in);
        }
        if (line == null) {
            return null;
        }
        String[] start = line.split(" ", 3);
        if (start.length != 3) {
            throw new IOException("not a request line: " + line);
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String h = readLine(in); h != null && !h.isEmpty(); h = readLine(in)) {
            int colon = h.indexOf(':');
            if (colon > 0) {
                headers.computeIfAbsent(h.substring(0, colon).trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                        .add(h.substring(colon + 1).trim());
            }
        }
        byte[] body;
        List<String> encoding = headers.get("transfer-encoding");
        List<String> length = headers.get("content-length");
        if (encoding != null && encoding.get(0).equalsIgnoreCase("chunked")) {
            body = readChunked(in);
        } else if (length != null) {
            body = readExactly(in, Integer.parseInt(length.get(0)));
        } else {
            body = new byte[0];
        }
        return new Recorded(start[0], start[1], start[2], headers, body);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int end = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, end, StandardCharsets.UTF_8);
            }
            line.write(b);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8.name());
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] out = new byte[n];
        int read = 0;
        while (read < n) {
            int got = in.read(out, read, n - read);
            if (got < 0) {
                throw new IOException("the body ended after " + read + " of " + n + " bytes");
            }
            read += got;
        }
        return out;
    }

    private static byte[] readChunked(InputStream in) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String size = readLine(in);
            if (size == null) {
                throw new IOException("the chunked body ended early");
            }
            int semi = size.indexOf(';');
            int n = Integer.parseInt((semi < 0 ? size : size.substring(0, semi)).trim(), 16);
            if (n == 0) {
                for (String trailer = readLine(in); trailer != null && !trailer.isEmpty(); trailer = readLine(in)) {
                    // trailers are not used
                }
                return body.toByteArray();
            }
            body.write(readExactly(in, n));
            readLine(in);
        }
    }

    /** The parts of a multipart/form-data body, in order; null when the body is not one. */
    static List<Map<String, String>> parseMultipart(byte[] body, String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) {
            return null;
        }
        int at = contentType.indexOf("boundary=");
        if (at < 0) {
            return null;
        }
        String boundary = contentType.substring(at + "boundary=".length()).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        String text = new String(body, StandardCharsets.UTF_8);
        String delimiter = "--" + boundary;
        List<Map<String, String>> parts = new ArrayList<>();
        int pos = text.indexOf(delimiter);
        while (pos >= 0) {
            int start = pos + delimiter.length();
            if (text.startsWith("--", start)) {
                break;
            }
            start = text.indexOf("\r\n", start) + 2;
            int next = text.indexOf("\r\n" + delimiter, start);
            if (next < 0) {
                break;
            }
            String raw = text.substring(start, next);
            int split = raw.indexOf("\r\n\r\n");
            String head = split < 0 ? raw : raw.substring(0, split);
            String value = split < 0 ? "" : raw.substring(split + 4);
            Map<String, String> part = new LinkedHashMap<>();
            String name = null;
            String filename = null;
            String type = null;
            for (String h : head.split("\r\n")) {
                int colon = h.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = h.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String val = h.substring(colon + 1).trim();
                if (key.equals("content-disposition")) {
                    name = param(val, "name");
                    filename = param(val, "filename");
                } else if (key.equals("content-type")) {
                    type = val;
                }
            }
            part.put("name", name);
            if (filename != null) {
                part.put("filename", filename);
                part.put("content_type", type == null ? "" : type);
            }
            part.put("value", value);
            parts.add(reorder(part));
            pos = next + 2;
        }
        return parts;
    }

    /** The keys in the order a scenario writes them: name, filename, content_type, value. */
    private static Map<String, String> reorder(Map<String, String> part) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : new String[] {"name", "filename", "content_type", "value"}) {
            if (part.containsKey(key)) {
                out.put(key, part.get(key));
            }
        }
        return out;
    }

    private static String param(String disposition, String name) {
        for (String piece : disposition.split(";")) {
            String p = piece.trim();
            if (p.startsWith(name + "=")) {
                String v = p.substring(name.length() + 1);
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return null;
    }

    // ---- what the tests share ---------------------------------------------------------------------------------------

    /** A scripted exchange: method and path expected, and the answer. */
    static JsonNode on(String method, String path, JsonNode response) {
        return on(method, path, response, null);
    }

    /** A scripted exchange: method, path and JSON body expected (null: any), and the answer. */
    static JsonNode on(String method, String path, JsonNode response, Object requestJson) {
        ObjectNode exchange = JSON.createObjectNode();
        ObjectNode request = exchange.putObject("request").put("method", method).put("path", path);
        if (requestJson != null) {
            request.set("json", JSON.valueToTree(requestJson));
        }
        exchange.set("response", response);
        return exchange;
    }

    /** A scripted answer: status, a JSON body (null: none), and an x-request-id. */
    static ObjectNode answer(int status, Object json) {
        ObjectNode resp = JSON.createObjectNode();
        resp.put("status", status);
        resp.putObject("headers").put("x-request-id", "019a7e00-0000-7000-8000-0000000000aa");
        if (json != null) {
            resp.set("json", JSON.valueToTree(json));
        }
        return resp;
    }

    /** An answer that is never given: the connection closes after the request is read. */
    static ObjectNode dropped() {
        return JSON.createObjectNode().put("drop", true);
    }
}
