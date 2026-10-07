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
            "/api/notifications", "/api/features", "/api/me"})
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
    void featuresCarryCapabilitiesPersonaAndInstructions() throws Exception {
        mvc.perform(as(get("/api/features"))).andExpect(jsonPath("$.features.length()").value(12))
                .andExpect(jsonPath("$.features[0].id").value("studio")).andExpect(jsonPath("$.features[1].id").value("optimal")).andExpect(jsonPath("$.modes.length()").value(4));
        mvc.perform(as(get("/api/features/notes"))).andExpect(jsonPath("$.capabilities[0].id").value("NOTION"))
                .andExpect(jsonPath("$.capabilities[0].kind").value("CONNECTOR")).andExpect(jsonPath("$.capabilities[0].available").value(false))
                .andExpect(jsonPath("$.capabilities[0].reason").value(org.hamcrest.Matchers.containsString("WEEKEND_NOTION_TOKEN")));
        mvc.perform(as(get("/api/features/image"))).andExpect(jsonPath("$.acceptsImages").value(true)).andExpect(jsonPath("$.makesArt").value(true))
                .andExpect(jsonPath("$.capabilities[0].id").value("VISION")).andExpect(jsonPath("$.capabilities[0].available").value(true));
        mvc.perform(as(get("/api/features/studio"))).andExpect(jsonPath("$.group").value("STUDIO"))
                .andExpect(jsonPath("$.capabilities[0].id").value("RENDER")).andExpect(jsonPath("$.capabilities[0].kind").value("PLUGIN"))
                .andExpect(jsonPath("$.capabilities[1].id").value("RESEARCH")).andExpect(jsonPath("$.capabilities[1].available").value(false))
                .andExpect(jsonPath("$.capabilities[1].reason").value(org.hamcrest.Matchers.containsString("WEEKEND_STUDIO_WEB_SEARCHES")))
                .andExpect(jsonPath("$.capabilities[3].reason").value(org.hamcrest.Matchers.containsString("commons.wikimedia.org")));
        mvc.perform(as(get("/api/features/ghost"))).andExpect(status().isNotFound());

        mvc.perform(as(put("/api/features/coding/instructions").content("{\"text\":\"Prefer pytest.\"}")))
                .andExpect(jsonPath("$.instructions").value("Prefer pytest.")).andExpect(jsonPath("$.customised").value(true));
        mvc.perform(as(put("/api/features/coding/instructions").content("{\"text\":\"key AKIAABCDEFGHIJKLMNOP\"}"))).andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/features/coding/persona").content(
                "{\"mode\":\"CUSTOM\",\"humor\":2,\"truth\":10,\"focus\":9,\"efficiency\":5,\"search\":\"WEB\",\"approval\":\"ALL\"}")))
                .andExpect(jsonPath("$.focusMode").value(true)).andExpect(jsonPath("$.mood").value("SERIOUS"));
        mvc.perform(as(post("/api/features/coding/reset"))).andExpect(jsonPath("$.customised").value(false))
                .andExpect(jsonPath("$.persona.efficiency").value(4));
        mvc.perform(as(put("/api/features/ghost/persona").content("{\"mode\":\"WORK\",\"humor\":3,\"truth\":9,\"focus\":8,\"efficiency\":3,\"search\":\"WEB\",\"approval\":\"ALL\"}")))
                .andExpect(status().isNotFound());

        mvc.perform(as(post("/api/chat").content("{\"message\":\"hi\",\"featureId\":\"smooth\"}"))).andExpect(jsonPath("$.featureName").value("Smooth"));
        mvc.perform(as(post("/api/chat").content("{\"message\":\"hi\",\"featureId\":\"ghost\"}"))).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/chat").content("{\"message\":\"look\",\"featureId\":\"image\",\"images\":[\"data:image/png;base64,PGh0bWw+\"]}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("does not match")));
        mvc.perform(as(get("/api/me"))).andExpect(jsonPath("$.highlights").isArray());
    }

}
