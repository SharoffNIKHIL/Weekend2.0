package com.weekend.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.agent.AgentService;
import com.weekend.assistant.agents.AgentDirectory;
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
        properties = "project-agent-file=src/test/resources/agents/test-instructions.md")
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
    @Autowired AgentDirectory agents;
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
    void seedsEveryScreenAndLoadsTheConfigAgent() throws Exception {
        assertThat(memories.all()).hasSize(5);
        assertThat(folders.summaries()).hasSize(5);
        assertThat(tasks.openCount()).isEqualTo(7);
        assertThat(reminders.upcomingCount()).isEqualTo(5);
        assertThat(payments.pendingApproval()).hasSize(1);
        assertThat(agent.pendingActions()).hasSize(1);
        assertThat(inbox.all()).hasSize(2);
        assertThat(agents.find("project-agent")).get().satisfies(a -> {
            assertThat(a.instructions()).startsWith("# TEST AGENT");
            assertThat(a.conditions().confirmAllTools()).isTrue();
        });

        Map<String, Object> home = call("GET", "/api/home", null);
        Map<String, Object> counts = (Map<String, Object>) home.get("counts");
        assertThat(counts).containsEntry("approvals", 2).containsEntry("tasks", 7).containsEntry("reminders", 5);
        assertThat((List<?>) home.get("folders")).hasSize(5);
        assertThat((List<?>) home.get("upNext")).hasSize(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatToTheDemoAgentTravelsOverLoopbackOnlyAfterApproval() throws Exception {
        String demo = agents.all().stream().filter(a -> a.name().equals("Demo agent")).findFirst().orElseThrow().id();
        Map<String, Object> health = call("POST", "/api/agents/" + demo + "/test", null);
        assertThat(health).containsEntry("healthy", true);

        int before = inbox.all().size();
        Map<String, Object> chat = call("POST", "/api/chat", Map.of("message", "ping from the test", "agentId", demo));
        Map<String, Object> pending = (Map<String, Object>) chat.get("pendingConfirmation");
        assertThat(pending.get("summary").toString()).contains("127.0.0.1");
        assertThat(inbox.all()).hasSize(before);                         // nothing sent yet

        Map<String, Object> result = call("POST", "/api/approvals/tool/" + pending.get("id"), Map.of("approved", true));
        assertThat(result.get("result").toString()).contains("Demo agent here").contains("ping from the test");
        assertThat(inbox.all()).hasSize(before + 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void connectedAgentsCanMessageWeekendWithTheirOneTimeTokenOnly() throws Exception {
        Map<String, Object> c = call("POST", "/api/agents/remote",
                Map.of("name", "Inbound tester", "endpoint", "http://127.0.0.1:" + port + "/demo-agent"));
        String token = (String) c.get("inboundToken");
        String agentId = (String) ((Map<String, Object>) c.get("agent")).get("id");
        assertThat(c.toString()).doesNotContain("inboundTokenHash");

        HttpRequest.Builder inboxReq = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/agent-inbox"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token);
        HttpResponse<String> ok = http.send(inboxReq.POST(HttpRequest.BodyPublishers.ofString(
                "{\"subject\":\"Build done\",\"body\":\"All green. password: hunter2\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(ok.statusCode()).isEqualTo(202);
        assertThat(inbox.all()).anySatisfy(m -> {
            assertThat(m.fromAgentId()).isEqualTo(agentId);
            assertThat(m.body()).contains("[REDACTED]").doesNotContain("hunter2");
        });
        HttpResponse<String> empty = http.send(inboxReq.POST(HttpRequest.BodyPublishers.ofString("{\"subject\":\"x\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(empty.statusCode()).isEqualTo(400);

        int last = 0;
        for (int i = 0; i < 60; i++) {
            last = http.send(inboxReq.POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"n\"}")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        assertThat(last).isEqualTo(429);                                 // 60 per hour per agent

        call("DELETE", "/api/agents/" + agentId, null);
        HttpResponse<String> revoked = http.send(inboxReq.POST(HttpRequest.BodyPublishers.ofString("{\"body\":\"n\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(revoked.statusCode()).isEqualTo(401);
    }

    @Test
    void refusesToRunWhenSessionsAreRequired(@Autowired Clock clock, @Autowired org.springframework.core.env.Environment env) {
        UiPreviewSeeder seeder = new UiPreviewSeeder(memories, reminders, folders, tasks, payments, inbox, notifications,
                agents, agent, TestFixtures.props(), clock, env);
        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("ui profile");
    }
}
