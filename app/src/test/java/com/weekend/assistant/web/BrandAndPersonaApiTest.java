package com.weekend.assistant.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weekend.assistant.security.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Private brand pack at /brand/ and the persona API, with owner sessions on. */
@SpringBootTest(properties = "weekend.owner.brand-dir=src/test/resources/brand-fixture")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class BrandAndPersonaApiTest {

    @Autowired MockMvc mvc;
    @Autowired SessionTokenService tokens;

    private String bearer() {
        return "Bearer " + tokens.issue();
    }

    @Test
    void servesThePrivateBrandPackOnly() throws Exception {
        mvc.perform(get("/brand/companion.svg")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-companion")));
        mvc.perform(get("/brand/missing.svg")).andExpect(status().isNotFound());
        mvc.perform(get("/brand/../application-test.yml")).andExpect(status().is4xxClientError());
        mvc.perform(get("/companion.svg")).andExpect(status().isOk());       // public fallback always there
        mvc.perform(get("/brand.json")).andExpect(jsonPath("$.companion").value(true)).andExpect(jsonPath("$.logo").value(true))
                .andExpect(jsonPath("$.icon").value(false));
    }

    @Test
    void personaUpdatesValidateAndReportMoodAndBudget() throws Exception {
        mvc.perform(put("/api/features/optimal/persona").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"FUNNY\",\"humor\":9,\"truth\":6,\"focus\":3,\"efficiency\":2,\"search\":\"MEMORY\",\"approval\":\"WRITES_AND_EXTERNAL\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/features/optimal/persona").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"CUSTOM\",\"humor\":3,\"truth\":9,\"focus\":8,\"efficiency\":5,\"search\":\"WEB\",\"approval\":\"ALL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mood").value("SERIOUS")).andExpect(jsonPath("$.focusMode").value(true))
                .andExpect(jsonPath("$.budget.label").value("Max")).andExpect(jsonPath("$.budget.strongModel").value(true));
        mvc.perform(put("/api/features/optimal/persona").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"NOPE\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/features/ghost/persona").header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"WORK\",\"humor\":3,\"truth\":9,\"focus\":8,\"efficiency\":3,\"search\":\"WEB\",\"approval\":\"ALL\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/features").header("Authorization", bearer())).andExpect(jsonPath("$.modes[0].mode").value("FUNNY"))
                .andExpect(jsonPath("$.modes[0].mood").value("HAPPY")).andExpect(jsonPath("$.features[1].persona.efficiency").value(5));
    }
}
