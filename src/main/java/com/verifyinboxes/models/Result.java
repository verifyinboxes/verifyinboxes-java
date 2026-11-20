package com.verifyinboxes.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * One address's result. {@link #jobId()} names the job that holds it: the single check of {@code verify}, or the
 * list of {@code verifyMany} (11 addresses or more).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Result {
    @JsonProperty("email")
    private String email;
    @JsonProperty("verdict")
    private String verdict;
    @JsonProperty("score")
    private Double score;
    @JsonProperty("safe_to_send")
    private boolean safeToSend;
    @JsonProperty("flags")
    private List<String> flags;
    @JsonProperty("provider")
    private String provider;
    @JsonProperty("catch_all_domain")
    private Boolean catchAllDomain;
    @JsonProperty("verified_at")
    private String verifiedAt;
    @JsonProperty("source")
    private String source;
    @JsonProperty("reason")
    private String reason;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("did_you_mean")
    private String didYouMean;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("job_id")
    private String jobId;

    Result() {
    }

    /**
     * The address, as checked.
     *
     * @return the email
     */
    public String email() {
        return email;
    }

    /**
     * {@code valid}, {@code invalid}, {@code catch_all}, {@code risky} or {@code unknown}. The API may add verdicts: show one
     * you do not know, and act on {@link #safeToSend()}.
     *
     * @return the verdict
     */
    public String verdict() {
        return verdict;
    }

    /**
     * For a {@code catch_all} verdict, the probability that the mailbox exists (0 to 1); {@code null} otherwise.
     *
     * @return the score
     */
    public Double score() {
        return score;
    }

    /**
     * Whether to send to it: {@code valid}, or a {@code catch_all} whose score reaches your organisation's
     * threshold.
     *
     * @return the safe to send
     */
    public boolean safeToSend() {
        return safeToSend;
    }

    /**
     * {@code disposable}, {@code role} ({@code info@}, {@code sales@}), {@code free_provider} (a consumer
     * provider), {@code gateway} (a security gateway).
     *
     * @return the flags, never {@code null}; the list cannot be changed
     */
    public List<String> flags() {
        return flags == null ? Collections.emptyList() : Collections.unmodifiableList(flags);
    }

    /**
     * The domain's mail provider, from its MX hosts: {@code google}, {@code microsoft}, {@code proofpoint},
     * {@code mimecast}, {@code other}; {@code null} when not known.
     *
     * @return the provider
     */
    public String provider() {
        return provider;
    }

    /**
     * Whether the domain accepts every address; {@code null} when not known.
     *
     * @return the catch all domain
     */
    public Boolean catchAllDomain() {
        return catchAllDomain;
    }

    /**
     * When the address was checked, ISO 8601.
     *
     * @return the verified at
     */
    public String verifiedAt() {
        return verifiedAt;
    }

    /**
     * Where the answer came from: {@code smtp} (our live check), {@code burner} (a catch-all resolution's test
     * email), {@code cache} (an earlier result).
     *
     * @return the source
     */
    public String source() {
        return source;
    }

    /**
     * Why the verdict is what it is: {@code mailbox_exists}, {@code mailbox_not_found}, {@code no_mail_server},
     * {@code accept_all}, {@code syntax}, {@code typo}, {@code greylisted}, {@code blocked}, {@code suppressed}, ...
     *
     * @return the reason
     */
    public String reason() {
        return reason;
    }

    /**
     * For a {@code typo}, the address it probably is; {@code null} otherwise.
     *
     * @return the did you mean
     */
    public String didYouMean() {
        return didYouMean;
    }

    /**
     * The job that holds the result: wait for it, or resolve it, by this id.
     *
     * @return the job id
     */
    public String jobId() {
        return jobId;
    }

    /**
     * This result as the API sent it, in JSON.
     *
     * @return the JSON
     */
    @Override
    public String toString() {
        return ModelJson.write(this);
    }
}
