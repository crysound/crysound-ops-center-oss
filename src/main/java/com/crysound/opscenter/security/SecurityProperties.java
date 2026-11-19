package com.crysound.opscenter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties("ops.security")
public record SecurityProperties(String operatorUsername, String operatorPassword,
                                 String viewerUsername, String viewerPassword) {
    // Validate after binding: binding constraint failures can print rejected credentials.
    void validate() {
        if (!configured(operatorUsername) || !configured(viewerUsername)
                || !configured(operatorPassword) || !configured(viewerPassword)
                || !acceptable(operatorPassword) || !acceptable(viewerPassword)) {
            throw new IllegalStateException(
                    "Security identities and passwords must be configured; passwords require at least 12 characters"
                            + " and at most 72 UTF-8 bytes");
        }
    }

    // BCrypt accepts at most 72 input bytes.
    private static boolean acceptable(String password) {
        return password.length() >= 12
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }

    @Override
    public String toString() { return "SecurityProperties[redacted]"; }
}
