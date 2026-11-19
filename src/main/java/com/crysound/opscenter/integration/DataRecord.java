package com.crysound.opscenter.integration;

import java.util.Objects;

public record DataRecord(String key, String value) {
    public DataRecord {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (key.isBlank() || key.length() > 128 || value.length() > 4096) {
            throw new IllegalArgumentException("Invalid record shape");
        }
    }
}
