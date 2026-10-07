package com.weekend.assistant.features;

import com.weekend.assistant.domain.AgentMode;
import com.weekend.assistant.domain.AgentPersona;
import com.weekend.assistant.domain.ApprovalRange;
import com.weekend.assistant.domain.Feature;
import com.weekend.assistant.domain.FeatureGroup;
import com.weekend.assistant.domain.SearchRange;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.security.SecretFilter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * The built-in features and the owner's per-feature overrides (persona and extra instructions). Guidelines here are
 * generic; the owner's personal preferences come from their private profile memories, never from this public code.
 */
@Service
public class FeatureCatalog {

    public static final String DEFAULT_ID = "optimal";
    public static final int MAX_INSTRUCTIONS = 4000;

    private static final String SVG_RULE = "When you create a picture, reply with exactly one self-contained SVG in a ```svg code block "
            + "(no scripts, no external images or fonts), then one or two sentences about it.";

    private static final List<Feature> BUILT_IN = List.of(
            feature("optimal", "Optimal", FeatureGroup.EVERYDAY, "sparkle", "Balanced everyday help",
                    "Your all-round assistant: answers, plans, calculations, reminders and quick look-ups.",
                    List.of("Give the best answer in a sensible length; ask one question when something important is unclear.",
                            "Use tools when they make the answer more accurate."),
                    List.of(Capability.TIME, Capability.MEMORY, Capability.TASKS, Capability.MATH, Capability.WEB, Capability.VISION),
                    AgentPersona.preset(AgentMode.WORK), true, false),
            feature("hard", "Hard", FeatureGroup.EVERYDAY, "target", "Deep thinking for tough problems",
                    "Maximum effort: the strong model, more steps and careful checking for complex maths, logic and analysis.",
                    List.of("Break the problem into steps and state your assumptions.",
                            "Check every number with the math tools; never do non-trivial arithmetic in your head.",
                            "End with a clearly marked final answer and how sure you are."),
                    List.of(Capability.TIME, Capability.MEMORY, Capability.MATH, Capability.WEB),
                    new AgentPersona(AgentMode.CUSTOM, 1, 10, 8, 5, SearchRange.WEB, ApprovalRange.WRITES_AND_EXTERNAL), false, false),
            feature("smooth", "Smooth", FeatureGroup.EVERYDAY, "smile", "Relaxed, friendly conversation",
                    "Light and quick: friendly replies for everyday chat, ideas and planning your day.",
                    List.of("Be warm and natural; keep replies short unless asked for more."),
                    List.of(Capability.TIME, Capability.MEMORY, Capability.TASKS),
                    new AgentPersona(AgentMode.FUNNY, 7, 7, 4, 2, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL), false, false),
            feature("focused", "Focused", FeatureGroup.EVERYDAY, "gauge", "Short, precise, no distractions",
                    "Focus mode: the shortest correct answer, the strong model, nothing off-topic.",
                    List.of("Answer only what was asked, in as few words as possible.", "No small talk, no tangents."),
                    List.of(Capability.TIME, Capability.MEMORY, Capability.TASKS, Capability.MATH),
                    new AgentPersona(AgentMode.DISCIPLINED, 0, 9, 10, 4, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL), false, false),
            feature("research", "Research", FeatureGroup.WORKSPACE, "search", "Browse, compare, cite",
                    "Looks things up for you, cross-checks sources and gives answers with links.",
                    List.of("Rank sources: official docs first, then official blogs, then reputable publications, then forums.",
                            "Cross-check key claims with two sources and give the links.",
                            "Say clearly what you could not verify.",
                            "Never put personal details or secrets into a search."),
                    List.of(Capability.WEB, Capability.MEMORY, Capability.NOTION, Capability.MATH, Capability.TIME),
                    AgentPersona.preset(AgentMode.BROWSE), false, false),
            feature("coding", "Coding", FeatureGroup.WORKSPACE, "code", "Code, scripts, IaC and reviews",
                    "Writes and reviews code, scripts, infrastructure as code and pipelines.",
                    List.of("Give complete, runnable code with a path comment on line 1.",
                            "Follow the owner's stated language and tooling preferences from their profile.",
                            "Pin versions, never hard-code secrets, and explain non-obvious choices briefly.",
                            "End with a command to verify the result."),
                    List.of(Capability.MEMORY, Capability.MATH, Capability.WEB, Capability.TIME),
                    new AgentPersona(AgentMode.CUSTOM, 2, 10, 8, 4, SearchRange.WEB, ApprovalRange.WRITES_AND_EXTERNAL), false, false),
            feature("financial", "Financial", FeatureGroup.WORKSPACE, "money", "Budgets, bills and numbers",
                    "Budgets, cost estimates, bills and payments, with every number calculated exactly.",
                    List.of("Show amounts in INR and USD and state the exchange rate and date used.",
                            "Calculate every figure with the math tools.",
                            "Never move money and never ask for card or bank details; payments are tracked and approved only.",
                            "Label estimates as estimates. This is not financial advice."),
                    List.of(Capability.MATH, Capability.FINANCE, Capability.MEMORY, Capability.TASKS, Capability.TIME),
                    new AgentPersona(AgentMode.CUSTOM, 1, 10, 8, 4, SearchRange.MEMORY, ApprovalRange.ALL), false, false),
            feature("designing", "Designing", FeatureGroup.WORKSPACE, "edit", "UI, layouts and mockups",
                    "Designs screens, layouts and diagrams, and reviews designs you attach as images.",
                    List.of("Explain the design reasoning: hierarchy, spacing, contrast and accessibility (WCAG AA).",
                            "For mockups and wireframes, draw them as SVG.", SVG_RULE),
                    List.of(Capability.ART, Capability.VISION, Capability.MEMORY, Capability.WEB),
                    new AgentPersona(AgentMode.CUSTOM, 4, 8, 6, 3, SearchRange.WEB, ApprovalRange.WRITES_AND_EXTERNAL), true, true),
            feature("drawing", "Drawing", FeatureGroup.WORKSPACE, "star", "Original art in 4K",
                    "Creates detailed original artwork as vector graphics you can export as a 4K image.",
                    List.of(SVG_RULE,
                            "Use viewBox 0 0 3840 2160 unless another shape is asked for; build depth with layers, gradients, filters and lighting.",
                            "Create original work: no copyrighted characters or logos and no likeness of real people."),
                    List.of(Capability.ART, Capability.VISION, Capability.MEMORY),
                    new AgentPersona(AgentMode.CUSTOM, 6, 6, 5, 4, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL), true, true),
            feature("image", "Image", FeatureGroup.WORKSPACE, "eye", "Understand any picture",
                    "Analyses photos, screenshots, charts and documents you attach, and can redraw them as art.",
                    List.of("Describe what is in the image precisely; read any text in it.",
                            "Point out details that matter for the question; say when you are unsure.",
                            "Do not identify real people from their faces.", SVG_RULE),
                    List.of(Capability.VISION, Capability.ART, Capability.MEMORY),
                    new AgentPersona(AgentMode.CUSTOM, 3, 9, 7, 4, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL), true, true),
            feature("notes", "Notes", FeatureGroup.WORKSPACE, "book", "Notes, Notion and to-dos",
                    "Writes clean notes, saves them to Notion after your yes, and turns them into tasks.",
                    List.of("Write notes with a title, short headings and bullet points.",
                            "Save to Notion only through the tool, which asks the owner first.",
                            "Offer to turn action items into tasks."),
                    List.of(Capability.NOTION, Capability.MEMORY, Capability.TASKS, Capability.TIME),
                    new AgentPersona(AgentMode.CUSTOM, 2, 9, 8, 3, SearchRange.MEMORY, ApprovalRange.WRITES_AND_EXTERNAL), false, false));

