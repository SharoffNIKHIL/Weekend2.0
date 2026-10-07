package com.weekend.assistant.agent;

import com.weekend.assistant.config.WeekendProperties;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Back-pressure for chat turns: at most N model-backed turns run at once; others wait up to the queue time and then
 * get {@link BusyException} (HTTP 503 + Retry-After) instead of piling up threads and cost under load.
 */
@Component
public class ChatGuard {

    /** Too many turns in flight; the caller should retry after {@code retryAfter}. */
    public static class BusyException extends RuntimeException {
        private final Duration retryAfter;

        public BusyException(Duration retryAfter) {
            super("Weekend is busy with other requests; try again in a few seconds.");
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    private final Semaphore permits;
    private final Duration wait;
    private final int capacity;

    public ChatGuard(WeekendProperties props) {
        this.capacity = props.pressure().maxConcurrentChats();
        this.permits = new Semaphore(capacity, true);
        this.wait = props.pressure().queueWait();
    }

    public <T> T run(Supplier<T> work) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusyException(Duration.ofSeconds(2));
        }
        if (!acquired) {
            throw new BusyException(Duration.ofSeconds(5));
        }
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }

    public int inFlight() {
        return capacity - permits.availablePermits();
    }

    public int capacity() {
        return capacity;
    }
}
