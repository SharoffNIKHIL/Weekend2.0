package com.weekend.assistant.studio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** The owner's own wording, parsed into a brief: topic, length, series size, format, voice and captions. */
class BriefParserTest {

    @Test
    void kubernetesSeriesOfFifteenShorts() {
        assertThat(BriefParser.isVideoRequest("create me a video on the kubernetes")).isTrue();
        VideoBrief first = BriefParser.parse("create me a video on the kubernetes");
        assertThat(first.topic()).isEqualTo("Kubernetes");
        assertThat(first.seconds()).isNull();

        VideoBrief answer = BriefParser.parse("45 sec short videos i want in series of whole kubernetes for the 15 videos");
        assertThat(answer.seconds()).isEqualTo(45);
        assertThat(answer.count()).isEqualTo(15);
        assertThat(answer.format()).isEqualTo(VideoFormat.SHORT);
        VideoBrief merged = first.merge(answer);
        assertThat(merged.topic()).isEqualTo("Kubernetes");
        assertThat(merged.countOrOne()).isEqualTo(15);
        assertThat(merged.formatOrDefault()).isEqualTo(VideoFormat.SHORT);
        assertThat(merged.wordBudget()).isBetween(90, 105);
    }

    @Test
    void arcticFoxTwoMinutes() {
        VideoBrief b = BriefParser.parse("build me a video of 2 min about arctic fox");
        assertThat(b.topic()).isEqualTo("Arctic fox");
        assertThat(b.seconds()).isEqualTo(120);
        assertThat(b.countOrOne()).isEqualTo(1);
        assertThat(b.formatOrDefault()).isEqualTo(VideoFormat.LONG);
        assertThat(b.voice()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "45 sec Short, 45, 1, SHORT",
        "60 sec Short, 60, 1, SHORT",
        "2 min video, 120, 1, LONG",
        "15 × 45 sec Shorts series, 45, 15, SHORT",
        "1:30, 90, 1, LONG",
        "a minute and a half, 90, 1, LONG",
        "make it 3 minutes long, 180, 1, LONG",
        "five 30 second reels, 30, 5, SHORT"})
    void lengthAnswers(String text, int seconds, int count, VideoFormat format) {
        VideoBrief b = BriefParser.parse(text);
        assertThat(b.seconds()).as(text).isEqualTo(seconds);
        assertThat(b.countOrOne()).as(text).isEqualTo(count);
        assertThat(b.formatOrDefault()).as(text).isEqualTo(format);
    }

    @ParameterizedTest
    @CsvSource({
        "Voice + captions, true, true",
        "yes, true, true",
        "yes add the context and voice, true, true",
        "Captions only, false, true",
        "Voice only, true, false",
        "Neither (music only), false, false",
        "no voice but with subtitles, false, true"})
    void voiceAndCaptionAnswers(String text, boolean voice, boolean captions) {
        VideoBrief b = BriefParser.parse(text);
        assertThat(b.voice()).as(text).isEqualTo(voice);
        assertThat(b.captions()).as(text).isEqualTo(captions);
    }

    @Test
    void limitsAndNonRequests() {
        assertThat(BriefParser.parse("10 hours").seconds()).isEqualTo(VideoBrief.MAX_SECONDS);
        assertThat(BriefParser.parse("2 sec").seconds()).isEqualTo(VideoBrief.MIN_SECONDS);
        assertThat(BriefParser.parse("100 videos").count()).isEqualTo(VideoBrief.MAX_COUNT);
        assertThat(BriefParser.isVideoRequest("what is kubernetes?")).isFalse();
        assertThat(BriefParser.isVideoRequest("remind me to watch a video")).isFalse();
        assertThat(BriefParser.isVideoRequest("make 3 shorts about black holes")).isTrue();
    }
}