    private final SecretFilter secrets;
    private final AuditLog audit;
    private final Map<String, AgentPersona> personas = new ConcurrentHashMap<>();
    private final Map<String, String> instructions = new ConcurrentHashMap<>();

    public FeatureCatalog(SecretFilter secrets, AuditLog audit) {
        this.secrets = secrets;
        this.audit = audit;
    }

    /** A feature with the owner's overrides applied. */
    public record Effective(Feature feature, AgentPersona persona, String instructions, boolean customised) {}

    public List<Feature> all() {
        return BUILT_IN;
    }

    public Optional<Effective> find(String id) {
        String key = id == null || id.isBlank() ? DEFAULT_ID : id;
        return BUILT_IN.stream().filter(f -> f.id().equals(key)).findFirst().map(this::effective);
    }

    public List<Effective> effectiveAll() {
        return BUILT_IN.stream().map(this::effective).toList();
    }

    public Optional<Effective> updatePersona(String id, AgentPersona persona) {
        if (persona == null) {
            throw new IllegalArgumentException("persona is required");
        }
        return find(id).map(e -> {
            personas.put(e.feature().id(), persona);
            audit.append("owner", "feature.persona", e.feature().id() + ":e" + persona.efficiency());
            return effective(e.feature());
        });
    }

    /** Extra owner instructions for one feature (added after the feature's guidelines). Blank clears them. */
    public Optional<Effective> updateInstructions(String id, String text) {
        String t = text == null ? "" : text.strip();
        if (t.length() > MAX_INSTRUCTIONS) {
            throw new IllegalArgumentException("instructions are longer than " + MAX_INSTRUCTIONS + " characters");
        }
        if (secrets.containsCredential(t)) {
            throw new IllegalArgumentException("instructions must not contain keys, passwords or card numbers");
        }
        return find(id).map(e -> {
            if (t.isEmpty()) {
                instructions.remove(e.feature().id());
            } else {
                instructions.put(e.feature().id(), t);
            }
            audit.append("owner", "feature.instructions", e.feature().id());
            return effective(e.feature());
        });
    }

    public Optional<Effective> reset(String id) {
        return find(id).map(e -> {
            personas.remove(e.feature().id());
            instructions.remove(e.feature().id());
            audit.append("owner", "feature.reset", e.feature().id());
            return effective(e.feature());
        });
    }

    /** The owner's overrides, for export (P6). */
    public Map<String, Map<String, Object>> overrides() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Feature f : BUILT_IN) {
            Map<String, Object> o = new LinkedHashMap<>();
            if (personas.containsKey(f.id())) {
                o.put("persona", personas.get(f.id()));
            }
            if (instructions.containsKey(f.id())) {
                o.put("instructions", instructions.get(f.id()));
            }
            if (!o.isEmpty()) {
                out.put(f.id(), o);
            }
        }
        return out;
    }

    public void resetAll() {
        personas.clear();
        instructions.clear();
    }

    private Effective effective(Feature f) {
        AgentPersona p = personas.getOrDefault(f.id(), f.persona());
        String i = instructions.get(f.id());
        return new Effective(f, p, i, personas.containsKey(f.id()) || i != null);
    }

    private static Feature feature(String id, String name, FeatureGroup group, String icon, String tagline, String description,
            List<String> guidelines, List<Capability> caps, AgentPersona persona, boolean images, boolean art) {
        return new Feature(id, name, group, icon, tagline, description, guidelines, caps.stream().map(Enum::name).toList(),
                persona, images, art);
    }
}
