package com.weekend.assistant.adapter.tts;

import com.weekend.assistant.port.TtsClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * macOS {@code say} for local DRAFT narration: free and offline (nothing leaves the Mac), but Apple's licence covers
 * personal, non-commercial use only — so videos made with it are marked "draft voice" and are not for monetised uploads.
 */
public class MacSayTtsClient implements TtsClient {

    private final String voice;

    public MacSayTtsClient(String voice) {
        this.voice = voice;
    }

    public static boolean available() {
        return Files.isExecutable(Path.of("/usr/bin/say"));
    }

    @Override
    public boolean enabled() {
        return available();
    }

    @Override
    public String label() {
        return "macOS voice " + voice + " (draft only)";
    }

    @Override
    public boolean commercialUse() {
        return false;
    }

    @Override
    public byte[] synthesize(String text) {
        Path out = null;
        try {
            out = Files.createTempFile("weekend-say", ".wav");
            Process p = new ProcessBuilder(List.of("/usr/bin/say", "-v", voice, "-o", out.toString(), "--file-format=WAVE",
                    "--data-format=LEI16@24000", "--", text)).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new TtsException("macOS say failed");
            }
            return Files.readAllBytes(out);
        } catch (IOException e) {
            throw new TtsException("macOS say not available");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("interrupted");
        } finally {
            if (out != null) {
                try {
                    Files.deleteIfExists(out);
                } catch (IOException ignored) {
                    // temp file; the OS cleans it up
                }
            }
        }
    }
}
