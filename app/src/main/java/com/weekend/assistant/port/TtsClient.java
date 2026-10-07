package com.weekend.assistant.port;

/** Turns narration text into speech (16-bit PCM WAV). 🔓 Cloud providers receive the script text (P7). */
public interface TtsClient {

    boolean enabled();

    /** Provider name for the UI and metadata, e.g. "Google Cloud TTS (en-IN-Neural2-B)". */
    String label();

    /** False for voices whose licence does not allow monetised YouTube use (e.g. macOS system voices). */
    boolean commercialUse();

    byte[] synthesize(String text);

    class TtsException extends RuntimeException {
        public TtsException(String message) {
            super(message);
        }
    }
}
