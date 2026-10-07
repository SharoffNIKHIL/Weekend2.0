package com.weekend.assistant.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EndpointPolicyTest {

    private final EndpointPolicy policy = new EndpointPolicy(List.of("agents.example.com", " LOCALHOST:9000 ", "127.0.0.1", ""));

    @Test
    void acceptsAllowListedHttpsAndLoopbackHttp() {
        assertThat(policy.check("https://agents.example.com/v1/")).isEqualTo("https://agents.example.com/v1");
        assertThat(policy.check("https://agents.example.com:8443/a")).isEqualTo("https://agents.example.com:8443/a");
        assertThat(policy.check("http://127.0.0.1:8081/demo-agent")).isEqualTo("http://127.0.0.1:8081/demo-agent");
        assertThat(policy.check("http://localhost:9000/x")).isEqualTo("http://localhost:9000/x");
        assertThat(policy.anyAllowed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://agents.example.com/x",            // plain http off loopback
            "https://evil.example.com/x",             // not allow-listed
            "https://agents.example.com.evil.io/x",   // suffix trick
            "http://localhost:9001/x",                // port not allowed
            "https://user:pw@agents.example.com/x",   // user-info
            "https://agents.example.com/x?redirect=y",
            "https://agents.example.com/x#frag",
            "ftp://agents.example.com/x",
            "file:///etc/passwd",
            "not a url",
            ""})
    void rejectsEverythingElse(String url) {
        assertThatThrownBy(() -> policy.check(url)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyAllowListAllowsNothing() {
        EndpointPolicy none = new EndpointPolicy(List.of());
        assertThat(none.anyAllowed()).isFalse();
        assertThatThrownBy(() -> none.check("https://agents.example.com")).hasMessageContaining("allowed-hosts");
        assertThatThrownBy(() -> policy.check("https://" + "a".repeat(300) + ".com")).hasMessageContaining("max 300");
    }
}
