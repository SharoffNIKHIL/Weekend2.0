package com.weekend.assistant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.weekend.assistant.security.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The new workspace and agent APIs over HTTP, with owner sessions required (production setting). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkspaceApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SessionTokenService tokens;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + tokens.issue()).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(MockHttpServletRequestBuilder req, int expected) throws Exception {
        return mvc.perform(as(req)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/home", "/api/folders", "/api/tasks", "/api/payments", "/api/approvals", "/api/messages",
            "/api/notifications", "/api/agents"})
    void everyNewEndpointNeedsAnOwnerSession(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(as(get(path))).andExpect(status().isOk());
    }

    @Test
    void foldersTasksAndRemindersFlow() throws Exception {
        String folder = JsonPath.read(body(post("/api/folders").content("{\"name\":\"Work\",\"icon\":\"work\"}"), 201), "$.id");
        mvc.perform(as(post("/api/folders").content("{\"name\":\"Bad\",\"icon\":\"rocket\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("icon")));
        mvc.perform(as(get("/api/folders/icons"))).andExpect(jsonPath("$.icons.length()").value(10));

        String task = JsonPath.read(body(post("/api/tasks").content(
                "{\"title\":\"Review plan\",\"folderId\":\"" + folder + "\",\"dueLocal\":\"2099-01-01T09:00\",\"priority\":\"HIGH\"}"), 201), "$.id");
        mvc.perform(as(post("/api/tasks").content("{\"title\":\"x\",\"dueLocal\":\"tomorrow\"}"))).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/tasks").content("{\"title\":\"\"}"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("title is required"));
        mvc.perform(as(post("/api/tasks/" + task + "/complete"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(as(post("/api/tasks/" + task + "/complete"))).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/tasks/" + task + "/reopen"))).andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(as(get("/api/tasks").param("folderId", folder))).andExpect(jsonPath("$.length()").value(1));

        String rem = JsonPath.read(body(post("/api/reminders").content(
                "{\"text\":\"Pay bill\",\"dueLocal\":\"2099-01-01T09:00\",\"folderId\":\"" + folder + "\"}"), 201), "$.id");
        mvc.perform(as(post("/api/reminders").content("{\"text\":\"Past\",\"dueLocal\":\"2000-01-01T09:00\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("the reminder time must be in the future"));
        mvc.perform(as(get("/api/folders/" + folder))).andExpect(jsonPath("$.tasks.length()").value(1))
                .andExpect(jsonPath("$.reminders.length()").value(1));
        mvc.perform(as(get("/api/folders"))).andExpect(jsonPath("$[0].openTasks").value(1)).andExpect(jsonPath("$[0].upcomingReminders").value(1));

        mvc.perform(as(put("/api/folders/" + folder).content("{\"name\":\"Office\"}"))).andExpect(jsonPath("$.name").value("Office"));
        mvc.perform(as(delete("/api/folders/" + folder))).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/folders/" + folder))).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/reminders/" + rem + "/folder").content("{\"folderId\":null}"))).andExpect(jsonPath("$.folderId").isEmpty());
        mvc.perform(as(delete("/api/tasks/" + task))).andExpect(status().isNoContent());
    }

    @Test
    void paymentsGoThroughApprovalsAndAreNeverPaidByWeekend() throws Exception {
        String pay = JsonPath.read(body(post("/api/payments").content(
                "{\"payee\":\"Rent\",\"amountInr\":25000,\"dueDate\":\"2099-01-05\",\"note\":\"Oct\"}"), 201), "$.id");
        mvc.perform(as(post("/api/payments").content("{\"payee\":\"X\",\"amountInr\":-1}"))).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/payments").content("{\"payee\":\"X\",\"amountInr\":10,\"note\":\"4111 1111 1111 1111\"}")))
                .andExpect(status().isBadRequest());

        mvc.perform(as(get("/api/approvals"))).andExpect(jsonPath("$[0].type").value("PAYMENT"));
        mvc.perform(as(get("/api/notifications"))).andExpect(jsonPath("$[0].kind").value("APPROVAL"));
        mvc.perform(as(post("/api/payments/" + pay + "/paid"))).andExpect(status().isNotFound());       // not approved yet
        mvc.perform(as(post("/api/approvals/payment/" + pay).content("{\"approved\":true}")))
                .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.containsString("does not pay")));
        mvc.perform(as(post("/api/approvals/payment/" + pay).content("{\"approved\":true}"))).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/approvals/bogus/" + pay).content("{\"approved\":true}"))).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/payments/" + pay + "/paid"))).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(as(get("/api/home"))).andExpect(jsonPath("$.counts.payments").value(0)).andExpect(jsonPath("$.counts.approvals").value(0));
    }

    @Test
    void toolApprovalsFromChatAndNotifications() throws Exception {
        String pending = JsonPath.read(body(post("/api/chat").content("{\"message\":\"add task call mom\"}"), 200), "$.pendingConfirmation.id");
        mvc.perform(as(get("/api/approvals"))).andExpect(jsonPath("$[0].type").value("TOOL")).andExpect(jsonPath("$[0].title").value("Add a task"));
        mvc.perform(as(post("/api/approvals/tool/" + pending).content("{\"approved\":true}"))).andExpect(status().isOk());
        mvc.perform(as(get("/api/tasks"))).andExpect(jsonPath("$[0].title").value("call mom"));

        mvc.perform(as(get("/api/home"))).andExpect(jsonPath("$.counts.notifications").value(1));
        mvc.perform(as(post("/api/notifications/read-all"))).andExpect(jsonPath("$.marked").value(1));
        mvc.perform(as(get("/api/home"))).andExpect(jsonPath("$.counts.notifications").value(0));
    }

    @Test
    void agentsCustomConditionsAndRemoteGuard() throws Exception {
        mvc.perform(as(get("/api/agents"))).andExpect(jsonPath("$.agents[0].id").value("weekend"))
                .andExpect(jsonPath("$.remoteAllowed").value(false)).andExpect(jsonPath("$.tools.length()").value(7));

        String id = JsonPath.read(body(post("/api/agents/custom").content(
                "{\"name\":\"Planner\",\"instructions\":\"# Planner\\nPlan my week.\",\"conditions\":{\"allowedTools\":[\"task_list\"],\"confirmAllTools\":true,\"thinkHarder\":false,\"maxToolSteps\":3}}"), 201), "$.id");
        String list = body(get("/api/agents"), 200);
        assertThat(list).doesNotContain("Plan my week");                       // text only via /instructions
        mvc.perform(as(get("/api/agents/" + id + "/instructions"))).andExpect(jsonPath("$.text").value("# Planner\nPlan my week."));
        mvc.perform(as(put("/api/agents/" + id + "/conditions").content("{\"allowedTools\":null,\"confirmAllTools\":false,\"thinkHarder\":true,\"maxToolSteps\":2}")))
                .andExpect(jsonPath("$.conditions.thinkHarder").value(true)).andExpect(jsonPath("$.instructionsTitle").value("Planner"));

        mvc.perform(as(post("/api/chat").content("{\"message\":\"hi\",\"agentId\":\"" + id + "\"}"))).andExpect(jsonPath("$.agentName").value("Planner"));
        mvc.perform(as(post("/api/chat").content("{\"message\":\"hi\",\"agentId\":\"ghost\"}"))).andExpect(status().isBadRequest());

        mvc.perform(as(post("/api/agents/remote").content("{\"name\":\"X\",\"endpoint\":\"https://agents.example.com\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("allowed-hosts")));
        mvc.perform(as(delete("/api/agents/weekend"))).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/agents/" + id))).andExpect(status().isNoContent());
    }

    @Test
    void agentInboxRejectsUnknownTokensAndNeverUsesTheOwnerSession() throws Exception {
        mvc.perform(post("/agent-inbox").contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hi\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/agent-inbox").header("Authorization", "Bearer " + tokens.issue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}")).andExpect(status().isUnauthorized());   // an owner session is not an agent token
        mvc.perform(as(get("/api/messages"))).andExpect(jsonPath("$.length()").value(0));
    }
}
