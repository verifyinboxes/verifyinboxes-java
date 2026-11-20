package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * A page of notices, oldest first ({@code GET /v1/notices}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class NoticePage {
    @JsonProperty("notices")
    private List<Notice> notices;
    @JsonProperty("next_after")
    private String nextAfter;

    NoticePage() {
    }

    /**
     * The page's notices.
     *
     * @return the notices, never {@code null}; the list cannot be changed
     */
    public List<Notice> notices() {
        return notices == null ? Collections.emptyList() : Collections.unmodifiableList(notices);
    }

    /**
     * What to send as {@code after} next time; {@code null} when there were none and you sent none.
     *
     * @return the next after
     */
    public String nextAfter() {
        return nextAfter;
    }

    /**
     * This noticePage as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
