package com.verifyinboxes.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.verifyinboxes.Webhooks;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

/**
 * Receives VerifyInboxes webhooks: checks the signature before anything else, then reads the event.
 *
 * <pre>
 * export VERIFYINBOXES_WEBHOOK_SECRET="..."      # the secret webhooks().create returned
 * java -cp ... com.verifyinboxes.examples.WebhookReceiver 8000
 * </pre>
 *
 * <p>Put it behind HTTPS (the API calls only https:// addresses), and register its address with
 * {@code client.webhooks().create("https://example.com/webhooks/verifyinboxes")}.
 */
public final class WebhookReceiver {
    private static final int MAX_BODY = 1 << 20;
    private static final ObjectMapper JSON = new ObjectMapper();

    private WebhookReceiver() {
    }

    public static void main(String[] args) throws IOException {
        String secret = System.getenv("VERIFYINBOXES_WEBHOOK_SECRET");
        if (secret == null || secret.isEmpty()) {
            System.err.println("set VERIFYINBOXES_WEBHOOK_SECRET to the webhook's secret");
            System.exit(2);
        }
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/webhooks/verifyinboxes", exchange -> handle(exchange, secret));
        server.start();
        System.out.println("listening on port " + port);
    }

    static void handle(HttpExchange exchange, String secret) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            // The body exactly as it came: the signature is over these bytes, not over JSON parsed and written again.
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readNBytes(MAX_BODY + 1);
            }
            String header = exchange.getRequestHeaders().getFirst(Webhooks.SIGNATURE_HEADER);
            if (body.length > MAX_BODY || !Webhooks.verifySignature(body, header, secret)) {
                // Not from VerifyInboxes, changed on the way, or older than 5 minutes.
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            JsonNode event = JSON.readTree(body);
            // Deliveries come at least once: the delivery header is the same on every retry of one, to drop duplicates.
            String delivery = exchange.getRequestHeaders().getFirst(Webhooks.DELIVERY_HEADER);
            switch (event.path("event").asText()) {
                case Webhooks.EVENT_JOB_COMPLETED:
                    System.out.printf("%s: list %s is %s: %s%n", delivery, event.path("job_id").asText(),
                            event.path("status").asText(), event.path("counts"));
                    break;
                case Webhooks.EVENT_ADDRESS_CONTRADICTED:
                    System.out.printf("%s: %s: %s%n", delivery, event.path("email").asText(), event.path("message").asText());
                    break;
                default:
                    break; // an event this receiver does not know: the API adds them over time
            }
            exchange.sendResponseHeaders(204, -1);
        } finally {
            exchange.close();
        }
    }
}
