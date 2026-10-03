package com.weekend.assistant.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretFilterTest {

    private final SecretFilter filter = new SecretFilter();

    @ParameterizedTest
    @ValueSource(strings = {
            "tskey-auth-kAbCdEf123456",
            "sk-ant-api03-abcdefghijklmnop",
            "ghp_abcdefghijklmnopqrstuvwxyz0123456789",
            "-----BEGIN OPENSSH PRIVATE KEY-----",
            "{\"private_key\": \"x\"}",
            "the staging password is hunter2",
            "api key: 12345"})
    void detectsSecrets(String text) {
        assertThat(filter.containsSecret(text)).isTrue();
        assertThat(filter.redact(text)).contains("[REDACTED]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"The staging DB password rotates on the 15th", "I prefer tea over coffee", "Meeting at 10am"})
    void allowsOrdinaryFacts(String text) {
        assertThat(filter.containsSecret(text)).isFalse();
    }

    @Test
    void detectsAwsStyleKeyBuiltAtRuntime() {
        String fake = "AKIA" + "ABCDEFGHIJKLMNOP"; // assembled so repo scanners don't flag a fake key
        assertThat(filter.containsSecret("my key is " + fake)).isTrue();
    }

    @Test
    void handlesNull() {
        assertThat(filter.containsSecret(null)).isFalse();
        assertThat(filter.redact(null)).isNull();
    }
}
