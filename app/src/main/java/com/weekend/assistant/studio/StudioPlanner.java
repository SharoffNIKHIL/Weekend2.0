package com.weekend.assistant.studio;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.port.LlmProvider;
import com.weekend.assistant.port.LlmProvider.Citation;
import com.weekend.assistant.port.LlmProvider.LlmRequest;
import com.weekend.assistant.port.LlmProvider.LlmResponse;
import com.weekend.assistant.port.LlmProvider.UserText;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude as producer: outlines a series in viewing order, then writes each video's script to an exact word budget,
 * optionally researching the web first (Claude's own web search, 🔓 P7). When the model cannot give valid JSON (always
 * the case with the offline model) it falls back to a clearly labelled draft so the pipeline still works end to end.
 */
@Service
public class StudioPlanner {

    static final String SYSTEM = """
            You are the producer for the owner's YouTube channel, working inside Weekend Studio.
            Be accurate: leave out anything you are not sure of. Write for spoken delivery: short sentences, no lists in the narration.
            Never copy text from sources; explain in your own words. Reply with JSON only, no prose around it.
            """;

    private final LlmProvider llm;
    private final WeekendProperties props;
    private final JsonMapper json = JsonMapper.builder().build();

    public StudioPlanner(LlmProvider llm, WeekendProperties props) {
        this.llm = llm;
        this.props = props;
    }

