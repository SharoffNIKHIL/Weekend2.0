package com.weekend.assistant.agent;

import com.weekend.assistant.config.WeekendProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Token cost in USD from the configured per-million prices (Vertex AI global endpoint, DESIGN §21.1). */
@Component
public class CostCalculator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final WeekendProperties.Llm llm;

    public CostCalculator(WeekendProperties props) {
        this.llm = props.llm();
    }

    public BigDecimal cost(String model, int inputTokens, int outputTokens) {
        boolean strong = model.equals(llm.modelStrong()) && !model.equals(llm.modelDefault());
        BigDecimal in = strong ? llm.strongInputPrice() : llm.defaultInputPrice();
        BigDecimal out = strong ? llm.strongOutputPrice() : llm.defaultOutputPrice();
        return in.multiply(BigDecimal.valueOf(inputTokens))
                .add(out.multiply(BigDecimal.valueOf(outputTokens)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
