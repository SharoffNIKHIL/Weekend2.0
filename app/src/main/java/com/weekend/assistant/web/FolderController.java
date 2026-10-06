package com.weekend.assistant.web;

import com.weekend.assistant.domain.Folder;
import com.weekend.assistant.workspace.FolderService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Home-screen folders for tasks and reminders. */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folders;

    public FolderController(FolderService folders) {
        this.folders = folders;
    }

    public record FolderRequest(String name, String icon) {}

    @GetMapping
    public List<FolderService.FolderSummary> list() {
        return folders.summaries();
    }

    @GetMapping("/icons")
    public Map<String, Object> icons() {
        return Map.of("icons", FolderService.ICONS.stream().sorted().toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FolderService.FolderContents> get(@PathVariable String id) {
        return ResponseEntity.of(folders.contents(id));
    }

    @PostMapping
    public ResponseEntity<Folder> create(@RequestBody FolderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(folders.create(req.name(), req.icon()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Folder> update(@PathVariable String id, @RequestBody FolderRequest req) {
        return ResponseEntity.of(folders.update(id, req.name(), req.icon()));
    }

    /** Items in the folder are kept and become unfiled. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return folders.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
