package com.weekend.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.inbox.InboxService;
import com.weekend.assistant.inbox.NotificationService;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.reminder.ReminderService;
import com.weekend.assistant.workspace.FolderService;
import com.weekend.assistant.workspace.PaymentService;
import com.weekend.assistant.workspace.TaskService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * The UI preview env ({@code local,ui}) on a real port: demo data for every screen, the config agent loaded from a
 * file, and a chat to the loopback demo agent that only travels over HTTP after approval.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"owner-profile-file=src/test/resources/agents/test-profile.md", "owner-brand-dir="})
@ActiveProfiles({"local", "ui"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UiPreviewSeederTest {

    @LocalServerPort int port;
    @Autowired MemoryService memories;
    @Autowired ReminderService reminders;
    @Autowired FolderService folders;
    @Autowired TaskService tasks;
    @Autowired PaymentService payments;
    @Autowired InboxService inbox;
    @Autowired NotificationService notifications;
    @Autowired AgentService agent;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private <T> T call(String method, String path, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(method + " " + path).isBetween(200, 299);
        return res.body().isEmpty() ? null : (T) json.readValue(res.body(), Object.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedsEveryScreenAndLoadsTheOwnerProfile() throws Exception {
        assertThat(memories.all()).hasSize(3);                               // from the profile fixture; secret line skipped
        assertThat(memories.all()).allMatch(m -> m.pinned()).noneMatch(m -> m.text().contains("hunter2"));
        assertThat(memories.all()).extracting(m -> m.text()).doesNotContain("I prefer filter coffee, no sugar"); // no made-up demo memories
        assertThat(folders.summaries()).hasSize(5);
        assertThat(tasks.openCount()).isEqualTo(7);
        assertThat(reminders.upcomingCount()).isEqualTo(5);
        assertThat(payments.pendingApproval()).hasSize(1);
        assertThat(agent.pendingActions()).hasSize(1);
        assertThat(inbox.all()).hasSize(2);

        Map<String, Object> home = call("GET", "/api/home", null);
        Map<String, Object> counts = (Map<String, Object>) home.get("counts");
        assertThat(counts).containsEntry("approvals", 2).containsEntry("tasks", 7).containsEntry("reminders", 5);
        Map<String, Object> me = call("GET", "/api/me", null);
        assertThat(me).containsEntry("name", "Test Owner");
        assertThat((List<String>) me.get("highlights")).containsExactly("The owner is a DevOps engineer.");
        Map<String, Object> features = call("GET", "/api/features", null);
        assertThat((List<?>) features.get("features")).hasSize(11);
    }

    @Test
    @SuppressWarnings("unchecked")
    void imageAnalysisAndArtWorkOverRealHttp() throws Exception {
        String png = "data:image/png;base64," + com.weekend.assistant.TestFixtures.png(6, 3, java.awt.Color.WHITE);
        Map<String, Object> seen = call("POST", "/api/chat", Map.of("message", "what is this?", "featureId", "image", "images", List.of(png)));
        assertThat(seen.get("reply").toString()).contains("PNG 6×3").contains("bright");
        assertThat(seen).containsEntry("featureName", "Image");
        Map<String, Object> art = call("POST", "/api/chat", Map.of("message", "draw a night sky over a lake", "featureId", "drawing"));
        assertThat(art.get("reply").toString()).contains("```svg").contains("viewBox=\"0 0 3840 2160\"");
    }

    @Test
    void refusesToRunWhenSessionsAreRequired(@Autowired Clock clock, @Autowired com.weekend.assistant.owner.OwnerProfile profile) {
        UiPreviewSeeder seeder = new UiPreviewSeeder(memories, reminders, folders, tasks, payments, inbox, notifications,
                agent, TestFixtures.props(), clock, profile);
        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ui profile");
    }
}
