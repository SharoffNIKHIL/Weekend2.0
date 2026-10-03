package com.weekend.assistant.web;

import com.weekend.assistant.domain.Memory;
import com.weekend.assistant.memory.MemoryService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** P6: the owner can see, pin and delete every memory. */
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memories;

    public MemoryController(MemoryService memories) {
        this.memories = memories;
    }

    public record PinRequest(boolean pinned) {}

    @GetMapping
    public List<Memory> list() {
        return memories.all();
    }

    @PostMapping("/{id}/pin")
    public ResponseEntity<Memory> pin(@PathVariable String id, @RequestBody PinRequest req) {
        return ResponseEntity.of(memories.pin(id, req.pinned()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return memories.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
