package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * A verdict you bought, overturned by what became of mail sent to the address.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Notice {
    @JsonProperty("notice_id")
    private String noticeId;
    @JsonProperty("email")
    private String email;
    @JsonProperty("job_ids")
    private List<String> jobIds;
    @JsonProperty("previous")
    private NoticePrevious previous;
    @JsonProperty("current")
    private NoticeCurrent current;
    @JsonProperty("evidence")
    private NoticeEvidence evidence;
    @JsonProperty("message")
    private String message;
    @JsonProperty("created_at")
    private String createdAt;

    Notice() {
    }

    /**
     * The notice's id: send the last one as {@code after}.
     *
     * @return the notice id
     */
    public String noticeId() {
        return noticeId;
    }

    /**
     * The address.
     *
     * @return the email
     */
    public String email() {
        return email;
    }

    /**
     * Your jobs that returned the verdict.
     *
     * @return the job ids, never {@code null}; the list cannot be changed
     */
    public List<String> jobIds() {
        return jobIds == null ? Collections.emptyList() : Collections.unmodifiableList(jobIds);
    }

    /**
     * The verdict you bought.
     *
     * @return the previous
     */
    public NoticePrevious previous() {
        return previous;
    }

    /**
     * The verdict now.
     *
     * @return the current
     */
    public NoticeCurrent current() {
        return current;
    }

    /**
     * What contradicted it.
     *
     * @return the evidence
     */
    public NoticeEvidence evidence() {
        return evidence;
    }

    /**
     * It, said in a sentence.
     *
     * @return the message
     */
    public String message() {
        return message;
    }

    /**
     * When the notice was made, ISO 8601.
     *
     * @return the created at
     */
    public String createdAt() {
        return createdAt;
    }

    /**
     * This notice as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
