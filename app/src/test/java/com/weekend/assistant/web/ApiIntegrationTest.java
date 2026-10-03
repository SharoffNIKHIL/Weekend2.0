package com.weekend.assistant.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weekend.assistant.security.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the full app (offline LLM, in-memory stores) and drives it over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SessionTokenService tokens;

    private String bearer() {
        return "Bearer " + tokens.issue();
    }

    @Test
    void healthIsOpenButApiNeedsASession() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/api/memories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/memories").header("Authorization", "Bearer forged.token")).andExpect(status().isUnauthorized());
    }

    @Test
    void chatEndToEndWithTool() throws Exception {
        mvc.perform(post("/api/chat").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"what time is it?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolsUsed[0]").value("current_time"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void validationAndDeleteAllGuard() throws Exception {
        mvc.perform(post("/api/chat").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/delete-all").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"yes\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/export").header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLog").isArray());
    }

    @Test
    void servesTheUi() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
