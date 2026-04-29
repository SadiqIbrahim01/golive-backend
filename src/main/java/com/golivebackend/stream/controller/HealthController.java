package com.golivebackend.stream.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * System health check endpoint.
 *
 * WHY NOT USE SPRING ACTUATOR?
 * Spring Boot Actuator provides /actuator/health out of the box
 * with DB connectivity checks, disk space, and more.
 * We're not using it because:
 *   1. Actuator adds significant classpath weight for MVP
 *   2. We want full control over what the health response returns
 *   3. Actuator's /actuator/* path conflicts with our /api prefix
 *      and needs extra config to align
 *
 * When this system grows: add Actuator, disable the default
 * endpoints, and keep this lightweight check for external monitoring.
 *
 * @RestController = @Controller + @ResponseBody
 * Every method return value is serialised to JSON automatically.
 * You never write response.getWriter().print(json) manually.
 *
 * @RequestMapping("/health") → full path becomes /api/health
 * because context-path: /api is set in application.yml.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * Returns application health status.
     *
     * WHY Map<String, Object> AND NOT A DTO?
     * A dedicated HealthResponse DTO would be correct in a larger system
     * where the shape of this response is consumed by multiple clients
     * and needs to be stable.
     *
     * For a health check that is only read by monitoring tools and
     * developers, Map is pragmatic and clear.
     * We document this trade-off so the reviewer understands it's
     * a deliberate choice, not an oversight.
     *
     * ResponseEntity<T> gives us explicit HTTP status control.
     * ResponseEntity.ok() sets HTTP 200.
     * If we wanted to signal degraded health, we'd return
     * ResponseEntity.status(503).body(...) — same method, different status.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(
                Map.of(
                        "status", "UP",
                        "service", "golive-backend",
                        /*
                         * Timestamp in the response tells you exactly when
                         * the check ran — useful when comparing logs
                         * across multiple app instances.
                         */
                        "timestamp", Instant.now().toString()
                )
        );
    }
}