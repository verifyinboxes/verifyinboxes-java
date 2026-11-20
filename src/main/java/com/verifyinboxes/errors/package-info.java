/**
 * The exceptions of the library. All are unchecked and extend {@link com.verifyinboxes.errors.VerifyInboxesException},
 * which carries the jobs a call had made when it failed ({@code jobIds()}) and, for an answer of the API, its request
 * id.
 *
 * <ul>
 *   <li>{@link com.verifyinboxes.errors.ApiException}: the API answered with an error status; a subclass per status
 *       ({@link com.verifyinboxes.errors.InvalidRequestException}, {@link com.verifyinboxes.errors.AuthenticationException},
 *       {@link com.verifyinboxes.errors.InsufficientCreditsException},
 *       {@link com.verifyinboxes.errors.PermissionDeniedException}, {@link com.verifyinboxes.errors.NotFoundException},
 *       {@link com.verifyinboxes.errors.ConflictException}, {@link com.verifyinboxes.errors.RateLimitException},
 *       {@link com.verifyinboxes.errors.ServerException}).</li>
 *   <li>{@link com.verifyinboxes.errors.ConnectionException}: no answer came;
 *       {@link com.verifyinboxes.errors.ApiTimeoutException}: none in time.</li>
 *   <li>{@link com.verifyinboxes.errors.WaitTimeoutException}: a check or a list was still running when the time to
 *       wait ran out.</li>
 *   <li>{@link com.verifyinboxes.errors.CancelledException}: the call's thread was interrupted, or its future
 *       cancelled.</li>
 *   <li>{@link com.verifyinboxes.errors.MissingApiKeyException}: no API key was given or found.</li>
 *   <li>{@link com.verifyinboxes.errors.VerifyInboxesException} itself: the API answered something it should not
 *       have.</li>
 * </ul>
 */
package com.verifyinboxes.errors;
