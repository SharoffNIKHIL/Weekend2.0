package com.weekend.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatImagesTest {

    private static String url(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void acceptsRealImagesOfTheSupportedTypes() throws Exception {
        String png = "data:image/png;base64," + com.weekend.assistant.TestFixtures.png(3, 3, Color.RED);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        byte[] gif = "GIF89a....".getBytes();
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes();
        assertThat(ChatImages.parse(List.of(png, url("image/jpeg", jpeg), url("image/gif", gif), url("image/webp", webp))))
                .extracting("mediaType").containsExactly("image/png", "image/jpeg", "image/gif", "image/webp");
        assertThat(ChatImages.parse(null)).isEmpty();
        assertThat(ChatImages.parse(List.of())).isEmpty();
    }

    @Test
    void rejectsWrongTypesFakesAndOversizedInput() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        assertThatThrownBy(() -> ChatImages.parse(Collections.nCopies(5, url("image/png", png)))).hasMessageContaining("at most 4");
        assertThatThrownBy(() -> ChatImages.parse(List.of(url("image/svg+xml", "<svg/>".getBytes())))).hasMessageContaining("PNG, JPEG");
        assertThatThrownBy(() -> ChatImages.parse(List.of(url("image/png", "<html>".getBytes())))).hasMessageContaining("does not match");
        assertThatThrownBy(() -> ChatImages.parse(List.of(url("image/webp", "RIFF\0\0\0\0AVI ".getBytes())))).hasMessageContaining("does not match");
        assertThatThrownBy(() -> ChatImages.parse(List.of("https://example.org/cat.png"))).hasMessageContaining("data URLs");
        assertThatThrownBy(() -> ChatImages.parse(List.of("data:image/png;base64,@@@"))).hasMessageContaining("base64");
        byte[] big = new byte[ChatImages.MAX_BYTES + 10];
        big[0] = (byte) 0x89;
        assertThatThrownBy(() -> ChatImages.parse(List.of(url("image/png", big)))).hasMessageContaining("5 MB");
    }
}
