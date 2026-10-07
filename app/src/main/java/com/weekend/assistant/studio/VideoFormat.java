package com.weekend.assistant.studio;

/** SHORT = vertical 9:16 for YouTube Shorts; LONG = landscape 16:9. */
public enum VideoFormat {
    SHORT(1080, 1920),
    LONG(1920, 1080);

    private final int width;
    private final int height;

    VideoFormat(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
