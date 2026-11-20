package com.verifyinboxes;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Checking that a webhook delivery came from VerifyInboxes, before reading anything in it. It needs no client and no
 * key.
 *
 * <pre>{@code
 * byte[] body = ...;  // the request's body exactly as it arrived, before any JSON parsing
 * if (!Webhooks.verifySignature(body, request.getHeader(Webhooks.SIGNATURE_HEADER), secret)) {
 *     // answer 400: not from VerifyInboxes, changed on the way, or older than 5 minutes
 * }
 * }</pre>
 */
public final class Webhooks {
    /** The header that carries a delivery's signature: {@code t=<unix seconds>,v1=<hex>}. */
    public static final String SIGNATURE_HEADER = "X-Boxverifier-Signature";
    /** The same on every retry of one delivery: deliveries come at least once, so drop the ones you have seen. */
    public static final String DELIVERY_HEADER = "X-Boxverifier-Delivery";
    /** How far a signature's time may be from yours: 5 minutes. */
    public static final Duration DEFAULT_TOLERANCE = Duration.ofSeconds(300);
    /** The event of a list or a resolution that finished. */
    public static final String EVENT_JOB_COMPLETED = "job.completed";
    /** The event of a verdict you bought, overturned by what became of mail sent to the address. */
    public static final String EVENT_ADDRESS_CONTRADICTED = "address.contradicted";

    private Webhooks() {
    }

    /**
     * Whether {@code header} (the delivery's {@value #SIGNATURE_HEADER}) signs {@code body} with your webhook's
     * {@code secret}, within 5 minutes of your clock. Never throws.
     *
     * @param body   the body exactly as it arrived
     * @param header the {@value #SIGNATURE_HEADER} header, or {@code null}
     * @param secret the 64 characters {@code webhooks().create} returned
     * @return {@code true} only for a valid signature
     */
    public static boolean verifySignature(byte[] body, String header, String secret) {
        return verifySignature(body, header, secret, DEFAULT_TOLERANCE, Instant.now());
    }

    /**
     * Whether {@code header} signs {@code body} with {@code secret}, within {@code tolerance} of your clock. Never
     * throws.
     *
     * @param body      the body exactly as it arrived
     * @param header    the {@value #SIGNATURE_HEADER} header, or {@code null}
     * @param secret    the 64 characters {@code webhooks().create} returned
     * @param tolerance how far the signature's time may be from now; a negative one accepts nothing
     * @return {@code true} only for a valid signature
     */
    public static boolean verifySignature(byte[] body, String header, String secret, Duration tolerance) {
        return verifySignature(body, header, secret, tolerance, Instant.now());
    }

    /**
     * Whether {@code header} signs {@code body} with {@code secret}, within {@code tolerance} of {@code now}. Never
     * throws.
     *
     * <p>The header is read as comma-separated {@code name=value} parts, each trimmed: exactly one {@code t}, in ASCII
     * digits, and one or more {@code v1}, each 64 lowercase hex characters (any other is ignored, so several may be
     * sent while a secret is being replaced: one that matches is enough). A {@code v1} is the HMAC-SHA256 of
     * {@code <t>.<body>}, keyed with the secret's characters (not hex-decoded), compared in constant time. Anything
     * that cannot be read is not valid.
     *
     * @param body      the body exactly as it arrived
     * @param header    the {@value #SIGNATURE_HEADER} header, or {@code null}
     * @param secret    the 64 characters {@code webhooks().create} returned
     * @param tolerance how far the signature's time may be from {@code now}; a negative one accepts nothing
     * @param now       the time to compare the signature's with
     * @return {@code true} only for a valid signature
     */
    public static boolean verifySignature(byte[] body, String header, String secret, Duration tolerance, Instant now) {
        try {
            if (body == null || header == null || secret == null || secret.isEmpty() || tolerance == null || now == null
                    || tolerance.isNegative()) {
                return false;
            }
            String stamp = null;
            int stamps = 0;
            List<byte[]> signatures = new ArrayList<>();
            for (String part : header.split(",", -1)) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String name = part.substring(0, eq).strip();
                String value = part.substring(eq + 1).strip();
                if (name.equals("t")) {
                    stamps++;
                    stamp = value;
                } else if (name.equals("v1") && lowerHex64(value)) {
                    signatures.add(value.getBytes(StandardCharsets.US_ASCII));
                }
            }
            if (stamps != 1 || !asciiDigits(stamp) || signatures.isEmpty()) {
                return false;
            }
            Duration age = Duration.between(Instant.ofEpochSecond(Long.parseLong(stamp)), now).abs();
            if (age.compareTo(tolerance) > 0) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((stamp + ".").getBytes(StandardCharsets.US_ASCII));
            byte[] expected = hex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
            boolean matched = false;
            for (byte[] signature : signatures) {
                // Every one is compared, in constant time: which of them matched is not told by the time it took.
                matched |= MessageDigest.isEqual(expected, signature);
            }
            return matched;
        } catch (RuntimeException | GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * {@link #verifySignature(byte[], String, String)} of a body given as text: its UTF-8 bytes are what was signed.
     *
     * @param body   the body exactly as it arrived, as text
     * @param header the {@value #SIGNATURE_HEADER} header, or {@code null}
     * @param secret the 64 characters {@code webhooks().create} returned
     * @return {@code true} only for a valid signature
     */
    public static boolean verifySignature(String body, String header, String secret) {
        return verifySignature(body, header, secret, DEFAULT_TOLERANCE, Instant.now());
    }

    /**
     * {@link #verifySignature(byte[], String, String, Duration)} of a body given as text.
     *
     * @param body      the body exactly as it arrived, as text
     * @param header    the {@value #SIGNATURE_HEADER} header, or {@code null}
     * @param secret    the 64 characters {@code webhooks().create} returned
     * @param tolerance how far the signature's time may be from now; a negative one accepts nothing
     * @return {@code true} only for a valid signature
     */
    public static boolean verifySignature(String body, String header, String secret, Duration tolerance) {
        return verifySignature(body, header, secret, tolerance, Instant.now());
    }

    /**
     * {@link #verifySignature(byte[], String, String, Duration, Instant)} of a body given as text.
     *
     * @param body      the body exactly as it arrived, as text
     * @param header    the {@value #SIGNATURE_HEADER} header, or {@code null}
     * @param secret    the 64 characters {@code webhooks().create} returned
     * @param tolerance how far the signature's time may be from {@code now}; a negative one accepts nothing
     * @param now       the time to compare the signature's with
     * @return {@code true} only for a valid signature
     */
    public static boolean verifySignature(String body, String header, String secret, Duration tolerance, Instant now) {
        return body != null && verifySignature(body.getBytes(StandardCharsets.UTF_8), header, secret, tolerance, now);
    }

    /** One or more of 0-9 only, and few enough to be a time. */
    private static boolean asciiDigits(String s) {
        if (s == null || s.isEmpty() || s.length() > 18) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean lowerHex64(String s) {
        if (s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = digits[(bytes[i] >> 4) & 0xf];
            out[2 * i + 1] = digits[bytes[i] & 0xf];
        }
        return new String(out);
    }
}
