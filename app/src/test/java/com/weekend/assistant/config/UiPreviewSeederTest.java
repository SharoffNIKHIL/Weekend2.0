package com.weekend.assistant.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weekend.assistant.TestFixtures;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.ReminderStatus;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.reminder.ReminderService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The UI preview env ({@code local,ui}) seeds demo data, serves it without a session, and guards itself. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "ui"})
class UiPreviewSeederTest {

    @Autowired MockMvc mvc;
    @Autowired MemoryService memories;
    @Autowired ReminderService reminders;

    @Test
    void seedsDemoDataForEveryScreen() throws Exception {
        assertEquals(5, memories.all().size());
        assertEquals(2, memories.all().stream().filter(Memory::pinned).count());
        assertEquals(3, reminders.all().stream().filter(r -> r.status() == ReminderStatus.SCHEDULED).count());
        assertEquals(1, reminders.all().stream().filter(r -> r.status() == ReminderStatus.CANCELLED).count());
        mvc.perform(get("/api/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("local"))
                .andExpect(jsonPath("$.dataLeavesIndia").value(false));
    }

    @Test
    void refusesToRunWhenSessionsAreRequired() {
        // TestFixtures.props(): offline model but require-session = true, i.e. a real-environment setting.
        UiPreviewSeeder seeder = new UiPreviewSeeder(memories, reminders, TestFixtures.props(), Clock.systemUTC());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> seeder.run(null));
        assertTrue(e.getMessage().contains("ui profile"));
    }
}
