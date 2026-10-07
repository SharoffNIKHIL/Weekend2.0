package com.weekend.assistant.studio;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.TestFixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Signed media links: valid for the right job and file until they expire, never otherwise. */
class MediaSignerTest {

    @Test
    void signsAndExpires() {
        TestFixtures.MutableClock clock = new TestFixtures.MutableClock(Instant.parse("2026-10-07T06:00:00Z"));
        MediaSigner signer = new MediaSigner(TestFixtures.props(), clock);
        String url = signer.url("job1", "video.mp4");
        assertThat(url).startsWith("/media/job1/video.mp4?exp=");
        long exp = Long.parseLong(url.replaceAll(".*exp=(\\d+).*", "$1"));
        String sig = url.replaceAll(".*sig=", "");
        assertThat(signer.valid("job1", "video.mp4", exp, sig)).isTrue();
        assertThat(signer.valid("job2", "video.mp4", exp, sig)).isFalse();
        assertThat(signer.valid("job1", "captions.srt", exp, sig)).isFalse();
        assertThat(signer.valid("job1", "video.mp4", exp + 1, sig)).isFalse();
        assertThat(signer.valid("job1", "video.mp4", exp, null)).isFalse();
        assertThat(signer.valid("job1", "video.mp4", exp, "x" + sig.substring(1))).isFalse();
        clock.advance(Duration.ofHours(7));
        assertThat(signer.valid("job1", "video.mp4", exp, sig)).isFalse();
        assertThat(new MediaSigner(TestFixtures.props(), clock).url("a", "b")).isNotEqualTo(null);
    }
}
