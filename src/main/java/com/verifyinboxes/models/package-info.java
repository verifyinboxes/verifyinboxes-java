/**
 * What the API returns, named after the schemas of its description, with the API's own names in JSON.
 *
 * <p>Every model is immutable. Its accessors are named after its fields in camelCase ({@code safeToSend()} for
 * {@code safe_to_send}); a value that can be {@code null} is a boxed type ({@code Double}, {@code Boolean}), and a list
 * is never {@code null}. Timestamps are ISO 8601 strings, as the API sends them. The API adds fields and values
 * without a new version: a field this version does not know is ignored, and a value it does not know (a new verdict,
 * reason or flag) is kept as the plain string it is, never an error.
 *
 * <p>{@code toString()} is the model's JSON, as the API sent it, but for a secret shown only once
 * ({@link com.verifyinboxes.models.WebhookCreated#secret()}, {@link com.verifyinboxes.models.IntegrationCreated#url()}),
 * which is left out. Jackson writes the whole model, secrets included, with the API's names.
 */
package com.verifyinboxes.models;
