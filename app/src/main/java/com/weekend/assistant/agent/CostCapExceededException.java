package com.weekend.assistant.agent;

/** Thrown when today's LLM spend reached the configured cap. */
public class CostCapExceededException extends RuntimeException {
    public CostCapExceededException(String message) {
        super(message);
    }
}
