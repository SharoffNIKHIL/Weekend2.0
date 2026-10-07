package com.weekend.assistant.studio;

/**
 * What the owner asked for. Null fields are still unknown and will be asked for, one question at a time.
 *
 * @param seconds  length of each video
 * @param count    number of videos (a series when > 1)
 * @param voice    narrate the script with a voice
 * @param captions show the words on screen as the video plays
 */
public record VideoBrief(String topic, Integer seconds, Integer count, VideoFormat format, Boolean voice, Boolean captions) {

    public static final int MIN_SECONDS = 10;
    public static final int MAX_SECONDS = 600;
    public static final int MAX_COUNT = 30;

    public static VideoBrief empty() {
        return new VideoBrief(null, null, null, null, null, null);
    }

    /** Fills unknown fields from {@code other}; known fields win over nothing, newer answers win over older ones. */
    public VideoBrief merge(VideoBrief other) {
        return new VideoBrief(
                other.topic != null ? other.topic : topic,
                other.seconds != null ? other.seconds : seconds,
                other.count != null ? other.count : count,
                other.format != null ? other.format : format,
                other.voice != null ? other.voice : voice,
                other.captions != null ? other.captions : captions);
    }

    public VideoFormat formatOrDefault() {
        return format != null ? format : (seconds != null && seconds <= 60 ? VideoFormat.SHORT : VideoFormat.LONG);
    }

    public int countOrOne() {
        return count == null ? 1 : count;
    }

    /** Narration budget at a calm 150 words per minute, minus a little room for the title and the outro. */
    public int wordBudget() {
        return Math.max(15, (int) Math.round((seconds == null ? 45 : seconds) * 2.5 * 0.88));
    }
}
