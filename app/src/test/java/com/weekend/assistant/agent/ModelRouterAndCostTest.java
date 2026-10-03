package com.weekend.assistant.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.TestFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModelRouterAndCostTest {

    private final ModelRouter router = new ModelRouter(TestFixtures.props());
    private final CostCalculator costs = new CostCalculator(TestFixtures.props());

    @Test
    void routesSimpleToDefaultAndHardToStrong() {
        assertThat(router.choose("what's the time?", false)).isEqualTo("claude-haiku-4-5@20251001");
        assertThat(router.choose("what's the time?", true)).isEqualTo("claude-sonnet-5");
        assertThat(router.choose("Compare Firestore and Cloud SQL for me", false)).isEqualTo("claude-sonnet-5");
        assertThat(router.choose("x".repeat(1200), false)).isEqualTo("claude-sonnet-5");
    }

    @Test
    void costsMatchPerMillionPrices() {
        // Haiku: 3,000 in × $1/M + 400 out × $5/M = 0.003 + 0.002
        assertThat(costs.cost("claude-haiku-4-5@20251001", 3000, 400)).isEqualByComparingTo(new BigDecimal("0.005"));
        // Sonnet 5: 3,000 × $2/M + 400 × $10/M = 0.006 + 0.004
        assertThat(costs.cost("claude-sonnet-5", 3000, 400)).isEqualByComparingTo(new BigDecimal("0.010"));
    }
}
