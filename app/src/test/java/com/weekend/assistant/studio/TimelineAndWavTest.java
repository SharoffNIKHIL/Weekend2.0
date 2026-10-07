package com.weekend.assistant.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Scene timing, caption chunks, SRT format and the WAV helpers. */
class TimelineAndWavTest {

    static Script script() {
        return new Script("T", "D", List.of(), List.of(
                new Script.Scene(Script.Kind.TITLE, "Pods", List.of(), "Pods, explained in under a minute.", null),
                new Script.Scene(Script.Kind.POINTS, "What", List.of("Smallest unit"), "A pod wraps one or more containers that share a network and storage.", null),
                new Script.Scene(Script.Kind.OUTRO, "Bye", List.of(), "Follow for part two.", null)), List.of(), false);
    }

    @Test
    void withoutVoiceTheRequestedLengthIsSharedByWords() {
        Timeline t = Timeline.of(script(), null, 45);
        assertThat(t.total()).isCloseTo(45, within(0.01));
        assertThat(t.slots()).hasSize(3);
        assertThat(t.slots().get(1).duration()).isGreaterThan(t.slots().get(2).duration());
        assertThat(t.slotAt(0).scene().kind()).isEqualTo(Script.Kind.TITLE);
        assertThat(t.slotAt(44.9).scene().kind()).isEqualTo(Script.Kind.OUTRO);
        assertThat(t.captions()).allSatisfy(c -> assertThat(c.text().split(" ")).hasSizeLessThanOrEqualTo(6));
        assertThat(t.captionAt(t.captions().get(0).start() + 0.01)).isEqualTo(t.captions().get(0).text());
    }

    @Test
    void withVoiceScenesFollowTheNarration() {
        List<short[]> audio = Arrays.asList(Wav.silence(2.0), Wav.silence(4.0), null);
        Timeline t = Timeline.of(script(), audio, 45);
        assertThat(t.slots().get(0).duration()).isCloseTo(2.0 + Timeline.LEAD + Timeline.TAIL, within(0.01));
        assertThat(t.slots().get(2).duration()).isEqualTo(Timeline.MIN_SCENE);
        assertThat(t.total()).isCloseTo(2.6 + 4.6 + 2.2, within(0.01));
    }

    @Test
    void srtIsWellFormed() {
        String srt = Timeline.of(script(), null, 30).srt();
        assertThat(srt).startsWith("1\n00:00:00,150 --> ");
        assertThat(srt).containsPattern("\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}");
        assertThat(Timeline.stamp(3725.5)).isEqualTo("01:02:05,500");
    }

    @Test
    void wavRoundTripPlaceAndPad() {
        short[] tone = new short[Wav.RATE];
        Arrays.fill(tone, (short) 1000);
        byte[] bytes = Wav.write(tone);
        assertThat(new String(bytes, 0, 4)).isEqualTo("RIFF");
        assertThat(Wav.read(bytes)).containsExactly(tone);
        assertThat(Wav.seconds(tone)).isEqualTo(1.0);

        short[] mixed = Wav.place(List.of(tone), List.of(0.5), 2.0);
        assertThat(mixed).hasSize(2 * Wav.RATE);
        assertThat(mixed[Wav.RATE / 4]).isZero();
        assertThat(mixed[Wav.RATE]).isEqualTo((short) 1000);

        short[] pad = Wav.pad(3.0);
        assertThat(pad).hasSize(3 * Wav.RATE);
        assertThat(Arrays.stream(toInts(pad)).map(Math::abs).max().orElse(0)).isBetween(1000, 32767);
        assertThatThrownBy(() -> Wav.read(new byte[] {1, 2, 3})).isInstanceOf(RuntimeException.class);
    }

    private static int[] toInts(short[] s) {
        int[] out = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            out[i] = s[i];
        }
        return out;
    }
}
