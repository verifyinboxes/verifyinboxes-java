package com.verifyinboxes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The version of this library. */
public final class Version {
    /**
     * The version of this library, such as {@code 0.1.0}: what its {@code User-Agent} names
     * ({@code verifyinboxes-java/<version>}).
     */
    public static final String SDK = read();

    private Version() {
    }

    private static String read() {
        try (InputStream in = Version.class.getResourceAsStream("/com/verifyinboxes/version.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                if (version != null && !version.isEmpty() && !version.startsWith("${")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
            // the build wrote no version: say so
        }
        return "unknown";
    }
}
