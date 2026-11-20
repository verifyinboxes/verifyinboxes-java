package com.verifyinboxes;

import com.verifyinboxes.models.NoticePage;

/**
 * The contradiction notices: verdicts you bought, overturned by what became of mail sent to the address:
 * {@code client.notices()}.
 */
public final class NoticeService {
    private final Transport t;

    NoticeService(Transport t) {
        this.t = t;
    }

    /**
     * The first notices, oldest first. See {@link #list(String, Integer)}.
     *
     * @return a page of notices
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public NoticePage list() {
        return list(null, null);
    }

    /**
     * The notices after one, oldest first. See {@link #list(String, Integer)}.
     *
     * @param after the {@code nextAfter()} of the page before, or {@code null}
     * @return a page of notices
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public NoticePage list(String after) {
        return list(after, null);
    }

    /**
     * Contradiction notices, oldest first ({@code GET /v1/notices}). Keep the page's {@code nextAfter()} and send it
     * as {@code after} next time; a page shorter than {@code limit} means you have caught up.
     *
     * @param after the {@code nextAfter()} of the page before, or {@code null}
     * @param limit at most this many, 1 to 500, or {@code null} for 100
     * @return a page of notices
     * @throws com.verifyinboxes.errors.VerifyInboxesException when the call fails
     */
    public NoticePage list(String after, Integer limit) {
        Transport.Request r = new Transport.Request("GET", "/v1/notices");
        if (after != null && !after.isEmpty()) {
            r.query("after", after);
        }
        if (limit != null) {
            r.query("limit", String.valueOf(limit));
        }
        return t.decode(t.send(r), NoticePage.class);
    }
}
