package com.weekend.assistant.owner;

import com.weekend.assistant.config.WeekendProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Private brand pack: when weekend.owner.brand-dir points at a local folder (never committed), its files are served
 * at /brand/ (logo.svg, icon.svg, companion.svg). The UI falls back to the public assets when a file is missing.
 */
@Configuration
public class BrandConfig implements WebMvcConfigurer {

    private final String dir;

    public BrandConfig(WeekendProperties props) {
        this.dir = props.owner().brandDir();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (dir != null && !dir.isBlank() && Files.isDirectory(Path.of(dir))) {
            registry.addResourceHandler("/brand/**").addResourceLocations(Path.of(dir).toUri().toString());
        }
    }
}
