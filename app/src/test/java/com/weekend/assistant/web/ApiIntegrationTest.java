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
    void infoNeedsASessionAndHoldsNoIdentifiers() throws Exception {
        mvc.perform(get("/api/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/info").header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("local"))
                .andExpect(jsonPath("$.retentionDays.messages").value(365))
                .andExpect(jsonPath("$.gcpProject").doesNotExist())
                .andExpect(jsonPath("$.sessionKey").doesNotExist());
    }

    @Test
    void brandInfoIsPublicAndEmptyWithoutAPack() throws Exception {
        mvc.perform(get("/brand.json")).andExpect(status().isOk()).andExpect(jsonPath("$.companion").value(false));
        mvc.perform(get("/brand/logo.svg")).andExpect(status().isNotFound());
    }

    @Test
    void servesTheUi() throws Exception {
        for (String asset : new String[] {"/index.html", "/app.js", "/theme.js", "/styles.css", "/sw.js", "/icon.svg", "/logo.svg", "/companion.js", "/companion.svg"}) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
    }
}
