package com.weekend.assistant.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.adapter.memory.InMemoryAuditLog;
import com.weekend.assistant.adapter.memory.InMemoryNotificationRepository;
import com.weekend.assistant.agent.RecordingLlm;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.port.StockImageClient;
import com.weekend.assistant.port.TtsClient;
import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Studio conversation (asks only what is missing, waits for Start) and real renders checked with ffprobe. */
class StudioServiceTest {

    @TempDir Path media;
    final TestFixtures.MutableClock clock = new TestFixtures.MutableClock(Instant.parse("2026-10-07T06:00:00Z"));
    final InMemoryNotificationRepository notes = new InMemoryNotificationRepository();
    final InMemoryAuditLog audit = new InMemoryAuditLog(clock);
    StudioService studio;

    /** A tone per call, length by word count, like a real voice would be. */
    static class FakeTts implements TtsClient {
        final boolean on;
        int calls;

        FakeTts(boolean on) {
            this.on = on;
        }

        public boolean enabled() {
            return on;
        }

        public String label() {
            return "Fake voice";
        }

        public boolean commercialUse() {
            return true;
        }

        public byte[] synthesize(String text) {
            calls++;
            short[] s = new short[(int) (Wav.RATE * Math.max(0.6, text.split("\\s+").length / 2.6))];
            for (int i = 0; i < s.length; i++) {
                s[i] = (short) (6000 * Math.sin(2 * Math.PI * 220 * i / Wav.RATE));
            }
            return Wav.write(s);
        }
    }

    static final class FakeStock implements StockImageClient {
        int calls;

        public boolean enabled() {
            return true;
        }

        public Optional<StockImage> find(String query, boolean landscape) {
            calls++;
            byte[] png = Base64.getDecoder().decode(TestFixtures.png(landscape ? 640 : 360, landscape ? 360 : 640, new Color(40, 120, 200)));
            return Optional.of(new StockImage(png, "Arctic fox in snow", "Jane Doe", "CC BY 2.0", "https://example.org/File:Fox.jpg"));
        }
    }

    StudioService service(TtsClient tts, StockImageClient stock) {
        WeekendProperties p = TestFixtures.withStudio(new WeekendProperties.Studio(media.toString(), null, 8, 0.15, 2, Duration.ofDays(30),
                null, 0, true));
        studio = new StudioService(new StudioPlanner(new RecordingLlm(), p), tts, stock, new NotificationService(notes, clock), audit, p, clock);
        return studio;
    }

    @AfterEach
    void stop() {
        if (studio != null) {
            studio.stop();
        }
    }

    @Test
    void asksOnlyWhatIsMissingThenWaitsForStart() {
        StudioService s = service(new FakeTts(true), new FakeStock());
        assertThat(s.respond("c1", "what is kubernetes?", false)).isEmpty();

        StudioReply r = s.respond("c1", "create me a video on the kubernetes", false).orElseThrow();
        assertThat(r.kind()).isEqualTo(StudioReply.Kind.ASK);
        assertThat(r.text()).contains("How long");
        assertThat(r.options()).contains("15 × 45 sec Shorts series");

        r = s.respond("c1", "45 sec short videos i want in series of whole kubernetes for the 15 videos", false).orElseThrow();
        assertThat(r.kind()).isEqualTo(StudioReply.Kind.ASK);
        assertThat(r.text()).contains("voice");
        assertThat(r.options()).contains("Voice + captions", "Captions only");

        r = s.respond("c1", "Voice + captions", false).orElseThrow();
        assertThat(r.kind()).isEqualTo(StudioReply.Kind.CONFIRM);
        assertThat(r.text()).contains("15 Shorts of 45 sec each on Kubernetes").contains("1080×1920").contains("Fake voice").contains("Start?");
        assertThat(r.plan()).hasSize(15).first().asString().startsWith("1. Kubernetes");
        assertThat(r.options()).containsExactly("Start", "Change length", "Cancel");
        assertThat(s.projects()).isEmpty(); // nothing renders before Start

        r = s.respond("c1", "Cancel", false).orElseThrow();
        assertThat(r.text()).contains("Cancelled");
        assertThat(s.respond("c1", "Start", false)).isEmpty();
        assertThat(s.projects()).isEmpty();
    }

    @Test
    void oneMessageWithEverythingGoesStraightToThePlan() {
        StudioService s = service(new FakeTts(false), new FakeStock());
        StudioReply r = s.respond("c2", "build me a video of 2 min about arctic fox with captions only", false).orElseThrow();
        assertThat(r.kind()).isEqualTo(StudioReply.Kind.CONFIRM);
        assertThat(r.text()).contains("a 2 min video on Arctic fox").contains("1920×1080").contains("voice: no voice").contains("captions: on");
        assertThat(r.plan()).hasSize(1);

        StudioReply bare = s.respond("c3", "Black holes", true).orElseThrow(); // inside the Studio feature, a bare topic is enough
        assertThat(bare.text()).contains("How long");
        assertThat(s.respond("c4", "Which topics are trending this week?", true)).isEmpty();
    }

