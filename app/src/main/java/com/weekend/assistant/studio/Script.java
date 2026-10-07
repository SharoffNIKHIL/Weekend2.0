package com.weekend.assistant.studio;

import java.util.List;
import java.util.Objects;

/** A video's script: scenes with narration and visuals, plus YouTube metadata and the sources it used. */
public record Script(String title, String description, List<String> tags, List<Scene> scenes, List<Source> sources, boolean draft) {

    public Script {
        Objects.requireNonNull(title, "title");
        description = description == null ? "" : description;
        tags = tags == null ? List.of() : List.copyOf(tags);
        scenes = List.copyOf(scenes);
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("a script needs at least one scene");
        }
    }

    public enum Kind { TITLE, POINTS, IMAGE, OUTRO }

    /** One scene. {@code bullets} are on-screen points; {@code imageQuery} asks the photo library for a matching image. */
    public record Scene(Kind kind, String heading, List<String> bullets, String narration, String imageQuery) {
        public Scene {
            Objects.requireNonNull(kind, "kind");
            heading = heading == null ? "" : heading;
            bullets = bullets == null ? List.of() : List.copyOf(bullets);
            narration = narration == null ? "" : narration.strip();
        }

        public int words() {
            return narration.isBlank() ? 0 : narration.split("\\s+").length;
        }
    }

    public record Source(String title, String url) {}

    public int words() {
        return scenes.stream().mapToInt(Scene::words).sum();
    }
}
