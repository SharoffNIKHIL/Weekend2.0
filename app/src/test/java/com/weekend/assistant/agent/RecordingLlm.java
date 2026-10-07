package com.weekend.assistant.agent;

import com.weekend.assistant.adapter.llm.ScriptedLlmProvider;
import com.weekend.assistant.port.LlmProvider;
import java.util.ArrayList;
import java.util.List;

/** Scripted offline model that also records every request, so tests can inspect prompts, tools and images. */
public final class RecordingLlm implements LlmProvider {

    public final List<LlmRequest> requests = new ArrayList<>();
    private final ScriptedLlmProvider inner = new ScriptedLlmProvider();

    @Override
    public LlmResponse complete(LlmRequest request) {
        requests.add(request);
        return inner.complete(request);
    }
}
