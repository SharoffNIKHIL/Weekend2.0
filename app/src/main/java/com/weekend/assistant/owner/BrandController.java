package com.weekend.assistant.owner;

import com.weekend.assistant.config.WeekendProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tells the UI which private brand files exist, so it never probes /brand/ for missing ones. Reveals no content. */
@RestController
public class BrandController {

    private final String dir;

    public BrandController(WeekendProperties props) {
        this.dir = props.owner().brandDir();
    }

    @GetMapping("/brand.json")
    public Map<String, Boolean> brand() {
        return Map.of("logo", has("logo.svg"), "icon", has("icon.svg"), "companion", has("companion.svg"));
    }

    private boolean has(String file) {
        return dir != null && !dir.isBlank() && Files.isRegularFile(Path.of(dir, file));
    }
}
