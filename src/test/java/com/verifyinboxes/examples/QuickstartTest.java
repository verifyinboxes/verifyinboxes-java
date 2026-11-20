package com.verifyinboxes.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.verifyinboxes.VerifyInboxesClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** The quickstart, run against a stand-in for the API: what it prints is what a reader copies. */
class QuickstartTest {
    private static final String REQUEST_ID = "019a7e00-0000-7000-8000-0000000000aa";
    private static final Map<String, String> JOBS = Map.of(
            "ann@example.com", "019a3b6e-8f0c-7d2e-9a41-5c3e2f1b0b01",
            "bob@example.com", "019a3b6e-8f0c-7d2e-9a41-5c3e2f1b0b02");

    @Test
    void checksEachAddressOnceAndNamesTheJobsAnErrorLeaves() throws IOException {
        List<String> asked = new CopyOnWriteArrayList<>();
        HttpServer api = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        api.createContext("/", exchange -> {
            String email = new ObjectMapper().readTree(exchange.getRequestBody()).path("email").asText();
            asked.add(email);
            String job = JOBS.get(email);
            String body = job == null
                    ? "{\"error\":{\"code\":\"insufficient_credits\",\"message\":\"the balance is 0\"}}"
                    : "{\"email\":\"" + email + "\",\"verdict\":\"invalid\",\"score\":null,\"safe_to_send\":false,"
                            + "\"flags\":[],\"provider\":\"google\",\"catch_all_domain\":false,"
                            + "\"verified_at\":\"2026-11-18T10:12:00.123Z\",\"source\":\"smtp\","
                            + "\"reason\":\"mailbox_not_found\",\"job_id\":\"" + job + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Request-Id", REQUEST_ID);
            exchange.sendResponseHeaders(job == null ? 402 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        api.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream savedOut = System.out;
        PrintStream savedErr = System.err;
        int code;
        try (VerifyInboxesClient client = VerifyInboxesClient.builder()
                .apiKey("bv_testtesttesttesttesttesttesttest")
                .baseUrl("http://127.0.0.1:" + api.getAddress().getPort())
                .build()) {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            code = Quickstart.run(client, List.of("ann@example.com", "bob@example.com", "cat@example.com"));
        } finally {
            System.setOut(savedOut);
            System.setErr(savedErr);
            api.stop(0);
        }
        String said = err.toString(StandardCharsets.UTF_8);
        assertEquals(1, code);
        // ann is checked once (by verify), not again by verifyMany.
        assertEquals(1, Collections.frequency(asked, "ann@example.com"), asked.toString());
        assertEquals(3, asked.size(), asked.toString());
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("ann@example.com: invalid"), out.toString());
        assertTrue(said.contains("402 insufficient_credits: the balance is 0 (request id " + REQUEST_ID + ")"), said);
        // bob's check carries on, its credit held: the error names it.
        assertTrue(said.contains("jobs made: " + JOBS.get("bob@example.com")), said);
    }
}
