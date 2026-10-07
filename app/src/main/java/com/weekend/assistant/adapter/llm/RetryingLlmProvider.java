package com.weekend.assistant.adapter.llm;

import com.weekend.assistant.port.LlmProvider;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-pressure resilience: retries a failed model call (rate limits, overload, network blips) with exponential
 * backoff — backoff, 2×backoff, 4×backoff … Programming errors (IllegalArgumentException/IllegalStateException) are not
 * retried. Message content is never logged.
 */
public class RetryingLlmProvider implements LlmProvider {

    /** Sleeps between attempts; replaced in tests. */
    public interface Sleeper {
        void sleep(Duration d) throws InterruptedException;
    }

    private static final Logger log = LoggerFactory.getLogger(RetryingLlmProvider.class);

    private final LlmProvider delegate;
    private final int retries;
    private final Duration backoff;
    private final Sleeper sleeper;

    public RetryingLlmProvider(LlmProvider delegate, int retries, Duration backoff, Sleeper sleeper) {
        this.delegate = delegate;
        this.retries = retries;
        this.backoff = backoff;
        this.sleeper = sleeper;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return delegate.complete(request);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw e;
            } catch (RuntimeException e) {
                last = e;
                if (attempt == retries) {
                    break;
                }
                Duration wait = backoff.multipliedBy(1L << attempt);
                log.warn("model call failed ({}), retry {}/{} in {} ms", e.getClass().getSimpleName(), attempt + 1, retries, wait.toMillis());
                try {
                    sleeper.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }
}
