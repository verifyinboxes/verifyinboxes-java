package com.verifyinboxes;

import com.verifyinboxes.models.Result;

/**
 * A check just started by {@code client.checks().start(email)}: its result, or, when the API is still running it,
 * only its job, to wait for with {@code client.checks().waitFor(check.jobId())}.
 */
public final class Check {
    private final String jobId;
    private final Result result;

    Check(String jobId, Result result) {
        this.jobId = jobId;
        this.result = result;
    }

    /**
     * The check's job: wait for it with {@code client.checks().waitFor(jobId)}.
     *
     * @return the job's id
     */
    public String jobId() {
        return jobId;
    }

    /**
     * Whether the check is still running: {@link #result()} is then {@code null}.
     *
     * @return {@code true} while there is no result yet
     */
    public boolean isPending() {
        return result == null;
    }

    /**
     * The check's result, when it is done.
     *
     * @return the result, or {@code null} while the check is pending
     */
    public Result result() {
        return result;
    }

    /**
     * The check as the API answered it, in JSON: {@code {"status":"pending","job_id":...}}, or the result.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return result == null ? "{\"status\":\"pending\",\"job_id\":\"" + jobId + "\"}" : result.toString();
    }
}
