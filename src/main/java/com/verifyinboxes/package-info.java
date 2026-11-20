/**
 * The official Java library for the VerifyInboxes API: check whether email addresses can receive mail, one at a time
 * or as lists of any size, and read the results. Start with {@link com.verifyinboxes.VerifyInboxesClient}.
 *
 * <pre>{@code
 * try (VerifyInboxesClient client = VerifyInboxesClient.fromEnv()) {   // reads VERIFYINBOXES_API_KEY
 *     Result result = client.verify("ann@example.com");
 *     List<Result> results = client.verifyMany(List.of("ann@example.com", "bob@example.com"));
 * }
 * }</pre>
 *
 * <p>What the API returns is in {@link com.verifyinboxes.models}, the exceptions in
 * {@link com.verifyinboxes.errors}, and checking a webhook's signature in {@link com.verifyinboxes.Webhooks}.
 */
package com.verifyinboxes;
