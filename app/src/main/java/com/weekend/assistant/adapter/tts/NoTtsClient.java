package com.weekend.assistant.adapter.tts;

import com.weekend.assistant.port.TtsClient;

/** No voice configured: videos are made with captions and music only. */
public class NoTtsClient implements TtsClient {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String label() {
        return "No voice configured";
    }

    @Override
    public boolean commercialUse() {
        return true;
    }

    @Override
    public byte[] synthesize(String text) {
        throw new TtsException("no voice provider configured (weekend.studio.tts.provider)");
    }
}
