package com.weekend.assistant.studio;

import com.weekend.assistant.config.WeekendProperties;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import javax.imageio.ImageIO;

/**
 * Turns a script into an upload-ready MP4: frames are painted in Java and piped as raw video into ffmpeg together
 * with the narration and the background pad; output is H.264 (High, yuv420p) + AAC 48 kHz with +faststart — the
 * settings YouTube recommends. Also writes a thumbnail and SRT captions.
 */
public final class VideoRenderer {

    /** What one render produced. */
    public record Output(Path mp4, Path srt, Path thumbnail, double seconds, int width, int height) {}

    private final WeekendProperties.Studio cfg;

    public VideoRenderer(WeekendProperties.Studio cfg) {
        this.cfg = cfg;
    }

    /** True when the configured ffmpeg runs. */
    public static boolean ffmpegAvailable(String ffmpeg) {
        try {
            Process p = new ProcessBuilder(ffmpeg, "-hide_banner", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public Output render(Timeline timeline, VideoFormat format, boolean captions, String badge, String nextTitle,
            Map<Script.Scene, BufferedImage> images, Map<Script.Scene, String> credits, Path dir, IntConsumer progress) {
        int w = even(format.width() * cfg.scale());
        int h = even(format.height() * cfg.scale());
        int fps = cfg.fps();
        double total = timeline.total();
        int frames = (int) Math.ceil(total * fps);
        Painter painter = new Painter(w, h, timeline, badge, captions, images, credits, nextTitle);
        try {
            Files.createDirectories(dir);
            Path narration = null;
            List<short[]> clips = new ArrayList<>();
            List<Double> starts = new ArrayList<>();
            for (Timeline.Slot s : timeline.slots()) {
                if (s.audio() != null && s.audio().length > 0) {
                    clips.add(s.audio());
                    starts.add(s.start() + Timeline.LEAD);
                }
            }
            if (!clips.isEmpty()) {
                narration = dir.resolve("narration.wav");
                Files.write(narration, Wav.write(Wav.place(clips, starts, total)));
            }
            Path music = null;
            if (cfg.music()) {
                music = dir.resolve("music.wav");
                Files.write(music, Wav.write(Wav.pad(total)));
            }
            Path mp4 = dir.resolve("video.mp4");
            List<String> cmd = new ArrayList<>(List.of(cfg.ffmpeg(), "-y", "-hide_banner", "-loglevel", "error",
                    "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", w + "x" + h, "-r", String.valueOf(fps), "-i", "-"));
            int audioInputs = 0;
            if (narration != null) {
                cmd.addAll(List.of("-i", narration.toString()));
                audioInputs++;
            }
            if (music != null) {
                cmd.addAll(List.of("-i", music.toString()));
                audioInputs++;
            }
            if (audioInputs == 0) {
                cmd.addAll(List.of("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"));
                cmd.addAll(List.of("-map", "0:v", "-map", "1:a"));
            } else if (audioInputs == 1) {
                cmd.addAll(List.of("-map", "0:v", "-map", "1:a"));
            } else {
                cmd.addAll(List.of("-filter_complex", "[1:a]volume=1.0[n];[2:a]volume=0.55[m];[n][m]amix=inputs=2:duration=longest:normalize=0[a]",
                        "-map", "0:v", "-map", "[a]"));
            }
            cmd.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-profile:v", "high", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-ac", "2", "-movflags", "+faststart",
                    "-t", String.format(java.util.Locale.ROOT, "%.3f", total), mp4.toString()));

            Process ff = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            ByteArrayOutputStream log = new ByteArrayOutputStream();
            Thread drain = new Thread(() -> {
                try (InputStream in = ff.getInputStream()) {
                    in.transferTo(log);
                } catch (IOException ignored) {
                    // process ended
                }
            }, "ffmpeg-log");
            drain.setDaemon(true);
            drain.start();
            BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
            byte[] raw = ((DataBufferByte) frame.getRaster().getDataBuffer()).getData();
            int lastPct = -1;
            try (OutputStream out = ff.getOutputStream()) {
                for (int i = 0; i < frames; i++) {
                    Graphics2D g = frame.createGraphics();
                    painter.paint(g, i / (double) fps);
                    g.dispose();
                    out.write(raw);
                    int pct = (int) (100L * (i + 1) / frames);
                    if (pct != lastPct) {
                        progress.accept(pct);
                        lastPct = pct;
                    }
                }
            } catch (IOException e) {
                ff.destroy();
                throw new IllegalStateException("ffmpeg stopped while rendering: " + tail(log));
            }
            if (!ff.waitFor(10, TimeUnit.MINUTES) || ff.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg failed: " + tail(log));
            }
            drain.join(2000);

            Path thumb = dir.resolve("thumbnail.jpg");
            BufferedImage t = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D tg = t.createGraphics();
            painter.paint(tg, Math.min(1.2, total / 2));
            tg.dispose();
            ImageIO.write(t, "jpg", thumb.toFile());

            Path srt = dir.resolve("captions.srt");
            Files.writeString(srt, timeline.srt(), StandardCharsets.UTF_8);
            Files.deleteIfExists(dir.resolve("narration.wav"));
            Files.deleteIfExists(dir.resolve("music.wav"));
            return new Output(mp4, srt, thumb, total, w, h);
        } catch (IOException e) {
            throw new IllegalStateException("could not write the video (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted");
        }
    }

    static int even(double v) {
        int i = (int) Math.round(v);
        return i % 2 == 0 ? i : i + 1;
    }

    private static String tail(ByteArrayOutputStream log) {
        String s = log.toString(StandardCharsets.UTF_8).strip();
        return s.length() > 300 ? s.substring(s.length() - 300) : s;
    }
}
