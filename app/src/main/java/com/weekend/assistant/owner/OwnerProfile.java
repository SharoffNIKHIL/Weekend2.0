package com.weekend.assistant.owner;

import com.weekend.assistant.config.WeekendProperties;
import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.memory.MemoryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Makes the agent know its owner: imports a private, local profile file (weekend.owner.profile-file, never committed)
 * as pinned memories at start-up. Pinned memories are always in the agent's context (P5: kept until the owner deletes
 * them; P6: exported and deleted like any memory; 🔓 P7: sent to the model with each prompt).
 *
 * <p>Format: one item per line starting with "- ", optionally typed: "- fact: …", "- pref: …", "- task: …";
 * "- name: …" sets the name used in the greeting (not stored as a memory).
 * Headings and other lines are ignored. Lines that look like secrets are skipped. Re-runs do not duplicate.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OwnerProfile implements ApplicationRunner {

    static final int MAX_ITEMS = 200;
    private static final Logger log = LoggerFactory.getLogger(OwnerProfile.class);

    private final MemoryService memories;
    private final String file;
    private int loaded;
    private volatile String name;

    public OwnerProfile(MemoryService memories, WeekendProperties props) {
        this.memories = memories;
        this.file = props.owner().profileFile();
    }

    public record Item(MemoryKind kind, String text) {}

    @Override
    public void run(ApplicationArguments args) {
        if (file == null || file.isBlank()) {
            return;
        }
        Path path = Path.of(file);
        if (!Files.isRegularFile(path)) {
            log.info("owner profile not found; skipped");
            return;
        }
        try {
            loaded = importText(Files.readString(path, StandardCharsets.UTF_8));
            log.info("owner profile: {} items imported as pinned memories", loaded);
        } catch (IOException e) {
            log.warn("owner profile not readable; skipped");
        }
    }

    /** Imports profile text; returns how many new memories were added. */
    public int importText(String text) {
        text.lines().map(String::strip).filter(l -> l.toLowerCase(Locale.ROOT).startsWith("- name:")).findFirst()
                .map(l -> l.substring(7).strip()).filter(n -> !n.isEmpty() && n.length() <= 40).ifPresent(n -> name = n);
        Set<String> existing = memories.all().stream().map(m -> m.text().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        int added = 0;
        for (Item item : parse(text)) {
            if (existing.contains(item.text().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Optional<Memory> m = memories.remember(item.text(), item.kind(), null);
            if (m.isPresent()) {
                memories.pin(m.get().id(), true);
                existing.add(item.text().toLowerCase(Locale.ROOT));
                added++;
            }
        }
        return added;
    }

    static List<Item> parse(String text) {
        return text.lines().map(String::strip).filter(l -> l.startsWith("- ") || l.startsWith("* "))
                .map(l -> l.substring(2).strip()).filter(l -> !l.isEmpty() && !l.toLowerCase(Locale.ROOT).startsWith("name:"))
                .limit(MAX_ITEMS).map(OwnerProfile::item).toList();
    }

    private static Item item(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (String[] p : new String[][] {{"fact:", "FACT"}, {"pref:", "PREFERENCE"}, {"preference:", "PREFERENCE"}, {"task:", "TASK"}}) {
            if (lower.startsWith(p[0])) {
                return new Item(MemoryKind.valueOf(p[1]), line.substring(p[0].length()).strip());
            }
        }
        return new Item(MemoryKind.FACT, line);
    }

    /** The owner's name from the profile, or null. */
    public String name() {
        return name;
    }

    public int loaded() {
        return loaded;
    }
}
