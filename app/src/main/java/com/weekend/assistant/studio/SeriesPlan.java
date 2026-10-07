package com.weekend.assistant.studio;

import java.util.List;

/** The outline of one video or a series: episode titles and what each covers, in viewing order. */
public record SeriesPlan(String topic, List<Episode> episodes, List<Script.Source> sources, boolean draft) {

    public SeriesPlan {
        episodes = List.copyOf(episodes);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record Episode(int number, String title, String focus) {}
}