    @Test
    void rendersUploadReadyVideosWithVoicePhotosCaptionsAndMetadata() throws Exception {
        assumeTrue(VideoRenderer.ffmpegAvailable("ffmpeg"), "ffmpeg not installed");
        FakeTts tts = new FakeTts(true);
        FakeStock stock = new FakeStock();
        StudioService s = service(tts, stock);
        s.respond("c5", "make 2 shorts about the arctic fox, 12 sec each", false);
        StudioReply confirm = s.respond("c5", "yes", false).orElseThrow();
        assertThat(confirm.kind()).isEqualTo(StudioReply.Kind.CONFIRM);
        StudioReply started = s.respond("c5", "Start", false).orElseThrow();
        assertThat(started.kind()).isEqualTo(StudioReply.Kind.STARTED);
        StudioProject p = s.project(started.projectId()).orElseThrow();

        List<VideoJob> jobs = waitForAll(s, p);
        assertThat(jobs).extracting(VideoJob::status).containsOnly(VideoJob.Status.READY);
        assertThat(tts.calls).isGreaterThan(3);
        assertThat(stock.calls).isGreaterThan(0);
        VideoJob first = jobs.get(0);
        assertThat(first.voice()).isEqualTo("Fake voice");
        assertThat(first.progress()).isEqualTo(100);

        Path mp4 = s.file(first.id(), "video.mp4").orElseThrow();
        String probe = probe(mp4);
        assertThat(probe).contains("codec_name=h264").contains("profile=High").contains("pix_fmt=yuv420p").contains("width=162")
                .contains("height=288").contains("codec_name=aac").contains("sample_rate=48000");
        double seconds = Double.parseDouble(probe.replaceAll("(?s).*format_duration=([0-9.]+).*", "$1"));
        assertThat(seconds).isCloseTo(first.seconds(), org.assertj.core.api.Assertions.within(0.4));
        byte[] head = Files.readAllBytes(mp4);
        assertThat(new String(head, 0, 64, StandardCharsets.ISO_8859_1).indexOf("moov")).as("+faststart: moov before mdat").isGreaterThan(0);

        assertThat(Files.readString(s.file(first.id(), "captions.srt").orElseThrow())).startsWith("1\n00:00:00,250 --> ");
        assertThat(s.file(first.id(), "thumbnail.jpg")).isPresent();
        String meta = Files.readString(s.file(first.id(), "metadata.json").orElseThrow());
        assertThat(meta).contains("\"part\" : \"1/2\"").contains("#Shorts").contains("Jane Doe").contains("CC BY 2.0").contains("\"draftScript\" : true");
        assertThat(s.file(first.id(), "../../etc/passwd")).isEmpty();
        assertThat(Files.list(mp4.getParent()).map(f -> f.getFileName().toString()))
                .containsExactlyInAnyOrder("video.mp4", "captions.srt", "thumbnail.jpg", "metadata.json");
        assertThat(notes.findAll()).extracting(n -> n.title()).contains("Video ready");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> export = (List<Map<String, Object>>) s.export();
        assertThat(export).hasSize(1).first().satisfies(e -> assertThat(e).containsEntry("topic", "The arctic fox"));
        clock.advance(Duration.ofDays(31));
        assertThat(s.purgeExpired(clock.instant())).isEqualTo(1);
        assertThat(s.projects()).isEmpty();
        assertThat(Files.exists(media.resolve(p.id()))).isFalse();
    }

    @Test
    void failuresAreReportedNotThrown() {
        StudioService s = service(new FakeTts(true) {
            @Override
            public byte[] synthesize(String text) {
                throw new TtsException("voice service unavailable");
            }
        }, new FakeStock());
        s.respond("c6", "make a 15 sec short about owls with voice and captions", false);
        StudioProject p = s.project(s.respond("c6", "go", false).orElseThrow().projectId()).orElseThrow();
        List<VideoJob> jobs = waitForAll(s, p);
        assertThat(jobs.get(0).status()).isEqualTo(VideoJob.Status.FAILED);
        assertThat(jobs.get(0).error()).contains("voice service unavailable");
        assertThat(notes.findAll()).extracting(n -> n.title()).contains("Video failed");
        s.deleteAll();
        assertThat(s.projects()).isEmpty();
    }

    static List<VideoJob> waitForAll(StudioService s, StudioProject p) {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (System.nanoTime() < until) {
            List<VideoJob> jobs = s.jobs(p);
            if (jobs.stream().allMatch(j -> j.status() == VideoJob.Status.READY || j.status() == VideoJob.Status.FAILED)) {
                return jobs;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("jobs did not finish: " + s.jobs(p));
    }

    static String probe(Path mp4) throws IOException, InterruptedException {
        Process pr = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries",
                "stream=codec_name,profile,pix_fmt,width,height,sample_rate:format=duration", "-of", "flat=s=_", mp4.toString())
                .redirectErrorStream(true).start();
        String out = new String(pr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        pr.waitFor();
        return out.replaceAll("streams_stream_\\d+_", "").replace("\"", "");
    }
}
