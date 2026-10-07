package com.weekend.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.adapter.llm.RetryingLlmProvider;
import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.LlmProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** High-pressure behaviour: retries with backoff, and a concurrency limit that answers "busy" instead of piling up. */
class PressureTest {

    private static final LlmProvider.LlmRequest REQ = new LlmProvider.LlmRequest("m", "s", List.of(), List.of(), 10);

    @Test
    void retriesTransientFailuresWithExponentialBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> waits = new ArrayList<>();
        LlmProvider flaky = r -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("503 overloaded");
            }
            return new LlmProvider.LlmResponse("ok", List.of(), "end_turn", 1, 1);
        };
        LlmProvider p = new RetryingLlmProvider(flaky, 2, Duration.ofMillis(100), waits::add);
        assertThat(p.complete(REQ).text()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(waits).containsExactly(Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void givesUpAfterTheRetriesAndNeverRetriesProgrammingErrors() {
        AtomicInteger calls = new AtomicInteger();
        LlmProvider down = r -> {
            calls.incrementAndGet();
            throw new RuntimeException("down");
        };
        assertThatThrownBy(() -> new RetryingLlmProvider(down, 2, Duration.ZERO, d -> { }).complete(REQ)).hasMessage("down");
        assertThat(calls).hasValue(3);

        AtomicInteger bad = new AtomicInteger();
        LlmProvider invalid = r -> {
            bad.incrementAndGet();
            throw new IllegalArgumentException("bad request");
        };
        assertThatThrownBy(() -> new RetryingLlmProvider(invalid, 5, Duration.ZERO, d -> { }).complete(REQ)).hasMessage("bad request");
        assertThat(bad).hasValue(1);
    }

    @Test
    void chatGuardLimitsConcurrencyAndAnswersBusy() throws Exception {
        WeekendProperties props = TestFixtures.with(TestFixtures.props(),
                new WeekendProperties.Pressure(2, Duration.ofMillis(150), 0, Duration.ZERO), null, null);
        ChatGuard guard = new ChatGuard(props);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            List<Future<String>> holders = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                holders.add(pool.submit(() -> guard.run(() -> {
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "done";
                })));
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(guard.inFlight()).isEqualTo(2);
            assertThatThrownBy(() -> guard.run(() -> "third")).isInstanceOf(ChatGuard.BusyException.class)
                    .satisfies(e -> assertThat(((ChatGuard.BusyException) e).retryAfter()).isPositive());
            release.countDown();
            for (Future<String> f : holders) {
                assertThat(f.get(2, TimeUnit.SECONDS)).isEqualTo("done");
            }
            assertThat(guard.run(() -> "after")).isEqualTo("after");
            assertThat(guard.inFlight()).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void manyParallelChatsAllCompleteWithinCapacity() throws Exception {
        WeekendProperties props = TestFixtures.with(TestFixtures.props(),
                new WeekendProperties.Pressure(4, Duration.ofSeconds(5), 0, Duration.ZERO), null, null);
        ChatGuard guard = new ChatGuard(props);
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger current = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int n = i;
                fs.add(pool.submit(() -> guard.run(() -> {
                    peak.accumulateAndGet(current.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    current.decrementAndGet();
                    return n;
                })));
            }
            int sum = 0;
            for (Future<Integer> f : fs) {
                sum += f.get(10, TimeUnit.SECONDS);
            }
            assertThat(sum).isEqualTo(780);
            assertThat(peak.get()).isLessThanOrEqualTo(4);
        } finally {
            pool.shutdownNow();
        }
    }
}
