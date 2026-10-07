package com.weekend.assistant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.weekend.assistant.security.SessionTokenService;
import com.weekend.assistant.studio.VideoRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The whole Studio over HTTP: chat asks → plan → Start → render → library → signed, range-capable media links. */
@SpringBootTest(properties = {"weekend.studio.scale=0.15", "weekend.studio.fps=8",
        "weekend.studio.media-dir=${java.io.tmpdir}/weekend-studio-api-test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class StudioApiTest {

    @Autowired MockMvc mvc;
    @Autowired SessionTokenService tokens;

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req) {
        return req.header("Authorization", "Bearer " + tokens.issue()).contentType(MediaType.APPLICATION_JSON);
    }

    private String chat(String conv, String message) throws Exception {
        String body = conv == null ? "{\"message\":\"" + message + "\",\"featureId\":\"studio\"}"
                : "{\"conversationId\":\"" + conv + "\",\"message\":\"" + message + "\",\"featureId\":\"studio\"}";
        return mvc.perform(as(post("/api/chat")).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void chatToFinishedVideo() throws Exception {
        assumeTrue(VideoRenderer.ffmpegAvailable("ffmpeg"), "ffmpeg not installed");
        String first = chat(null, "make me a short about the water cycle");
        assertThat((String) JsonPath.read(first, "$.studio.kind")).isEqualTo("ASK");
        assertThat((String) JsonPath.read(first, "$.model")).isEqualTo("studio");
        String conv = JsonPath.read(first, "$.conversationId");
        assertThat((List<String>) JsonPath.read(first, "$.studio.options")).contains("45 sec Short");

        String second = chat(conv, "15 sec");
        assertThat((String) JsonPath.read(second, "$.studio.text")).contains("voice");
        String plan = chat(conv, "Captions only");
        assertThat((String) JsonPath.read(plan, "$.studio.kind")).isEqualTo("CONFIRM");
        assertThat((String) JsonPath.read(plan, "$.studio.text")).contains("15 sec Short on The water cycle");
        String started = chat(conv, "Start");
        assertThat((String) JsonPath.read(started, "$.studio.kind")).isEqualTo("STARTED");
        String project = JsonPath.read(started, "$.studio.projectId");

        String job = null;
        for (int i = 0; i < 600 && job == null; i++) {
            String p = mvc.perform(as(get("/api/studio/projects/" + project))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String st = JsonPath.read(p, "$.jobs[0].status");
            assertThat(st).isNotEqualTo("FAILED");
            job = "READY".equals(st) ? p : null;
            Thread.sleep(100);
        }
        assertThat(job).as("render finished").isNotNull();
        assertThat((String) JsonPath.read(job, "$.jobs[0].metadata.description")).contains("#Shorts");
        assertThat((Boolean) JsonPath.read(job, "$.jobs[0].draftScript")).isTrue();
        String video = JsonPath.read(job, "$.jobs[0].files.video");
        assertThat(video).startsWith("/media/").contains("sig=");

        // signed link works without the session token, supports ranges, and can be saved as a file
        mvc.perform(get(video)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "video/mp4"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get(video).header("Range", "bytes=0-99")).andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", org.hamcrest.Matchers.startsWith("bytes 0-99/")));
        mvc.perform(get(video + "&download=1")).andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("the-water-cycle")));
        // tampered, wrong file, or unknown names are refused
        mvc.perform(get(video.replace("sig=", "sig=x"))).andExpect(status().isForbidden());
        mvc.perform(get(video.replace("video.mp4", "captions.srt"))).andExpect(status().isForbidden());
        mvc.perform(get(video.replace("video.mp4", "..%2F..%2Fsecret"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/studio/projects")).andExpect(status().isUnauthorized());

        mvc.perform(as(get("/api/studio/projects"))).andExpect(jsonPath("$[0].id").value(project));
        mvc.perform(as(get("/api/export"))).andExpect(jsonPath("$.other.studio[0].topic").value("The water cycle"));
        mvc.perform(as(delete("/api/studio/projects/" + project))).andExpect(status().isNoContent());
        mvc.perform(get(video)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/studio/projects/" + project))).andExpect(status().isNotFound());
    }

    @Test
    void ordinaryQuestionsStillGoToTheAgent() throws Exception {
        String r = chat(null, "what is the water cycle?");
        assertThat((Object) JsonPath.read(r, "$.studio")).isNull();
        assertThat((String) JsonPath.read(r, "$.model")).isNotEqualTo("studio");
        mvc.perform(as(get("/api/studio/status"))).andExpect(jsonPath("$.voice").value(false)).andExpect(jsonPath("$.research").value(false));
    }
}
