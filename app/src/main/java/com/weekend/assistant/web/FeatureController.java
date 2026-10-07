package com.weekend.assistant.web;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.AgentMode;
import com.weekend.assistant.domain.AgentPersona;
import com.weekend.assistant.domain.Feature;
import com.weekend.assistant.domain.Mood;
import com.weekend.assistant.features.Capability;
import com.weekend.assistant.features.FeatureCatalog;
import com.weekend.assistant.tools.Tool;
import com.weekend.assistant.tools.ToolRegistry;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Features: what Weekend is for right now, with attached plugins/connectors, guidelines and the owner's settings. */
@RestController
@RequestMapping("/api/features")
public class FeatureController {

    private final FeatureCatalog features;
    private final ToolRegistry tools;
    private final WeekendProperties props;
    private com.weekend.assistant.studio.StudioService studio;

    public FeatureController(FeatureCatalog features, ToolRegistry tools, WeekendProperties props) {
        this.features = features;
        this.tools = tools;
        this.props = props;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setStudio(com.weekend.assistant.studio.StudioService studio) {
        this.studio = studio;
    }

    /** A plugin or connector and whether it works right now (and why not). */
    public record CapabilityView(String id, String name, Capability.Kind kind, String description, boolean available, String reason) {}

    public record FeatureView(String id, String name, String group, String icon, String tagline, String description,
            List<String> guidelines, List<CapabilityView> capabilities, AgentPersona persona, AgentPersona defaults, Mood mood,
            boolean focusMode, AgentPersona.Budget budget, String instructions, boolean customised, boolean acceptsImages,
            boolean makesArt, boolean dataLeavesIndia) {}

    public record ModeView(AgentMode mode, AgentPersona persona, Mood mood) {}

    public record Catalog(List<FeatureView> features, List<ModeView> modes) {}

    public record InstructionsRequest(String text) {}

    @GetMapping
    public Catalog list() {
        List<ModeView> modes = Arrays.stream(AgentMode.values()).filter(m -> m != AgentMode.CUSTOM)
                .map(m -> new ModeView(m, AgentPersona.preset(m), AgentPersona.preset(m).mood())).toList();
        return new Catalog(features.effectiveAll().stream().map(this::view).toList(), modes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeatureView> get(@PathVariable String id) {
        return ResponseEntity.of(features.find(id).filter(e -> e.feature().id().equals(id)).map(this::view));
    }

    @PutMapping("/{id}/persona")
    public ResponseEntity<FeatureView> persona(@PathVariable String id, @RequestBody AgentPersona req) {
        return ResponseEntity.of(exists(id) ? features.updatePersona(id, req).map(this::view) : java.util.Optional.empty());
    }

    @PutMapping("/{id}/instructions")
    public ResponseEntity<FeatureView> instructions(@PathVariable String id, @RequestBody InstructionsRequest req) {
        return ResponseEntity.of(exists(id) ? features.updateInstructions(id, req.text()).map(this::view) : java.util.Optional.empty());
    }

    @PostMapping("/{id}/reset")
    public ResponseEntity<FeatureView> reset(@PathVariable String id) {
        return ResponseEntity.of(exists(id) ? features.reset(id).map(this::view) : java.util.Optional.empty());
    }

    private boolean exists(String id) {
        return features.all().stream().anyMatch(f -> f.id().equals(id));
    }

    private FeatureView view(FeatureCatalog.Effective e) {
        Feature f = e.feature();
        AgentPersona p = e.persona();
        List<CapabilityView> caps = f.capabilities().stream().map(Capability::valueOf).map(this::capability).toList();
        boolean abroad = "vertex".equals(props.llm().provider()) && !props.llm().vertexLocation().startsWith("asia-south");
        return new FeatureView(f.id(), f.name(), f.group().name(), f.icon(), f.tagline(), f.description(), f.guidelines(), caps, p,
                f.persona(), p.mood(), p.focusMode(), p.budget(), e.instructions(), e.customised(), f.acceptsImages(), f.makesArt(), abroad);
    }

    private CapabilityView capability(Capability c) {
        switch (c) {
            case RENDER, RESEARCH, VOICE, PHOTOS -> {
                return studioCapability(c);
            }
            default -> { }
        }
        boolean ok = c.tools().stream().allMatch(t -> tools.find(t).map(Tool::available).orElse(false));
        String reason = ok ? null : switch (c) {
            case WEB -> "Off: allow a web host (WEEKEND_WEB_ALLOWED_HOSTS) after a security checkpoint";
            case NOTION -> "Off: connect Notion (WEEKEND_NOTION_TOKEN) after a security checkpoint";
            default -> "Not available";
        };
        return new CapabilityView(c.name(), c.label(), c.kind(), c.description(), ok, reason);
    }

    private CapabilityView studioCapability(Capability c) {
        com.weekend.assistant.studio.StudioService.Health h = studio == null ? null : studio.health();
        boolean ok = h != null && switch (c) {
            case RENDER -> h.render();
            case RESEARCH -> h.research();
            case VOICE -> h.voice();
            default -> h.photos();
        };
        String reason = ok ? (c == Capability.VOICE && !h.voiceCommercial() ? "Draft voice (" + h.voiceLabel() + "): not for monetised uploads" : null)
                : switch (c) {
                    case RENDER -> "Off: install ffmpeg (brew install ffmpeg) or set WEEKEND_FFMPEG";
                    case RESEARCH -> "Off: offline draft scripts until Claude web search is enabled (WEEKEND_STUDIO_WEB_SEARCHES) after a security checkpoint";
                    case VOICE -> "Off: set WEEKEND_TTS=google (or say for drafts) after a security checkpoint";
                    default -> "Off: allow commons.wikimedia.org and upload.wikimedia.org (WEEKEND_WEB_ALLOWED_HOSTS) after a security checkpoint";
                };
        return new CapabilityView(c.name(), c.label(), c.kind(), c.description(), ok, reason);
    }
}
