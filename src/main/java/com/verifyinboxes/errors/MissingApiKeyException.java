package com.verifyinboxes.errors;

/**
 * No API key was given to the builder, and the {@code VERIFYINBOXES_API_KEY} environment variable is not set (or is
 * blank). An organisation admin makes keys in the VerifyInboxes app under Developers, API keys.
 */
public final class MissingApiKeyException extends VerifyInboxesException {
    private static final long serialVersionUID = 1L;

    /**
     * No key.
     *
     * @param message where a key is looked for, and where keys are made
     */
    public MissingApiKeyException(String message) {
        super(message);
    }
}
