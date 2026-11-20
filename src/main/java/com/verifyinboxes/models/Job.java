package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * A check, a list or a catch-all resolution, as it is now ({@code GET /v1/jobs/{id}}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Job {
    @JsonProperty("id")
    private String id;
    @JsonProperty("kind")
    private String kind;
    @JsonProperty("status")
    private String status;
    @JsonProperty("total")
    private long total;
    @JsonProperty("done")
    private long done;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("finished_at")
    private String finishedAt;
    @JsonProperty("counts")
    private VerdictCounts counts;
    @JsonProperty("purged")
    private boolean purged;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("result")
    private Result result;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("parent_job_id")
    private String parentJobId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("resolve_job_ids")
    private List<String> resolveJobIds;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("download_url")
    private String downloadUrl;

    Job() {
    }

    /**
     * The job's id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * {@code single} (a check), {@code bulk} (a list) or {@code resolve} (a catch-all resolution).
     *
     * @return the kind
     */
    public String kind() {
        return kind;
    }

    /**
     * {@code running} until every address has a result, then {@code done}. The API may add statuses.
     *
     * @return the status
     */
    public String status() {
        return status;
    }

    /**
     * The number of addresses in the job.
     *
     * @return the total
     */
    public long total() {
        return total;
    }

    /**
     * The number with a result so far (for a resolution, settled).
     *
     * @return the done
     */
    public long done() {
        return done;
    }

    /**
     * When the job was made, ISO 8601.
     *
     * @return the created at
     */
    public String createdAt() {
        return createdAt;
    }

    /**
     * When it finished; {@code null} until then.
     *
     * @return the finished at
     */
    public String finishedAt() {
        return finishedAt;
    }

    /**
     * By verdict once the job is done; a resolution's settled rows so far while it runs; else {@code null}.
     *
     * @return the counts
     */
    public VerdictCounts counts() {
        return counts;
    }

    /**
     * Whether the job's results have been deleted (30 days after it finished).
     *
     * @return the purged
     */
    public boolean purged() {
        return purged;
    }

    /**
     * A single check's result, once it is done and while it is kept; else {@code null}.
     *
     * @return the result
     */
    public Result result() {
        return result;
    }

    /**
     * For a resolution only, the job it resolves; else {@code null}.
     *
     * @return the parent job id
     */
    public String parentJobId() {
        return parentJobId;
    }

    /**
     * For a list or a check, its resolutions, oldest first.
     *
     * @return the resolve job ids, never {@code null}; the list cannot be changed
     */
    public List<String> resolveJobIds() {
        return resolveJobIds == null ? Collections.emptyList() : Collections.unmodifiableList(resolveJobIds);
    }

    /**
     * For a finished list or resolution whose data is kept, where its CSV is ({@code lists().download}
     * reads it for you); else {@code null}.
     *
     * @return the download url
     */
    public String downloadUrl() {
        return downloadUrl;
    }

    /**
     * This job as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
