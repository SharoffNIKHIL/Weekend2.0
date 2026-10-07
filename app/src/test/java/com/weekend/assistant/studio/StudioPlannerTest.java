package com.weekend.assistant.studio;

import static org.assertj.core.api.Assertions.assertThat;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.agent.RecordingLlm;
import com.weekend.assistant.port.LlmProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The producer: Claude's JSON plans and scripts (with citations), and the labelled offline drafts. */
class StudioPlannerTest {

    static final VideoBrief K8S = new VideoBrief("Kubernetes", 45, 15, VideoFormat.SHORT, true, true);

    @Test
    void offlineModelGivesLabelledDraftsThatFitTheBrief() {
        StudioPlanner planner = new StudioPlanner(new RecordingLlm(), TestFixtures.props());
        SeriesPlan plan = planner.plan(K8S);
        assertThat(plan.draft()).isTrue();
        assertThat(plan.episodes()).hasSize(15).extracting(SeriesPlan.Episode::number).containsExactlyElementsOf(range(1, 15));
        assertThat(plan.episodes()).extracting(SeriesPlan.Episode::title).doesNotHaveDuplicates();
        Script s = planner.script(K8S, plan, plan.episodes().get(0));
        assertThat(s.draft()).isTrue();
        assertThat(s.scenes().get(0).kind()).isEqualTo(Script.Kind.TITLE);
        assertThat(s.scenes().get(s.scenes().size() - 1).kind()).isEqualTo(Script.Kind.OUTRO);
        assertThat(s.scenes().get(s.scenes().size() - 1).narration()).contains(plan.episodes().get(1).title());
        assertThat(s.words()).isBetween(40, K8S.wordBudget() + 25);
        assertThat(s.scenes()).noneMatch(sc -> sc.kind() == Script.Kind.IMAGE); // technical topic: graphics

        VideoBrief fox = new VideoBrief("Arctic fox", 120, null, null, true, true);
        Script f = planner.script(fox, planner.plan(fox), planner.plan(fox).episodes().get(0));
        assertThat(f.scenes()).anyMatch(sc -> sc.kind() == Script.Kind.IMAGE && "Arctic fox".equals(sc.imageQuery()));
    }

    @Test
    void usesClaudesJsonWithWebSearchAndCitations() {
        List<LlmProvider.LlmRequest> seen = new ArrayList<>();
        LlmProvider fake = req -> {
            seen.add(req);
            String text = req.turns().toString().contains("Plan 2")
                    ? "Here you go: {\"episodes\":[{\"title\":\"Meet the Arctic fox\",\"focus\":\"Where it lives\"},"
                      + "{\"title\":\"Winter coat\",\"focus\":\"How it survives\"}]}"
                    : "{\"title\":\"Meet the Arctic fox\",\"description\":\"A small fox of the far north.\",\"tags\":[\"fox\",\"arctic\"],"
                      + "\"scenes\":[{\"kind\":\"title\",\"heading\":\"Arctic fox\",\"narration\":\"Meet the Arctic fox.\"},"
                      + "{\"kind\":\"IMAGE\",\"heading\":\"Home\",\"narration\":\"It lives on the tundra.\",\"imageQuery\":\"arctic fox tundra\"},"
                      + "{\"kind\":\"WEIRD\",\"heading\":\"Coat\",\"bullets\":[\"Thick fur\",\"\"],\"narration\":\"Its fur changes colour.\"},"
                      + "{\"kind\":\"OUTRO\",\"heading\":\"Next\",\"narration\":\"Next: winter coat.\"}]}";
            return new LlmProvider.LlmResponse(text, List.of(), "end_turn", 10, 10,
                    List.of(new LlmProvider.Citation("Arctic fox - Example", "https://example.org/fox")));
        };
        StudioPlanner planner = new StudioPlanner(fake, TestFixtures.withStudio(
                new com.weekend.assistant.config.WeekendProperties.Studio(null, null, 0, 0, 0, null, null, 3, true)));
        VideoBrief brief = new VideoBrief("Arctic fox", 60, 2, VideoFormat.SHORT, true, true);
        SeriesPlan plan = planner.plan(brief);
        assertThat(plan.draft()).isFalse();
        assertThat(plan.episodes()).extracting(SeriesPlan.Episode::title).containsExactly("Meet the Arctic fox", "Winter coat");
        assertThat(plan.sources()).extracting(Script.Source::url).containsExactly("https://example.org/fox");
        Script s = planner.script(brief, plan, plan.episodes().get(0));
        assertThat(s.draft()).isFalse();
        assertThat(s.scenes()).extracting(Script.Scene::kind)
                .containsExactly(Script.Kind.TITLE, Script.Kind.IMAGE, Script.Kind.POINTS, Script.Kind.OUTRO);
        assertThat(s.scenes().get(2).bullets()).containsExactly("Thick fur");
        assertThat(s.scenes().get(1).imageQuery()).isEqualTo("arctic fox tundra");
        assertThat(seen).allSatisfy(r -> assertThat(r.webSearches()).isEqualTo(3));
        assertThat(seen.get(1).turns().toString()).contains("about 132 words").contains("Winter coat");
    }

    private static List<Integer> range(int a, int b) {
        List<Integer> out = new ArrayList<>();
        for (int i = a; i <= b; i++) {
            out.add(i);
        }
        return out;
    }
}
