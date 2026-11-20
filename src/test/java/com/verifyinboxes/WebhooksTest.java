package com.verifyinboxes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhooksTest {
    static final String SECRET = "6f1d0c9e8b7a6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e";
    static final String BODY = "{\"event\": \"job.completed\", \"job_id\": \"019a3b6e-8f0c-7d2e-9a41-5c3e2f1b0a77\"}";
    static final long T = 1_762_858_800L;

    static String sign(String t, String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((t + ".").getBytes(StandardCharsets.UTF_8));
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void needsNoClientAndUsesTheClock() throws Exception {
        long now = Instant.now().getEpochSecond();
        String header = "t=" + now + ",v1=" + sign(String.valueOf(now), BODY, SECRET);
        assertTrue(Webhooks.verifySignature(BODY, header, SECRET));
        assertTrue(Webhooks.verifySignature(BODY.getBytes(StandardCharsets.UTF_8), header, SECRET));
        assertTrue(Webhooks.verifySignature(BODY, header, SECRET, Webhooks.DEFAULT_TOLERANCE));
        assertEquals("X-Boxverifier-Signature", Webhooks.SIGNATURE_HEADER);
        assertEquals(Duration.ofSeconds(300), Webhooks.DEFAULT_TOLERANCE);
    }

    @Test
    void toleranceAndNow() throws Exception {
        String header = "t=" + T + ",v1=" + sign(String.valueOf(T), BODY, SECRET);
        Instant now = Instant.ofEpochSecond(T + 600);
        assertFalse(Webhooks.verifySignature(BODY, header, SECRET, Duration.ofSeconds(599), now));
        assertTrue(Webhooks.verifySignature(BODY, header, SECRET, Duration.ofSeconds(600), now));
        assertFalse(Webhooks.verifySignature(BODY, header, SECRET, Duration.ofSeconds(600), now.plusMillis(1)),
                "a fraction of a second past the tolerance");
        assertFalse(Webhooks.verifySignature(BODY, header, SECRET, Duration.ofSeconds(-1), Instant.ofEpochSecond(T)),
                "a negative tolerance accepts nothing");
    }

    @Test
    void malformedHeadersNeverThrow() throws Exception {
        String good = sign(String.valueOf(T), BODY, SECRET);
        Instant now = Instant.ofEpochSecond(T);
        String[] headers = {
            null, "", " ", ",", ",,,", "=", "=,=,=", "t", "t=", "v1=", "t=,v1=", "t=1", "t=" + T, "v1=" + good,
            "t=" + T + ",v1=" + good.toUpperCase(), "t=" + T + ",v1=" + good.substring(1), "t=" + T + ",v1=" + good + "0",
            "t=-" + T + ",v1=" + good, "t=+" + T + ",v1=" + good, "t=" + T + ".0,v1=" + good, "t=0x10,v1=" + good,
            "t=99999999999999999999999,v1=" + good, "t=9223372036854775807,v1=" + good, "t=" + "1".repeat(10_000),
            "t=" + T + ",v1=" + "é".repeat(64), "t=" + T + ",v1=" + "ü".repeat(32), "t=²²²,v1=" + good,
            "t=١٧٦٢٨٥٨٨٠٠,v1=" + good, "t=" + T + "\u0000,v1=" + good,
            "t=1".repeat(1000), "junk", "\u0000\u0001\u0002",
        };
        for (String header : headers) {
            for (String secret : new String[] {SECRET, null, ""}) {
                for (Object body : new Object[] {BODY, null, BODY.getBytes(StandardCharsets.UTF_8)}) {
                    boolean valid = body instanceof byte[]
                            ? Webhooks.verifySignature((byte[]) body, header, secret, Webhooks.DEFAULT_TOLERANCE, now)
                            : Webhooks.verifySignature((String) body, header, secret, Webhooks.DEFAULT_TOLERANCE, now);
                    assertFalse(valid, "accepted " + header);
                }
            }
        }
        assertFalse(Webhooks.verifySignature(BODY, "t=" + T + ",v1=" + good, SECRET, null, now));
        assertFalse(Webhooks.verifySignature(BODY, "t=" + T + ",v1=" + good, SECRET, Webhooks.DEFAULT_TOLERANCE, null));
        assertTrue(Webhooks.verifySignature(BODY, " t = " + T + " ,  v1 = " + good + " ", SECRET, Webhooks.DEFAULT_TOLERANCE, now),
                "parts and their names and values are trimmed");
    }

    @Test
    void theClientHasItToo() throws Exception {
        try (VerifyInboxesClient client = VerifyInboxesClient.builder().apiKey(ClientTest.KEY).build()) {
            long now = Instant.now().getEpochSecond();
            String header = "t=" + now + ",v1=" + sign(String.valueOf(now), BODY, SECRET);
            assertTrue(client.webhooks().verifySignature(BODY, header, SECRET));
            assertTrue(client.webhooks().verifySignature(BODY.getBytes(StandardCharsets.UTF_8), header, SECRET));
            assertFalse(client.webhooks().verifySignature(BODY + " ", header, SECRET));
        }
    }
}
