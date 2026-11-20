package com.verifyinboxes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Which rows {@code client.lists().results(id, params)} reads, and how many each request asks for.
 *
 * <pre>{@code
 * client.lists().results(id, ResultsParams.defaults().onlySafeToSend(true).pageSize(1000));
 * }</pre>
 *
 * <p>Params are immutable: each method returns a copy with its change.
 */
public final class ResultsParams {
    private static final ResultsParams DEFAULTS = new ResultsParams(500, false, Collections.emptyList(), false);

    private final int pageSize;
    private final boolean onlySafeToSend;
    private final List<String> verdicts;
    private final boolean partial;

    private ResultsParams(int pageSize, boolean onlySafeToSend, List<String> verdicts, boolean partial) {
        this.pageSize = pageSize;
        this.onlySafeToSend = onlySafeToSend;
        this.verdicts = verdicts;
        this.partial = partial;
    }

    /**
     * The defaults: every row, 500 to a request, of a finished list.
     *
     * @return the defaults
     */
    public static ResultsParams defaults() {
        return DEFAULTS;
    }

    /**
     * How many rows each request asks for (default 500). A page may hold fewer, even none, before the last one.
     *
     * @param pageSize 1 to 1000
     * @return a copy with it
     * @throws IllegalArgumentException when it is not 1 to 1000
     */
    public ResultsParams pageSize(int pageSize) {
        if (pageSize < 1 || pageSize > 1000) {
            throw new IllegalArgumentException("The page size is 1 to 1000");
        }
        return new ResultsParams(pageSize, onlySafeToSend, verdicts, partial);
    }

    /**
     * Keeps the rows safe to send to. Not with {@link #verdicts}.
     *
     * @param onlySafeToSend {@code true} to keep only those
     * @return a copy with it
     * @throws IllegalArgumentException when verdicts are given too
     */
    public ResultsParams onlySafeToSend(boolean onlySafeToSend) {
        if (onlySafeToSend && !verdicts.isEmpty()) {
            throw new IllegalArgumentException("Give onlySafeToSend or verdicts, not both");
        }
        return new ResultsParams(pageSize, onlySafeToSend, verdicts, partial);
    }

    /**
     * Keeps the rows of these verdicts ({@code valid}, {@code invalid}, {@code catch_all}, {@code risky},
     * {@code unknown}). Not with {@link #onlySafeToSend}.
     *
     * @param verdicts the verdicts; empty for all
     * @return a copy with them
     * @throws IllegalArgumentException when onlySafeToSend is set too, or a verdict is empty
     */
    public ResultsParams verdicts(Collection<String> verdicts) {
        return new ResultsParams(pageSize, onlySafeToSend, Filters.verdicts(verdicts, onlySafeToSend), partial);
    }

    /**
     * Keeps the rows of these verdicts. Not with {@link #onlySafeToSend}.
     *
     * @param verdicts the verdicts
     * @return a copy with them
     * @throws IllegalArgumentException when onlySafeToSend is set too, or a verdict is empty
     */
    public ResultsParams verdicts(String... verdicts) {
        return verdicts(Arrays.asList(verdicts));
    }

    /**
     * Reads a list still running, as it stands: read it again from the start once it is done.
     *
     * @param partial {@code true} to read a list that has not finished
     * @return a copy with it
     */
    public ResultsParams partial(boolean partial) {
        return new ResultsParams(pageSize, onlySafeToSend, verdicts, partial);
    }

    int size() {
        return pageSize;
    }

    void addFilters(Transport.Request r) {
        Filters.add(r, onlySafeToSend, verdicts, partial);
    }

    /** The filters of the results and the download: only=safe_to_send, verdicts=a,b, partial=1. */
    static final class Filters {
        private Filters() {
        }

        static List<String> verdicts(Collection<String> verdicts, boolean onlySafeToSend) {
            List<String> copy = new ArrayList<>(verdicts == null ? Collections.emptyList() : verdicts);
            for (String v : copy) {
                if (v == null || v.isEmpty()) {
                    throw new IllegalArgumentException("A verdict is empty");
                }
            }
            if (onlySafeToSend && !copy.isEmpty()) {
                throw new IllegalArgumentException("Give onlySafeToSend or verdicts, not both");
            }
            return Collections.unmodifiableList(copy);
        }

        static void add(Transport.Request r, boolean onlySafeToSend, List<String> verdicts, boolean partial) {
            if (onlySafeToSend) {
                r.query("only", "safe_to_send");
            }
            if (!verdicts.isEmpty()) {
                r.query("verdicts", String.join(",", verdicts));
            }
            if (partial) {
                r.query("partial", "1");
            }
        }
    }
}
