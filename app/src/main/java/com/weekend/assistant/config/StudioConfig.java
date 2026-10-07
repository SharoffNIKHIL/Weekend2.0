package com.weekend.assistant.config;

import com.weekend.assistant.adapter.tts.GoogleTtsClient;
import com.weekend.assistant.adapter.tts.MacSayTtsClient;
import com.weekend.assistant.adapter.tts.NoTtsClient;
import com.weekend.assistant.port.TtsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the narration voice: none (default), google (Cloud TTS) or say (macOS drafts). */
@Configuration
public class StudioConfig {

    @Bean
    public TtsClient ttsClient(WeekendProperties props) {
        WeekendProperties.Tts t = props.studio().tts();
        return switch (t.provider()) {
            case "google" -> new GoogleTtsClient(t, GoogleTtsClient.adcToken());
            case "say" -> MacSayTtsClient.available() ? new MacSayTtsClient(t.sayVoice()) : new NoTtsClient();
            case "none" -> new NoTtsClient();
            default -> throw new IllegalStateException("unknown weekend.studio.tts.provider: " + t.provider());
        };
    }
}
