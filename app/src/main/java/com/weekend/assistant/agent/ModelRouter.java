package com.weekend.assistant.agent;

import com.weekend.assistant.config.WeekendProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Rule-based routing (DESIGN §7.2): cheap model by default, strong model for long or hard requests. */
@Component
public class ModelRouter {

    private static final List<String> HARD_HINTS = List.of(
            "think harder", "step by step", "analyse", "analyze", "architecture", "design a", "compare", "debug", "prove");

    private final WeekendProperties.Llm llm;
    private final int strongMinChars;

    public ModelRouter(WeekendProperties props) {
        this.llm = props.llm();
        this.strongMinChars = props.agent().strongModelMinChars();
    }

    public String choose(String userText, boolean thinkHarder) {
        if (thinkHarder || userText.length() >= strongMinChars) {
            return llm.modelStrong();
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        return HARD_HINTS.stream().anyMatch(lower::contains) ? llm.modelStrong() : llm.modelDefault();
    }
}
