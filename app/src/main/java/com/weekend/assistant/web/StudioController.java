package com.weekend.assistant.web;

import com.weekend.assistant.studio.MediaSigner;
import com.weekend.assistant.studio.StudioProject;
import com.weekend.assistant.studio.StudioService;
import com.weekend.assistant.studio.VideoJob;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Weekend Studio library: projects, live job progress and signed links to the finished files. */
@RestController
@RequestMapping("/api/studio")
public class StudioController {

    private final StudioService studio;
    private final MediaSigner signer;
    private final JsonMapper json = JsonMapper.builder().build();

    public StudioController(StudioService studio, MediaSigner signer) {
        this.studio = studio;
        this.signer = signer;
    }

    public record Files_(String video, String captions, String thumbnail, String metadata) {}

    public record JobView(String id, int number, int total, String title, String status, String stage, int progress, String error,
            Double seconds, String voice, boolean draftVoice, boolean draftScript, Files_ files, Map<String, Object> metadata,
            Instant createdAt, Instant readyAt) {}

    public record ProjectView(String id, String conversationId, String topic, Integer seconds, int count, String format, boolean voice,
            boolean captions, boolean draft, List<JobView> jobs, Instant createdAt) {}

    @GetMapping("/status")
    public StudioService.Health status() {
        return studio.health();
    }

    @GetMapping("/projects")
    public List<ProjectView> projects() {
        return studio.projects().stream().map(this::view).toList();
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<ProjectView> project(@PathVariable String id) {
        return ResponseEntity.of(studio.project(id).map(this::view));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return studio.deleteProject(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private ProjectView view(StudioProject p) {
        return new ProjectView(p.id(), p.conversationId(), p.brief().topic(), p.brief().seconds(), p.brief().countOrOne(),
                p.brief().formatOrDefault().name(), Boolean.TRUE.equals(p.brief().voice()), Boolean.TRUE.equals(p.brief().captions()),
                p.plan().draft(), studio.jobs(p).stream().map(this::job).toList(), p.createdAt());
    }

    @SuppressWarnings("unchecked")
    private JobView job(VideoJob j) {
        Files_ files = null;
        Map<String, Object> meta = null;
        if (j.status() == VideoJob.Status.READY) {
            files = new Files_(signer.url(j.id(), "video.mp4"), signer.url(j.id(), "captions.srt"), signer.url(j.id(), "thumbnail.jpg"),
                    signer.url(j.id(), "metadata.json"));
            meta = studio.file(j.id(), "metadata.json").map(f -> {
                try {
                    return (Map<String, Object>) json.readValue(Files.readString(f), Map.class);
                } catch (Exception e) {
                    return null;
                }
            }).orElse(null);
        }
        return new JobView(j.id(), j.number(), j.total(), j.title(), j.status().name(), j.stage(), j.progress(), j.error(), j.seconds(),
                j.voice(), j.draftVoice(), j.script() != null && j.script().draft(), files, meta, j.createdAt(), j.readyAt());
    }
}
