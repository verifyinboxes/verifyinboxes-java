package com.verifyinboxes;

import static com.verifyinboxes.FakeApi.answer;
import static com.verifyinboxes.FakeApi.on;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class VersionTest {
    /** The project's own version, as pom.xml says it. */
    static String pomVersion() throws Exception {
        String pom = new String(Files.readAllBytes(Path.of("pom.xml")), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<artifactId>verifyinboxes-java</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
        assertTrue(m.find(), "pom.xml has no version for verifyinboxes-java");
        return m.group(1);
    }

    @Test
    void theVersionIsThePoms() throws Exception {
        assertNotNull(Version.SDK);
        assertTrue(Version.SDK.matches("\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.]+)?"), Version.SDK);
        assertEquals(pomVersion(), Version.SDK);
    }

    @Test
    void theUserAgentNamesIt() throws Exception {
        try (FakeApi api = new FakeApi(List.of(
                on("GET", "/v1/account", answer(200, ClientTest.account())),
                on("GET", "/v1/account", answer(200, ClientTest.account()))))) {
            try (VerifyInboxesClient plain = VerifyInboxesClient.builder().apiKey(ClientTest.KEY).baseUrl(api.url()).build()) {
                plain.account().get();
            }
            try (VerifyInboxesClient mine = VerifyInboxesClient.builder().apiKey(ClientTest.KEY).baseUrl(api.url())
                    .userAgent("my-app/1.2").build()) {
                mine.account().get();
            }
            assertEquals("verifyinboxes-java/" + pomVersion(), api.requests().get(0).header("user-agent"));
            assertEquals("my-app/1.2 verifyinboxes-java/" + pomVersion(), api.requests().get(1).header("user-agent"));
        }
    }
}
