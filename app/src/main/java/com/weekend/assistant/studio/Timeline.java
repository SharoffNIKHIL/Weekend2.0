package com.weekend.assistant.studio;

import java.util.ArrayList;
import java.util.List;

/**
 * When each scene plays and which caption shows when. With a voice, scenes last as long as their narration (plus a
 * short breath); without one, the requested length is shared out by word count.
 */
public record Timeline(List<Slot> slots, List<Caption> captions, double total) {

    static final double LEAD = 0.25;
    static final double TAIL = 0.35;
    static final double MIN_SCENE = 2.2;

    public record Slot(Script.Scene scene, double start, double duration, short[] audio) {
        public double end() {
            return start + duration;
        }
    }

    public record Caption(double start, double end, String text) {}

    /** {@code audio.get(i)} is scene i's narration (null when there is none). */
    public static Timeline of(Script script, List<short[]> audio, int targetSeconds) {
        List<Script.Scene> scenes = script.scenes();
        boolean voiced = audio != null && audio.stream().anyMatch(a -> a != null && a.length > 0);
        double[] dur = new double[scenes.size()];
        if (voiced) {
            for (int i = 0; i < scenes.size(); i++) {
                short[] a = audio.get(i);
                dur[i] = Math.max(MIN_SCENE, (a == null ? 0 : Wav.seconds(a)) + LEAD + TAIL);
            }
        } else {
            int words = Math.max(1, script.words());
            double remaining = targetSeconds - MIN_SCENE * scenes.size();
            for (int i = 0; i < scenes.size(); i++) {
                dur[i] = MIN_SCENE + Math.max(0, remaining) * scenes.get(i).words() / words;
            }
        }
        List<Slot> slots = new ArrayList<>();
        List<Caption> caps = new ArrayList<>();
        double t = 0;
        for (int i = 0; i < scenes.size(); i++) {
            Script.Scene s = scenes.get(i);
            short[] a = voiced ? audio.get(i) : null;
            slots.add(new Slot(s, t, dur[i], a));
            double speechStart = t + (voiced ? LEAD : 0.15);
            double speechLen = voiced && a != null ? Wav.seconds(a) : dur[i] - 0.3;
            caps.addAll(chunks(s.narration(), speechStart, speechLen));
            t += dur[i];
        }
        return new Timeline(List.copyOf(slots), List.copyOf(caps), t);
    }

    /** Splits narration into short caption lines (about 6 words, breaking after punctuation), timed by length. */
    static List<Caption> chunks(String narration, double start, double length) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int n = 0;
        for (String w : narration.strip().split("\\s+")) {
            if (w.isBlank()) {
                continue;
            }
            cur.append(cur.isEmpty() ? "" : " ").append(w);
            n++;
            if (n >= 6 || (n >= 3 && w.matches(".*[.,;:!?]$"))) {
                parts.add(cur.toString());
                cur.setLength(0);
                n = 0;
            }
        }
        if (!cur.isEmpty()) {
            parts.add(cur.toString());
        }
        int chars = Math.max(1, parts.stream().mapToInt(String::length).sum());
        List<Caption> out = new ArrayList<>();
        double t = start;
        for (String p : parts) {
            double d = length * p.length() / chars;
            out.add(new Caption(t, t + d, p));
            t += d;
        }
        return out;
    }

    public Slot slotAt(double t) {
        for (Slot s : slots) {
            if (t < s.end()) {
                return s;
            }
        }
        return slots.get(slots.size() - 1);
    }

    public String captionAt(double t) {
        for (Caption c : captions) {
            if (t >= c.start() && t < c.end()) {
                return c.text();
            }
        }
        return null;
    }

    /** SubRip captions (YouTube accepts .srt uploads). */
    public String srt() {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Caption c : captions) {
            sb.append(i++).append('\n').append(stamp(c.start())).append(" --> ").append(stamp(c.end())).append('\n').append(c.text()).append("\n\n");
        }
        return sb.toString();
    }

    static String stamp(double s) {
        long ms = Math.round(s * 1000);
        return String.format("%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000);
    }
}
