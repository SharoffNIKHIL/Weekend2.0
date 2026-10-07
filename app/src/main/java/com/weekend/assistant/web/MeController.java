package com.weekend.assistant.web;

import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.domain.MemoryKind;
import com.weekend.assistant.memory.MemoryService;
import com.weekend.assistant.owner.OwnerProfile;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who the owner is, for the greeting and the "About you" strip: name and a few pinned facts from the private profile. */
@RestController
@RequestMapping("/api")
public class MeController {

    private final OwnerProfile profile;
    private final MemoryService memories;

    public MeController(OwnerProfile profile, MemoryService memories) {
        this.profile = profile;
        this.memories = memories;
    }

    public record Me(String name, List<String> highlights, long pinned, long memories) {}

    @GetMapping("/me")
    public Me me() {
        List<Memory> all = memories.all();
        List<String> highlights = all.stream().filter(Memory::pinned).filter(m -> m.kind() == MemoryKind.FACT)
                .sorted(java.util.Comparator.comparing(Memory::createdAt))           // profile order: who you are comes first
                .map(Memory::text).filter(t -> !t.toLowerCase(java.util.Locale.ROOT).startsWith("my name is"))
                .limit(4).map(t -> t.length() > 90 ? t.substring(0, 87) + "..." : t).toList();
        return new Me(profile.name(), highlights, all.stream().filter(Memory::pinned).count(), all.size());
    }
}
