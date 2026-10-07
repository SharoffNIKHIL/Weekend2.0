package com.weekend.assistant.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.adapter.memory.InMemoryAuditLog;
import com.weekend.assistant.domain.AgentMode;
import com.weekend.assistant.domain.AgentPersona;
import com.weekend.assistant.domain.Feature;
import com.weekend.assistant.domain.FeatureGroup;
import com.weekend.assistant.security.SecretFilter;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureCatalogTest {

    private final InMemoryAuditLog audit = new InMemoryAuditLog(Clock.systemUTC());
    private final FeatureCatalog catalog = new FeatureCatalog(new SecretFilter(), audit);

    @Test
    void elevenWellFormedFeatures() {
        List<Feature> all = catalog.all();
        assertThat(all).extracting(Feature::id).containsExactly("optimal", "hard", "smooth", "focused", "research", "coding",
                "financial", "designing", "drawing", "image", "notes");
        assertThat(all).filteredOn(f -> f.group() == FeatureGroup.EVERYDAY).hasSize(4);
        assertThat(all).allSatisfy(f -> {
            assertThat(f.guidelines()).isNotEmpty();
            assertThat(f.capabilities()).isNotEmpty().allSatisfy(c -> Capability.valueOf(c));
            assertThat(f.tagline()).isNotBlank();
        });
        assertThat(all).filteredOn(Feature::makesArt).allSatisfy(f -> {
            assertThat(f.capabilities()).contains("ART");
            assertThat(String.join(" ", f.guidelines())).contains("```svg");
        });
        assertThat(all).filteredOn(Feature::acceptsImages).extracting(Feature::id).containsExactly("optimal", "designing", "drawing", "image");
        assertThat(catalog.find("notes").orElseThrow().feature().capabilities()).contains("NOTION");
        assertThat(catalog.find("research").orElseThrow().feature().capabilities()).contains("WEB", "NOTION");
        assertThat(catalog.find(null).orElseThrow().feature().id()).isEqualTo("optimal");
        assertThat(catalog.find("ghost")).isEmpty();
    }

    @Test
    void overridesApplyExportAndReset() {
        AgentPersona funny = AgentPersona.preset(AgentMode.FUNNY);
        catalog.updatePersona("coding", funny);
        catalog.updateInstructions("coding", "  Use pytest.  ");
        FeatureCatalog.Effective e = catalog.find("coding").orElseThrow();
        assertThat(e.persona()).isEqualTo(funny);
        assertThat(e.instructions()).isEqualTo("Use pytest.");
        assertThat(e.customised()).isTrue();
        assertThat(catalog.overrides()).containsOnlyKeys("coding");
        assertThat(catalog.updateInstructions("coding", " ").orElseThrow().instructions()).isNull();
        assertThat(catalog.reset("coding").orElseThrow().customised()).isFalse();
        catalog.updatePersona("notes", funny);
        catalog.resetAll();
        assertThat(catalog.overrides()).isEmpty();
        assertThat(audit.findAll()).extracting("action").contains("feature.persona", "feature.instructions", "feature.reset");
    }

    @Test
    void instructionsAreValidated() {
        assertThatThrownBy(() -> catalog.updateInstructions("coding", "x".repeat(FeatureCatalog.MAX_INSTRUCTIONS + 1)))
                .hasMessageContaining("longer than");
        assertThatThrownBy(() -> catalog.updateInstructions("coding", "use key AKIAABCDEFGHIJKLMNOP")).hasMessageContaining("must not contain");
        assertThat(catalog.updateInstructions("ghost", "x")).isEmpty();
        assertThat(catalog.updateInstructions("coding", "If I paste a secret: warn me").orElseThrow().instructions()).contains("secret");
    }
}
