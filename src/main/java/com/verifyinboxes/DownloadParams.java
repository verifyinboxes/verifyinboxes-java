package com.verifyinboxes;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Which rows {@code client.lists().download(id, destination, params)} writes.
 *
 * <pre>{@code
 * client.lists().download(id, Path.of("safe.csv"), DownloadParams.defaults().onlySafeToSend(true));
 * }</pre>
 *
 * <p>Params are immutable: each method returns a copy with its change.
 */
public final class DownloadParams {
    private static final DownloadParams DEFAULTS = new DownloadParams(false, Collections.emptyList(), false);

    private final boolean onlySafeToSend;
    private final List<String> verdicts;
    private final boolean partial;

    private DownloadParams(boolean onlySafeToSend, List<String> verdicts, boolean partial) {
        this.onlySafeToSend = onlySafeToSend;
        this.verdicts = verdicts;
        this.partial = partial;
    }

    /**
     * The defaults: every row of a finished list.
     *
     * @return the defaults
     */
    public static DownloadParams defaults() {
        return DEFAULTS;
    }

    /**
     * Keeps the rows safe to send to. Not with {@link #verdicts}.
     *
     * @param onlySafeToSend {@code true} to keep only those
     * @return a copy with it
     * @throws IllegalArgumentException when verdicts are given too
     */
    public DownloadParams onlySafeToSend(boolean onlySafeToSend) {
        if (onlySafeToSend && !verdicts.isEmpty()) {
            throw new IllegalArgumentException("Give onlySafeToSend or verdicts, not both");
        }
        return new DownloadParams(onlySafeToSend, verdicts, partial);
    }

    /**
     * Keeps the rows of these verdicts ({@code valid}, {@code invalid}, {@code catch_all}, {@code risky},
     * {@code unknown}). Not with {@link #onlySafeToSend}.
     *
     * @param verdicts the verdicts; empty for all
     * @return a copy with them
     * @throws IllegalArgumentException when onlySafeToSend is set too, or a verdict is empty
     */
    public DownloadParams verdicts(Collection<String> verdicts) {
        return new DownloadParams(onlySafeToSend, ResultsParams.Filters.verdicts(verdicts, onlySafeToSend), partial);
    }

    /**
     * Keeps the rows of these verdicts. Not with {@link #onlySafeToSend}.
     *
     * @param verdicts the verdicts
     * @return a copy with them
     * @throws IllegalArgumentException when onlySafeToSend is set too, or a verdict is empty
     */
    public DownloadParams verdicts(String... verdicts) {
        return verdicts(Arrays.asList(verdicts));
    }

    /**
     * Downloads a list still running, as it stands.
     *
     * @param partial {@code true} to download a list that has not finished
     * @return a copy with it
     */
    public DownloadParams partial(boolean partial) {
        return new DownloadParams(onlySafeToSend, verdicts, partial);
    }

    void addFilters(Transport.Request r) {
        ResultsParams.Filters.add(r, onlySafeToSend, verdicts, partial);
    }
}
