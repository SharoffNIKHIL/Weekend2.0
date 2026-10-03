package com.weekend.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point. Runs on Cloud Run (container) or locally with {@code mvn spring-boot:run}. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WeekendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeekendApplication.class, args);
    }
}
