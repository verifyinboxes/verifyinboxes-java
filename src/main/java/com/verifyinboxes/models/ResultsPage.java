package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * One page of a list's results ({@code GET /v1/jobs/{id}/results}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ResultsPage {
    @JsonProperty("data")
    private List<ResultRow> data;
    @JsonProperty("next_cursor")
    private String nextCursor;
    @JsonProperty("complete")
    private boolean complete;

    ResultsPage() {
    }

    /**
     * The page's rows, in order. A page may hold fewer rows than asked, even none, before the last one.
     *
     * @return the data, never {@code null}; the list cannot be changed
     */
    public List<ResultRow> data() {
        return data == null ? Collections.emptyList() : Collections.unmodifiableList(data);
    }

    /**
     * The next page's cursor; {@code null} on the last page.
     *
     * @return the next cursor
     */
    public String nextCursor() {
        return nextCursor;
    }

    /**
     * Whether the job is done, so that no row will be added.
     *
     * @return the complete
     */
    public boolean complete() {
        return complete;
    }

    /**
     * This resultsPage as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
