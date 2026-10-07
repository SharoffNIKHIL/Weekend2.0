package com.weekend.assistant.studio;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands how the owner asks for videos, in plain English: "create me a video on kubernetes",
 * "45 sec short videos, a series of 15", "build me a video of 2 min about arctic fox", "yes, with voice and captions".
 * Pure and deterministic, so it is fast, testable and works offline; Claude refines the plan later.
 */
public final class BriefParser {

    private static final Map<String, Integer> WORDS = Map.ofEntries(Map.entry("one", 1), Map.entry("a", 1), Map.entry("an", 1),
            Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
            Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11),
            Map.entry("twelve", 12), Map.entry("fifteen", 15), Map.entry("twenty", 20), Map.entry("thirty", 30), Map.entry("half", 0));

    static final Pattern INTENT = Pattern.compile(
            "\\b(create|make|build|generate|produce|craft|render|do)\\b.{0,40}?\\b(video|videos|short|shorts|reel|reels|series|clip|clips)\\b");
    private static final Pattern TOPIC = Pattern.compile("\\b(?:on|about|explaining|covering|regarding)\\s+(?:the\\s+(?:topic|subject)\\s+of\\s+)?(.+)$");
    private static final Pattern TOPIC_OF = Pattern.compile("\\b(?:of)\\s+(?:the\\s+(?:topic|subject)\\s+of\\s+)?(.+)$");
    private static final Pattern HOURS = Pattern.compile("\\b(\\d+)\\s*(?:h|hr|hrs|hour|hours)\\b");
    private static final Pattern AND_HALF = Pattern.compile("\\b(a|an|one|two|three|four|five|ten|\\d+)\\s+(?:min|minute)s?\\s+and\\s+a\\s+half\\b");
    private static final Pattern MIN_SEC = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:m|min|mins|minute|minutes)\\b(?:\\s*(?:and\\s*)?(\\d+)\\s*(?:s|sec|secs|second|seconds)\\b)?");
    private static final Pattern SEC = Pattern.compile("(\\d+)\\s*(?:s|sec|secs|second|seconds)\\b");
    private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}):([0-5]\\d)\\b");
    private static final Pattern WORD_MIN = Pattern.compile("\\b(a|an|one|two|three|four|five|ten|half)\\s*(?:a\\s+)?(?:min|minute)s?\\b");
    private static final Pattern COUNT = Pattern.compile("\\b(\\d+\\b|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty)\\s*(?:x\\s*)?"
            + "(?:\\d+\\s*(?:s|sec|secs|seconds?)\\s*)?(?:short\\s+)?(?:videos|shorts|parts|episodes|clips|reels)\\b");
    private static final Pattern SERIES_OF = Pattern.compile("\\bseries\\s+(?:of\\s+)?(\\d+|two|three|four|five|six|seven|eight|nine|ten|twelve|fifteen|twenty)\\b");
    private static final Pattern TIMES = Pattern.compile("\\b(\\d+)\\s*[x×]\\s*\\d+\\s*(?:s|sec|secs|seconds?|m|min|mins|minutes?)\\b");

    private BriefParser() {}

    public static boolean isVideoRequest(String text) {
        return text != null && INTENT.matcher(text.toLowerCase(Locale.ROOT)).find();
    }

    /** Everything the text says about a video; unknown parts stay null. */
    public static VideoBrief parse(String text) {
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('×', 'x').strip();
        return new VideoBrief(topic(text), seconds(t), count(t), format(t), voice(t), captions(t));
    }

    static Integer seconds(String t) {
        Matcher clock = CLOCK.matcher(t);
        if (clock.find()) {
            return clamp(Integer.parseInt(clock.group(1)) * 60 + Integer.parseInt(clock.group(2)));
        }
        Matcher half = AND_HALF.matcher(t);
        if (half.find()) {
            String n = half.group(1);
            int v = n.chars().allMatch(Character::isDigit) ? Integer.parseInt(n) : WORDS.getOrDefault(n, 1);
            return clamp(v * 60 + 30);
        }
        Matcher hr = HOURS.matcher(t);
        if (hr.find()) {
            return clamp(Integer.parseInt(hr.group(1)) * 3600);
        }
        Matcher m = MIN_SEC.matcher(t);
        if (m.find()) {
            double min = Double.parseDouble(m.group(1));
            int extra = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            return clamp((int) Math.round(min * 60) + extra);
        }
        Matcher w = WORD_MIN.matcher(t);
        if (w.find()) {
            int v = WORDS.getOrDefault(w.group(1), 1);
            return clamp(v == 0 ? 30 : v * 60);
        }
        Matcher s = SEC.matcher(t);
        return s.find() ? clamp(Integer.parseInt(s.group(1))) : null;
    }

    static Integer count(String t) {
        for (Pattern p : new Pattern[] {TIMES, SERIES_OF, COUNT}) {
            Matcher m = p.matcher(t);
            if (m.find()) {
                String n = m.group(1);
                int v = n.chars().allMatch(Character::isDigit) ? Integer.parseInt(n) : WORDS.getOrDefault(n, 1);
                return Math.max(1, Math.min(VideoBrief.MAX_COUNT, v));
            }
        }
        if (Pattern.compile("\\b(a|one|single)\\s+(video|short|clip|reel)\\b").matcher(t).find()) {
            return 1;
        }
        return null;
    }

    static VideoFormat format(String t) {
        if (t.matches(".*\\b(shorts?|reels?|vertical|9:16|portrait)\\b.*")) {
            return VideoFormat.SHORT;
        }
        if (t.matches(".*\\b(long|landscape|16:9|horizontal|full video|youtube video)\\b.*")) {
            return VideoFormat.LONG;
        }
        return null;
    }

    private static final Pattern VOICE_NO = Pattern.compile("\\b(no voice|without (?:a )?voice|silent|no narration|without narration|captions only|"
            + "subtitles only|text only|music only|neither|no voice-?over)\\b");
    private static final Pattern VOICE_YES = Pattern.compile("\\b(voice|narrat\\w*|voice-?over|read(?:ing)? (?:it|the words|aloud)|speak\\w*)\\b");
    private static final Pattern CAPTIONS_NO = Pattern.compile("\\b(no captions|no subtitles|without captions|without subtitles|voice only|"
            + "no text|neither|music only)\\b");
    private static final Pattern CAPTIONS_YES = Pattern.compile("\\b(captions?|subtitles?|context|text on screen|on-screen text|words on screen|text only)\\b");
    private static final Pattern YES = Pattern.compile("^\\s*(yes|yeah|yep|yup|sure|ok|okay|both|please do|of course)\\b");
    private static final Pattern NO = Pattern.compile("^\\s*(no|nope|nah)\\b");

    /** "yes", "with voice", "voice + captions" → true; "no", "no voice", "captions only", "neither" → false; else unknown. */
    static Boolean voice(String t) {
        if (VOICE_NO.matcher(t).find() || (NO.matcher(t).find() && !t.matches(".*\\b(captions?|subtitles?)\\b.*"))) {
            return Boolean.FALSE;
        }
        return VOICE_YES.matcher(t).find() || YES.matcher(t).find() ? Boolean.TRUE : null;
    }

    /** "context", "captions", "yes" → true; "no", "no captions", "voice only", "neither" → false; else unknown. */
    static Boolean captions(String t) {
        if (CAPTIONS_NO.matcher(t).find() || (NO.matcher(t).find() && !t.matches(".*\\b(voice|narrat\\w*)\\b.*"))) {
            return Boolean.FALSE;
        }
        return CAPTIONS_YES.matcher(t).find() || YES.matcher(t).find() ? Boolean.TRUE : null;
    }

    static String topic(String text) {
        if (text == null) {
            return null;
        }
        String s = text.strip().replaceAll("[.!?]+$", "");
        Matcher m = TOPIC.matcher(s.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            m = TOPIC_OF.matcher(s.toLowerCase(Locale.ROOT));
            if (!m.find()) {
                return null;
            }
        }
        String raw = s.substring(m.start(1));
        // cut trailing details: ", 45 sec", "in 15 parts", "with voice", "for 2 min"
        raw = raw.replaceAll("(?i)[,;]\\s.*$", "")
                .replaceAll("(?i)\\s+(?:in|as|for|with|of|at|and|that is|which is)\\s+(?:the\\s+)?(?:\\d|a\\s|an\\s|one|two|three|five|ten|fifteen|twenty|voice|captions|subtitles|series|short|long|half).*$", "")
                .replaceAll("(?i)\\b(?:videos?|shorts?|series|clips?)\\b\\s*$", "")
                .replaceAll("(?i)^(?:the\\s+)?(?:whole|entire|full)\\s+", "")
                .replaceAll("(?i)^the\\s+(?=\\S+$)", "")
                .strip();
        if (raw.isBlank() || raw.length() > 120 || raw.matches("(?i)\\d+\\s*(?:s|sec|secs|seconds?|m|min|mins|minutes?)")) {
            return null;
        }
        return Optional.of(raw).map(BriefParser::capitalise).orElse(null);
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Integer clamp(int s) {
        return Math.max(VideoBrief.MIN_SECONDS, Math.min(VideoBrief.MAX_SECONDS, s));
    }
}