    public SeriesPlan plan(VideoBrief brief) {
        int n = brief.countOrOne();
        String prompt = "Plan " + n + " YouTube " + (brief.formatOrDefault() == VideoFormat.SHORT ? "Shorts" : "videos") + " of "
                + brief.seconds() + " seconds each about \"" + brief.topic() + "\". "
                + (n > 1 ? "Together they must cover the whole topic end to end, in a sensible learning order, without overlap. " : "")
                + "JSON: {\"episodes\":[{\"title\":\"catchy title, max 60 characters\",\"focus\":\"one sentence on what this video covers\"}]} "
                + "with exactly " + n + " items.";
        LlmResponse res = ask(prompt, 1500);
        Optional<Map<String, Object>> parsed = parse(res.text());
        List<SeriesPlan.Episode> eps = new ArrayList<>();
        if (parsed.isPresent() && parsed.get().get("episodes") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("title") instanceof String t && !t.isBlank() && eps.size() < n) {
                    eps.add(new SeriesPlan.Episode(eps.size() + 1, clip(t, 80), clip(str(m, "focus"), 200)));
                }
            }
        }
        if (eps.size() == n) {
            return new SeriesPlan(brief.topic(), eps, sources(res.citations()), false);
        }
        return draftPlan(brief);
    }

    public Script script(VideoBrief brief, SeriesPlan plan, SeriesPlan.Episode ep) {
        int words = brief.wordBudget();
        int scenes = Math.max(3, Math.min(10, (brief.seconds() == null ? 45 : brief.seconds()) / 9));
        String next = ep.number() < plan.episodes().size() ? plan.episodes().get(ep.number()).title() : null;
        String prompt = "Write the script for " + (plan.episodes().size() > 1 ? "part " + ep.number() + " of " + plan.episodes().size() + ", " : "")
                + "\"" + ep.title() + "\" — " + ep.focus() + " Series topic: " + plan.topic() + ". "
                + "Length: " + brief.seconds() + " seconds, so about " + words + " words of narration in total (strict). "
                + "Format: " + (brief.formatOrDefault() == VideoFormat.SHORT ? "vertical YouTube Short" : "landscape YouTube video") + ". "
                + "Use about " + scenes + " scenes: first a TITLE scene with a hook, then POINTS scenes (technical ideas) or IMAGE scenes "
                + "(real-world subjects: animals, places, objects), last an OUTRO" + (next != null ? " that teases the next part: \"" + next + "\"" : "")
                + ". JSON: {\"title\":\"YouTube title, max 70 characters\",\"description\":\"2-3 sentences\",\"tags\":[\"max 8\"],"
                + "\"scenes\":[{\"kind\":\"TITLE|POINTS|IMAGE|OUTRO\",\"heading\":\"max 6 words\",\"bullets\":[\"max 3, max 7 words each\"],"
                + "\"narration\":\"the words spoken in this scene\",\"imageQuery\":\"2-4 plain words for a photo (IMAGE scenes only)\"}]}";
        LlmResponse res = ask(prompt, 3000);
        Optional<Script> s = parse(res.text()).flatMap(m -> toScript(m, sources(res.citations())));
        return s.orElseGet(() -> draftScript(brief, plan, ep));
    }

    private LlmResponse ask(String prompt, int maxTokens) {
        try {
            return llm.complete(new LlmRequest(props.llm().modelStrong(), SYSTEM, List.of(new UserText(prompt)), List.of(), maxTokens, 0.4,
                    props.studio().webSearches()));
        } catch (RuntimeException e) {
            return new LlmResponse("", List.of(), "error", 0, 0);
        }
    }

    @SuppressWarnings("unchecked")
    Optional<Map<String, Object>> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        if (text.contains("max 60 characters") || text.contains("max 70 characters")) {
            return Optional.empty(); // the prompt's own JSON template echoed back, not an answer
        }
        int a = text.indexOf('{');
        int b = text.lastIndexOf('}');
        if (a < 0 || b <= a) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(text.substring(a, b + 1), Map.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private Optional<Script> toScript(Map<String, Object> m, List<Script.Source> sources) {
        if (!(m.get("title") instanceof String title) || !(m.get("scenes") instanceof List<?> raw) || raw.isEmpty()) {
            return Optional.empty();
        }
        List<Script.Scene> scenes = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> s) || scenes.size() >= 12) {
                continue;
            }
            Script.Kind kind;
            try {
                kind = Script.Kind.valueOf(String.valueOf(s.get("kind")).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                kind = Script.Kind.POINTS;
            }
            List<String> bullets = s.get("bullets") instanceof List<?> bl
                    ? bl.stream().map(String::valueOf).filter(x -> !x.isBlank()).limit(3).map(x -> clip(x, 60)).toList() : List.of();
            String query = s.get("imageQuery") instanceof String q && !q.isBlank() ? clip(q, 60) : null;
            scenes.add(new Script.Scene(kind, clip(str(s, "heading"), 60), bullets,
                    clip(str(s, "narration"), 1200), query));
        }
        if (scenes.size() < 2 || scenes.stream().filter(sc -> !sc.narration().isBlank()).count() < 2) {
            return Optional.empty();
        }
        List<String> tags = m.get("tags") instanceof List<?> tl ? tl.stream().map(String::valueOf).limit(8).toList() : List.of();
        return Optional.of(new Script(clip(title, 90), clip(str(m, "description"), 1000), tags, scenes, sources, false));
    }

    // ---------- offline drafts ----------
    static final List<String> ASPECTS = List.of("What it is", "Why it matters", "The core idea", "How it works", "Key building blocks",
            "Getting started", "A real example", "Common uses", "Best practices", "Mistakes to avoid", "Tools and ecosystem",
            "Going deeper", "Performance tips", "Security basics", "Troubleshooting", "Recap and what next");

    static SeriesPlan draftPlan(VideoBrief brief) {
        int n = brief.countOrOne();
        List<SeriesPlan.Episode> eps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String aspect = n == 1 ? "Explained" : ASPECTS.get(Math.min(i, ASPECTS.size() - 1)) + (i >= ASPECTS.size() ? " " + (i - ASPECTS.size() + 2) : "");
            eps.add(new SeriesPlan.Episode(i + 1, brief.topic() + ": " + aspect, aspect + " — part " + (i + 1) + " of " + n + "."));
        }
        return new SeriesPlan(brief.topic(), eps, List.of(), true);
    }

    static Script draftScript(VideoBrief brief, SeriesPlan plan, SeriesPlan.Episode ep) {
        String topic = plan.topic();
        String aspect = ep.title().contains(": ") ? ep.title().substring(ep.title().indexOf(": ") + 2) : ep.title();
        int budget = brief.wordBudget();
        int total = plan.episodes().size();
        boolean visual = !topic.toLowerCase(Locale.ROOT).matches(".*\\b(kubernetes|docker|terraform|python|java|aws|cloud|api|code|linux|git|devops|sql|network\\w*)\\b.*");
        List<Script.Scene> scenes = new ArrayList<>();
        scenes.add(new Script.Scene(Script.Kind.TITLE, ep.title(), List.of(),
                (total > 1 ? "Part " + ep.number() + " of " + total + ". " : "") + topic + ". " + aspect + ", in under " + brief.seconds() + " seconds.", null));
        String[] lines = {
            "Here is the short version of " + ("Explained".equals(aspect) ? topic : aspect.toLowerCase(Locale.ROOT) + " in " + topic) + ".",
            "Start with the big picture, then look at one detail that makes it click.",
            "Notice how each piece connects to the next; that is what makes " + topic + " work.",
            "Keep this in mind, because the next idea builds on it.",
            "Try explaining it in your own words; if you can, you have got it."};
        int middle = Math.max(1, Math.min(6, (brief.seconds() == null ? 45 : brief.seconds()) / 12));
        int used = scenes.get(0).words();
        for (int i = 0; i < middle; i++) {
            String narration = lines[i % lines.length];
            int target = Math.max(8, (budget - used - 12) / (middle - i));
            StringBuilder sb = new StringBuilder(narration);
            int k = 1;
            // whole sentences only, so the voice never stops mid-thought
            while (k < lines.length && sb.toString().split("\\s+").length + lines[(i + k) % lines.length].split("\\s+").length <= target) {
                sb.append(' ').append(lines[(i + k++) % lines.length]);
            }
            String text = sb.toString();
            used += text.split("\\s+").length;
            boolean image = visual && i % 2 == 0;
            scenes.add(new Script.Scene(image ? Script.Kind.IMAGE : Script.Kind.POINTS, i == 0 ? aspect : "Key point " + (i + 1),
                    image ? List.of() : List.of("The big picture", "One detail that matters", "How it connects"), text, image ? topic : null));
        }
        String next = ep.number() < total ? plan.episodes().get(ep.number()).title() : null;
        scenes.add(new Script.Scene(Script.Kind.OUTRO, "Thanks for watching", List.of(),
                next != null ? "Next up: " + next + ". Follow so you don't miss it." : "Subscribe for more on " + topic + ".", null));
        return new Script(ep.title(), "Draft script made offline. Connect Claude for researched, accurate scripts. Topic: " + topic + ".",
                List.of(topic.toLowerCase(Locale.ROOT), "shorts", "explained"), scenes, List.of(), true);
    }

    static List<Script.Source> sources(List<Citation> cites) {
        LinkedHashSet<Script.Source> out = new LinkedHashSet<>();
        cites.forEach(c -> out.add(new Script.Source(c.title(), c.url())));
        return List.copyOf(out);
    }

    static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    static String clip(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
