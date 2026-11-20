package com.verifyinboxes;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What {@code client.lists().create(params)} makes a list of: exactly one of a list of addresses, a CSV file, a CSV's
 * text, or a stream of a CSV, chosen by the factory method; then, for a CSV, the name it carries and its column of
 * addresses, and for any, your own {@code Idempotency-Key}.
 *
 * <pre>{@code
 * client.lists().create(CreateListParams.file(Path.of("prospects.csv")).emailColumn("work_email"));
 * client.lists().create(CreateListParams.emails(List.of("ann@example.com", "bob@example.com")));
 * client.lists().create(CreateListParams.csv("email\nann@example.com\n"));
 * client.lists().create(CreateListParams.stream(in, "prospects.csv"));
 * }</pre>
 *
 * <p>A file is read again for a retry; a CSV's text is sent again from memory; a stream is read as it is sent, so it
 * is never sent again once its sending began (and it is not closed: it is yours). Params are immutable: each method
 * returns a copy with its change.
 */
public final class CreateListParams {
    private final List<String> emails;
    private final Path file;
    private final String csv;
    private final InputStream stream;
    private final String filename;
    private final String emailColumn;
    private final String idempotencyKey;

    private CreateListParams(List<String> emails, Path file, String csv, InputStream stream, String filename,
                             String emailColumn, String idempotencyKey) {
        this.emails = emails;
        this.file = file;
        this.csv = csv;
        this.stream = stream;
        this.filename = filename;
        this.emailColumn = emailColumn;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * A list of these addresses, sent as JSON.
     *
     * @param emails the addresses
     * @return the params
     * @throws IllegalArgumentException when the list or one of its addresses is {@code null}
     */
    public static CreateListParams emails(List<String> emails) {
        if (emails == null) {
            throw new IllegalArgumentException("Give the addresses");
        }
        for (String email : emails) {
            if (email == null) {
                throw new IllegalArgumentException("Give the addresses, none of them null");
            }
        }
        return new CreateListParams(Collections.unmodifiableList(new ArrayList<>(emails)), null, null, null, null, null, null);
    }

    /**
     * A list of the CSV file at this path, with a header row. It is read again for a retry. Its name is the upload's.
     *
     * @param file the file
     * @return the params
     * @throws IllegalArgumentException when it is {@code null}
     */
    public static CreateListParams file(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("Give the file's path");
        }
        return new CreateListParams(null, file, null, null, null, null, null);
    }

    /**
     * A list of a CSV's text, with a header row. The upload is named {@code list.csv} unless {@link #filename} says
     * otherwise.
     *
     * @param csv the CSV
     * @return the params
     * @throws IllegalArgumentException when it is {@code null}
     */
    public static CreateListParams csv(String csv) {
        if (csv == null) {
            throw new IllegalArgumentException("Give the CSV's text");
        }
        return new CreateListParams(null, null, csv, null, null, null, null);
    }

    /**
     * A list of the CSV a stream gives, with a header row. It is read as it is sent, so it is never sent again once
     * its sending began. It is not closed.
     *
     * @param stream   the CSV's bytes
     * @param filename the name the upload carries, such as {@code prospects.csv}
     * @return the params
     * @throws IllegalArgumentException when the stream is {@code null} or the name empty
     */
    public static CreateListParams stream(InputStream stream, String filename) {
        if (stream == null) {
            throw new IllegalArgumentException("Give the stream");
        }
        return new CreateListParams(null, null, null, stream, nonEmpty(filename, "filename"), null, null);
    }

    /**
     * The name the upload carries (a file's own name, {@code list.csv} for a CSV's text, unless this says otherwise).
     *
     * @param filename the name
     * @return a copy with it
     * @throws IllegalArgumentException when it is empty, or the list is of addresses (it has no file)
     */
    public CreateListParams filename(String filename) {
        csvOnly("filename");
        return new CreateListParams(emails, file, csv, stream, nonEmpty(filename, "filename"), emailColumn, idempotencyKey);
    }

    /**
     * The header of the CSV's column of addresses (default {@code email}).
     *
     * @param emailColumn the column's header
     * @return a copy with it
     * @throws IllegalArgumentException when it is empty, or the list is of addresses (it has no columns)
     */
    public CreateListParams emailColumn(String emailColumn) {
        csvOnly("emailColumn");
        return new CreateListParams(emails, file, csv, stream, filename, nonEmpty(emailColumn, "emailColumn"), idempotencyKey);
    }

    /**
     * Your own {@code Idempotency-Key} (default: a new one for the call). A key names one request, and the API keeps
     * its answer for 24 hours: the same key with another file gets the first file's list. Make it from the request
     * itself (a hash of the file), and give a new file, or a retry after a {@code 402}, a new key.
     *
     * @param idempotencyKey 1 to 255 visible ASCII characters
     * @return a copy with it
     * @throws IllegalArgumentException when it is not 1 to 255 visible ASCII characters
     */
    public CreateListParams idempotencyKey(String idempotencyKey) {
        return new CreateListParams(emails, file, csv, stream, filename, emailColumn, Transport.checkKey(idempotencyKey));
    }

    private void csvOnly(String what) {
        if (emails != null) {
            throw new IllegalArgumentException(what + " is for a CSV: a list of addresses has none");
        }
    }

    private static String nonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("The " + name + " is empty");
        }
        return value;
    }

    List<String> emailList() {
        return emails;
    }

    String key() {
        return idempotencyKey;
    }

    /** The upload of a CSV, or null for a list of addresses. */
    Upload upload() {
        if (emails != null) {
            return null;
        }
        String name = filename;
        if (name == null) {
            Path own = file == null ? null : file.getFileName();
            name = own == null ? "list.csv" : own.toString();
        }
        return Upload.of(emailColumn == null ? "email" : emailColumn, name, file, csv, stream);
    }
}
