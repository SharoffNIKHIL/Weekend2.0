package com.weekend.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgentPersonaTest {

    @ParameterizedTest
    @CsvSource({"FUNNY,HAPPY,MEMORY,WRITES_AND_EXTERNAL", "DISCIPLINED,SERIOUS,MEMORY,ALL", "WORK,CALM,WEB,WRITES_AND_EXTERNAL",
            "BROWSE,CURIOUS,WIDE,WRITES_ONLY"})
    void presetsSetRangesAndMood(AgentMode mode, Mood mood, SearchRange search, ApprovalRange approval) {
        AgentPersona p = AgentPersona.preset(mode);
        assertThat(p.mode()).isEqualTo(mode);
        assertThat(p.mood()).isEqualTo(mood);
        assertThat(p.search()).isEqualTo(search);
        assertThat(p.approval()).isEqualTo(approval);
    }

    @Test
    void highEfficiencyMeansFocusModeAndASeriousFace() {
        AgentPersona funnyMax = new AgentPersona(AgentMode.FUNNY, 9, 6, 3, 5, SearchRange.MEMORY, ApprovalRange.ALL);
        assertThat(funnyMax.focusMode()).isTrue();
        assertThat(funnyMax.mood()).isEqualTo(Mood.SERIOUS);
        assertThat(AgentPersona.preset(AgentMode.WORK).focusMode()).isFalse();
        assertThat(AgentPersona.DEFAULT).isEqualTo(AgentPersona.preset(AgentMode.WORK));
        assertThat(AgentPersona.preset(AgentMode.CUSTOM).mode()).isEqualTo(AgentMode.WORK);
    }

    @Test
    void slidersAreClamped() {
        AgentPersona p = new AgentPersona(AgentMode.CUSTOM, 99, -5, 11, 9, SearchRange.OFF, ApprovalRange.ALL);
        assertThat(p.humor()).isEqualTo(10);
        assertThat(p.truth()).isZero();
        assertThat(p.focus()).isEqualTo(10);
        assertThat(p.efficiency()).isEqualTo(5);
        assertThat(new AgentPersona(AgentMode.CUSTOM, 0, 0, 0, 0, SearchRange.OFF, ApprovalRange.ALL).efficiency()).isEqualTo(1);
    }

    @Test
    void humourRaisesAndTruthLowersTheTemperature() {
        assertThat(AgentPersona.preset(AgentMode.DISCIPLINED).temperature()).isLessThan(AgentPersona.preset(AgentMode.WORK).temperature());
        assertThat(AgentPersona.preset(AgentMode.FUNNY).temperature()).isGreaterThan(AgentPersona.preset(AgentMode.WORK).temperature());
        assertThat(new AgentPersona(AgentMode.CUSTOM, 10, 0, 5, 3, SearchRange.OFF, ApprovalRange.ALL).temperature()).isEqualTo(1.0);
        assertThat(new AgentPersona(AgentMode.CUSTOM, 0, 10, 5, 3, SearchRange.OFF, ApprovalRange.ALL).temperature()).isEqualTo(0.0);
    }

    @ParameterizedTest
    @CsvSource({"1,Eco,false,2,512", "2,Balanced,false,4,1024", "3,Standard,false,5,1536", "4,High,true,8,2048", "5,Max,true,10,4096"})
    void efficiencyBuysModelStepsAndLength(int efficiency, String label, boolean strong, int steps, int tokens) {
        AgentPersona.Budget b = new AgentPersona(AgentMode.CUSTOM, 5, 5, 5, efficiency, SearchRange.OFF, ApprovalRange.ALL).budget();
        assertThat(b.label()).isEqualTo(label);
        assertThat(b.strongModel()).isEqualTo(strong);
        assertThat(b.maxToolSteps()).isEqualTo(steps);
        assertThat(b.maxTokens()).isEqualTo(tokens);
    }
}
