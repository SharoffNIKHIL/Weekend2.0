package com.weekend.assistant.studio;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.NotificationKind;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.port.AuditLog;
import com.weekend.assistant.port.StockImageClient;
import com.weekend.assistant.port.TtsClient;
import com.weekend.assistant.retention.OwnerData;
import jakarta.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Weekend Studio: the conversation that turns "make me a video on X" into a brief (asking only what is missing),
 * the plan the owner confirms, and the render queue that produces upload-ready MP4s. Videos stay in a private media
 * folder (P5: purged after weekend.studio.retention; P6: exported as metadata and deleted with everything else).
 */
@Service
public class StudioService implements OwnerData {

    private static final Logger log = LoggerFactory.getLogger(StudioService.class);
    static final Pattern START = Pattern.compile("^\\s*(start|go|yes|yep|ok|okay|sure|render|confirm|do it|make it|start rendering)\\b.*", Pattern.CASE_INSENSITIVE);
    static final Pattern CANCEL = Pattern.compile("^\\s*(cancel|stop|never ?mind|forget it|no thanks)\\b.*", Pattern.CASE_INSENSITIVE);

    enum Stage { TOPIC, LENGTH, VOICE, CONFIRM }

    record Draft(VideoBrief brief, Stage stage, SeriesPlan plan) {}

    private final StudioPlanner planner;
    private final TtsClient tts;
    private final StockImageClient stock;
    private final NotificationService notifications;
    private final AuditLog audit;
    private final WeekendProperties props;
    private final Clock clock;
    private final VideoRenderer renderer;
    private final Path media;
    private final ExecutorService queue;
    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
    private final Map<String, StudioProject> projects = new ConcurrentHashMap<>();
    private final Map<String, VideoJob> jobs = new ConcurrentHashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile Boolean ffmpeg;

