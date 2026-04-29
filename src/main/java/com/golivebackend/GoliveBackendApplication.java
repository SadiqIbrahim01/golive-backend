package com.golivebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @EnableConfigurationProperties activates scanning for classes
 * annotated with @ConfigurationProperties.
 * Without this, LiveKitProperties is not bound to application.yml values.
 *
 * Alternative: add @ConfigurationPropertiesScan to scan the full
 * package automatically — we use the explicit annotation so it's
 * visible and intentional.
 */

@SpringBootApplication
@EnableConfigurationProperties
public class GoliveBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoliveBackendApplication.class, args);
    }

}
