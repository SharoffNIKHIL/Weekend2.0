package com.weekend.assistant.web;

import com.weekend.assistant.studio.MediaSigner;
import com.weekend.assistant.studio.StudioService;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves rendered files through short-lived signed links (no session token in URLs). Only the four known file names are
 * served; byte ranges are supported so the player can seek. {@code ?download=1} saves the file instead of playing it.
 */
@RestController
public class MediaController {

    private static final Map<String, MediaType> TYPES = Map.of(
            "video.mp4", MediaType.parseMediaType("video/mp4"),
            "captions.srt", MediaType.parseMediaType("application/x-subrip"),
            "thumbnail.jpg", MediaType.IMAGE_JPEG,
            "metadata.json", MediaType.APPLICATION_JSON);

    private final StudioService studio;
    private final MediaSigner signer;

    public MediaController(StudioService studio, MediaSigner signer) {
        this.studio = studio;
        this.signer = signer;
    }

    @GetMapping("/media/{jobId}/{file}")
    public ResponseEntity<Resource> file(@PathVariable String jobId, @PathVariable String file, @RequestParam(defaultValue = "0") long exp,
            @RequestParam(required = false) String sig, @RequestParam(defaultValue = "0") int download) {
        if (!TYPES.containsKey(file) || !signer.valid(jobId, file, exp, sig)) {
            return ResponseEntity.status(403).build();
        }
        return studio.file(jobId, file).<ResponseEntity<Resource>>map(path -> {
            ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(TYPES.get(file))
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff");
            if (download == 1) {
                String name = studio.job(jobId).map(j -> slug(j.title()) + ext(file)).orElse(file);
                b.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString());
            }
            return b.body(new FileSystemResource(path));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    static String slug(String title) {
        String s = title.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "video" : s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String ext(String file) {
        return file.substring(file.indexOf('.'));
    }
}