    public StudioService(StudioPlanner planner, TtsClient tts, StockImageClient stock, NotificationService notifications, AuditLog audit,
            WeekendProperties props, Clock clock) {
        this.planner = planner;
        this.tts = tts;
        this.stock = stock;
        this.notifications = notifications;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.renderer = new VideoRenderer(props.studio());
        this.media = Path.of(props.studio().mediaDir());
        this.queue = Executors.newFixedThreadPool(props.studio().renderThreads(), r -> {
            Thread t = new Thread(r, "studio-render");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void stop() {
        queue.shutdownNow();
    }

    // ---------- conversation ----------

    /** Handles the message when it is about making a video (or continues one in progress); otherwise empty. */
    public Optional<StudioReply> respond(String conversationId, String text, boolean studioFeature) {
        Draft draft = drafts.get(conversationId);
        boolean request = BriefParser.isVideoRequest(text);
        boolean bareTopic = draft == null && !request && studioFeature && !text.isBlank() && text.length() <= 120 && !text.strip().endsWith("?");
        if (draft == null && !request && !bareTopic) {
            return Optional.empty();
        }
        if (draft != null && CANCEL.matcher(text).matches()) {
            drafts.remove(conversationId);
            return Optional.of(new StudioReply(StudioReply.Kind.INFO, "Cancelled. Nothing was made.", List.of(), List.of(), null));
        }
        if (draft != null && draft.stage() == Stage.CONFIRM && START.matcher(text).matches()) {
            drafts.remove(conversationId);
            StudioProject p = start(conversationId, draft.brief(), draft.plan());
            int n = p.jobIds().size();
            return Optional.of(new StudioReply(StudioReply.Kind.STARTED, n == 1 ? "On it. I'll drop the video here when it's ready."
                    : "On it: " + n + " videos. I'll drop each one here as soon as it's ready.", List.of(), List.of(), p.id()));
        }
        VideoBrief parsed = BriefParser.parse(text);
        if (draft != null && draft.brief().topic() != null && !request) {
            // an answer to a question ("45 sec, series of 15 on the whole topic") never replaces the topic
            parsed = new VideoBrief(null, parsed.seconds(), parsed.count(), parsed.format(), parsed.voice(), parsed.captions());
        }
        VideoBrief brief = (draft == null ? VideoBrief.empty() : draft.brief()).merge(parsed);
        if ((bareTopic || draft != null && draft.stage() == Stage.TOPIC) && brief.topic() == null && !text.isBlank() && text.length() <= 120) {
            brief = brief.merge(new VideoBrief(cap(text.strip()), null, null, null, null, null));
        }
        if (brief.topic() == null) {
            return ask(conversationId, brief, Stage.TOPIC, "What should the video be about?",
                    List.of("Kubernetes", "Arctic fox", "How the internet works", "Indian space missions"));
        }
        if (brief.seconds() == null) {
            return ask(conversationId, brief, Stage.LENGTH, "How long should it be? A single video or a series?",
                    List.of("45 sec Short", "60 sec Short", "2 min video", "15 × 45 sec Shorts series"));
        }
        if (brief.voice() == null || brief.captions() == null) {
            String note = tts.enabled() ? "" : " (No voice service is set up yet, so for now it would be captions and music.)";
            return ask(conversationId, brief, Stage.VOICE, "Do you want a voice reading the script, and the words on screen as the video plays?" + note,
                    List.of("Voice + captions", "Captions only", "Voice only", "Neither (music only)"));
        }
        SeriesPlan plan = planner.plan(brief);
        drafts.put(conversationId, new Draft(brief, Stage.CONFIRM, plan));
        return Optional.of(new StudioReply(StudioReply.Kind.CONFIRM, summary(brief, plan),
                List.of("Start", "Change length", "Cancel"), plan.episodes().stream().map(e -> e.number() + ". " + e.title()).toList(), null));
    }

    private Optional<StudioReply> ask(String conv, VideoBrief brief, Stage stage, String q, List<String> options) {
        drafts.put(conv, new Draft(brief, stage, null));
        return Optional.of(new StudioReply(StudioReply.Kind.ASK, q, options, List.of(), null));
    }

    String summary(VideoBrief b, SeriesPlan plan) {
        int n = b.countOrOne();
        VideoFormat f = b.formatOrDefault();
        String voice = b.voice() ? (tts.enabled() ? tts.label() : "no voice service yet (captions + music)") : "no voice";
        return "Ready to make " + (n == 1 ? "a " + human(b.seconds()) + " " + (f == VideoFormat.SHORT ? "Short" : "video")
                : n + " " + (f == VideoFormat.SHORT ? "Shorts" : "videos") + " of " + human(b.seconds()) + " each") + " on " + b.topic() + ". "
                + "Format " + f.width() + "×" + f.height() + " MP4 · voice: " + voice + " · captions: " + (b.captions() ? "on" : "off") + "."
                + (plan.draft() ? " (Offline draft scripts: connect Claude for researched ones.)" : "")
                + (b.voice() && tts.enabled() && !tts.commercialUse() ? " Draft voice: fine to review, not for monetised uploads." : "")
                + " Start?";
    }

    static String human(Integer s) {
        if (s == null) {
            return "?";
        }
        return s < 60 ? s + " sec" : s % 60 == 0 ? (s / 60) + " min" : (s / 60) + " min " + (s % 60) + " sec";
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- projects and the render queue ----------

    public StudioProject start(String conversationId, VideoBrief brief, SeriesPlan plan) {
        String pid = UUID.randomUUID().toString();
        Instant now = clock.instant();
        List<String> ids = new ArrayList<>();
        for (SeriesPlan.Episode ep : plan.episodes()) {
            VideoJob j = new VideoJob(UUID.randomUUID().toString(), pid, ep.number(), plan.episodes().size(), ep.title(), VideoJob.Status.QUEUED,
                    "Queued", 0, null, null, null, null, false, now, null);
            jobs.put(j.id(), j);
            ids.add(j.id());
        }
        StudioProject p = new StudioProject(pid, conversationId, brief, plan, ids, now);
        projects.put(pid, p);
        audit.append("owner", "studio.start", pid + ":" + ids.size());
        for (int i = 0; i < ids.size(); i++) {
            SeriesPlan.Episode ep = plan.episodes().get(i);
            String id = ids.get(i);
            queue.submit(() -> make(id, p, ep));
        }
        return p;
    }

    void make(String jobId, StudioProject p, SeriesPlan.Episode ep) {
        try {
            update(jobId, j -> j.with(VideoJob.Status.WORKING, "Writing the script", 3));
            Script script = planner.script(p.brief(), p.plan(), ep);
            update(jobId, j -> j.withScript(script).with(VideoJob.Status.WORKING, "Finding visuals", 8));
            boolean landscape = p.brief().formatOrDefault() == VideoFormat.LONG;
            Map<Script.Scene, BufferedImage> images = new HashMap<>();
            Map<Script.Scene, String> credits = new HashMap<>();
            List<String> photoCredits = new ArrayList<>();
            for (Script.Scene s : script.scenes()) {
                if (s.kind() == Script.Kind.IMAGE && s.imageQuery() != null && stock.enabled()) {
                    stock.find(s.imageQuery(), landscape).ifPresent(img -> {
                        try {
                            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(img.bytes()));
                            if (bi != null) {
                                images.put(s, bi);
                                credits.put(s, img.author() + ", " + img.licence());
                                photoCredits.add(img.credit());
                            }
                        } catch (IOException ignored) {
                            // unreadable image: the scene falls back to graphics
                        }
                    });
                }
            }
            boolean voiced = Boolean.TRUE.equals(p.brief().voice()) && tts.enabled();
            List<short[]> audio = new ArrayList<>();
            int i = 0;
            for (Script.Scene s : script.scenes()) {
                int at = i++;
                update(jobId, j -> j.with(VideoJob.Status.WORKING, voiced ? "Recording the voice" : "Timing the scenes",
                        10 + 15 * at / script.scenes().size()));
                audio.add(voiced && !s.narration().isBlank() ? Wav.read(tts.synthesize(s.narration())) : null);
            }
            Timeline timeline = Timeline.of(script, voiced ? audio : null, p.brief().seconds());
            Path dir = media.resolve(p.id()).resolve(jobId);
            String badge = ep.number() > 0 && p.plan().episodes().size() > 1 ? "Part " + ep.number() + "/" + p.plan().episodes().size() : null;
            String next = ep.number() < p.plan().episodes().size() ? p.plan().episodes().get(ep.number()).title() : null;
            VideoRenderer.Output out = renderer.render(timeline, p.brief().formatOrDefault(), Boolean.TRUE.equals(p.brief().captions()), badge, next,
                    images, credits, dir, pct -> update(jobId, j -> j.with(VideoJob.Status.WORKING, "Rendering", 25 + pct * 74 / 100)));
            Files.writeString(dir.resolve("metadata.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(
                    metadata(script, p, ep, timeline, photoCredits, voiced)));
            Instant at = clock.instant();
            update(jobId, j -> j.ready(out.seconds(), voiced ? tts.label() : null, voiced && !tts.commercialUse(), at));
            audit.append("system", "studio.ready", jobId);
            notifications.notify(NotificationKind.SYSTEM, "Video ready", script.title(), "#library");
        } catch (RuntimeException | IOException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("studio job {} failed: {}", jobId, msg);
            update(jobId, j -> j.failed(msg.length() > 240 ? msg.substring(0, 237) + "..." : msg));
            notifications.notify(NotificationKind.SYSTEM, "Video failed", ep.title(), "#library");
        }
    }

    Map<String, Object> metadata(Script s, StudioProject p, SeriesPlan.Episode ep, Timeline t, List<String> photos, boolean voiced) {
        StringBuilder d = new StringBuilder(s.description());
        boolean shorts = p.brief().formatOrDefault() == VideoFormat.SHORT;
        if (!shorts && t.slots().size() > 2) {
            d.append("\n\nChapters:\n");
            for (Timeline.Slot sl : t.slots()) {
                String h = sl.scene().heading().isBlank() ? sl.scene().kind().name() : sl.scene().heading();
                d.append(String.format("%d:%02d %s%n", (int) sl.start() / 60, (int) sl.start() % 60, h));
            }
        }
        List<Script.Source> sources = Stream.concat(p.plan().sources().stream(), s.sources().stream()).distinct().toList();
        if (!sources.isEmpty()) {
            d.append("\n\nSources:\n");
            sources.forEach(src -> d.append("- ").append(src.title()).append(": ").append(src.url()).append('\n'));
        }
        if (!photos.isEmpty()) {
            d.append("\n\nPhotos:\n");
            photos.forEach(c -> d.append("- ").append(c).append('\n'));
        }
        if (shorts) {
            d.append("\n\n#Shorts");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", s.title());
        m.put("description", d.toString().strip());
        m.put("tags", s.tags());
        m.put("part", ep.number() + "/" + p.plan().episodes().size());
        m.put("format", p.brief().formatOrDefault().name());
        m.put("seconds", Math.round(t.total() * 10) / 10.0);
        m.put("voice", voiced ? tts.label() : "none");
        m.put("draftVoice", voiced && !tts.commercialUse());
        m.put("draftScript", s.draft());
        m.put("narration", s.scenes().stream().map(Script.Scene::narration).filter(x -> !x.isBlank()).toList());
        return m;
    }

    private void update(String jobId, java.util.function.UnaryOperator<VideoJob> f) {
        jobs.computeIfPresent(jobId, (k, v) -> f.apply(v));
    }

    public List<StudioProject> projects() {
        return projects.values().stream().sorted(Comparator.comparing(StudioProject::createdAt).reversed()).toList();
    }

    public Optional<StudioProject> project(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    public List<VideoJob> jobs(StudioProject p) {
        return p.jobIds().stream().map(jobs::get).filter(java.util.Objects::nonNull).toList();
    }

    public Optional<VideoJob> job(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /** The file for a job (video.mp4, captions.srt, thumbnail.jpg, metadata.json), only when it exists. */
    public Optional<Path> file(String jobId, String name) {
        if (!List.of("video.mp4", "captions.srt", "thumbnail.jpg", "metadata.json").contains(name)) {
            return Optional.empty();
        }
        return job(jobId).filter(j -> j.status() == VideoJob.Status.READY)
                .map(j -> media.resolve(j.projectId()).resolve(j.id()).resolve(name)).filter(Files::isRegularFile);
    }

    public boolean deleteProject(String id) {
        StudioProject p = projects.remove(id);
        if (p == null) {
            return false;
        }
        p.jobIds().forEach(jobs::remove);
        wipe(media.resolve(p.id()));
        audit.append("owner", "studio.delete", id);
        return true;
    }

    /** Status of the studio's plugins and connectors, for the feature page. */
    public record Health(boolean render, boolean voice, String voiceLabel, boolean voiceCommercial, boolean photos, boolean research) {}

    public Health health() {
        if (ffmpeg == null) {
            ffmpeg = VideoRenderer.ffmpegAvailable(props.studio().ffmpeg());
        }
        boolean research = props.studio().webSearches() > 0 && "vertex".equals(props.llm().provider());
        return new Health(ffmpeg, tts.enabled(), tts.label(), tts.commercialUse(), stock.enabled(), research);
    }

    // ---------- owner data (P5, P6) ----------

    @Override
    public String name() {
        return "studio";
    }

    @Override
    public Object export() {
        return projects().stream().map(p -> Map.of("id", p.id(), "topic", String.valueOf(p.brief().topic()), "createdAt", p.createdAt().toString(),
                "videos", jobs(p).stream().map(j -> Map.of("title", j.title(), "status", j.status().name(), "seconds", j.seconds() == null ? 0 : j.seconds()))
                        .toList())).toList();
    }

    @Override
    public int purgeExpired(Instant now) {
        Instant cutoff = now.minus(props.studio().retention());
        List<String> old = projects.values().stream().filter(p -> p.createdAt().isBefore(cutoff)).map(StudioProject::id).toList();
        old.forEach(this::deleteProject);
        return old.size();
    }

    @Override
    public void deleteAll() {
        List.copyOf(projects.keySet()).forEach(this::deleteProject);
        drafts.clear();
    }

    private static void wipe(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
