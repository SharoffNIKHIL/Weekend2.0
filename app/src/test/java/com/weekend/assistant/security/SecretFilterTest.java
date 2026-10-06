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

    @Test
    void detectsAndRedactsLuhnValidCardNumbersOnly() {
        SecretFilter f = new SecretFilter();
        assertThat(f.containsSecret("card 4111 1111 1111 1111 exp 12/29")).isTrue();
        assertThat(f.containsSecret("card 4111-1111-1111-1111")).isTrue();
        assertThat(f.redact("pay with 4111111111111111 now")).isEqualTo("pay with [REDACTED] now");
        assertThat(f.containsSecret("order 1234567812345678")).isFalse();   // fails Luhn
        assertThat(f.containsSecret("call +91 98450 12345")).isFalse();     // too short
        assertThat(f.redact("amount 25000.46 on 2026-10-15")).isEqualTo("amount 25000.46 on 2026-10-15");
    }

    @Test
    void credentialCheckIgnoresTheKeywordHeuristic() {
        SecretFilter f = new SecretFilter();
        assertThat(f.containsSecret("If the owner pastes a secret: do not repeat it")).isTrue();
        assertThat(f.containsCredential("If the owner pastes a secret: do not repeat it")).isFalse();
        assertThat(f.containsCredential("key AKIAABCDEFGHIJKLMNOP")).isTrue();
        assertThat(f.containsCredential("4111 1111 1111 1111")).isTrue();
    }
}
