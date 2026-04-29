package com.golivebackend.livekit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

/**
 * Strongly-typed configuration for LiveKit credentials.
 *
 * @ConfigurationProperties(prefix = "livekit"):
 * Spring reads all properties under the "livekit" key in application.yml
 * and maps them to fields in this class by name.
 *
 * application.yml:          maps to:
 *   livekit:
 *     url: wss://...    →   this.url
 *     api-key: abc      →   this.apiKey  (kebab-case → camelCase auto)
 *     api-secret: xyz   →   this.apiSecret
 *
 * @Component: registers this as a Spring bean so it can be
 * injected into LiveKitTokenService via constructor injection.
 *
 * WHY NOT A RECORD?
 * @ConfigurationProperties requires mutable fields (Spring sets them
 * via setters after construction). Records are immutable — they don't
 * work with the standard property binding mechanism.
 * We use @Getter + @Setter (Lombok) to satisfy the binding requirement
 * while keeping the class concise.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "livekit")
public class LiveKitProperties {

    /**
     * LiveKit server WebSocket URL.
     * Format: wss://your-project.livekit.cloud
     * Used by the SDK when constructing tokens — embedded in the token
     * so the frontend knows which LiveKit server to connect to.
     */
    private String url;

    /**
     * LiveKit API key.
     * Public identifier for your LiveKit project.
     * Used to sign tokens — included in the JWT header.
     */
    private String apiKey;

    /**
     * LiveKit API secret.
     * The private signing secret. NEVER logged, NEVER returned to clients.
     * If this leaks, anyone can generate valid tokens for your rooms.
     */
    private String apiSecret;
}