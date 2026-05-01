package com.golivebackend.config.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP broker configuration.
 *
 * @EnableWebSocketMessageBroker activates the full STOMP messaging
 * infrastructure. This is what enables:
 *   - @MessageMapping on controller methods
 *   - SimpMessagingTemplate for server-to-client pushes
 *   - Topic-based subscriptions (/topic/...)
 *
 * WHY WebSocketMessageBrokerConfigurer AND NOT WebSocketConfigurer?
 * WebSocketConfigurer sets up raw WebSocket (no STOMP).
 * WebSocketMessageBrokerConfigurer adds the full STOMP broker layer.
 * We need STOMP for topic-based routing — raw WebSocket would require
 * us to implement routing ourselves.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Configures the STOMP message broker.
     *
     * TWO TYPES OF DESTINATIONS:
     *
     * 1. Application destinations (/app):
     *    Messages sent by clients to /app/... are routed to
     *    @MessageMapping methods in our controllers.
     *    Example: client SEND /app/chat/send → ChatWebSocketController
     *
     * 2. Broker destinations (/topic):
     *    Messages sent to /topic/... go through the in-memory broker
     *    and are pushed to all subscribed clients.
     *    Example: server sends to /topic/streams/abc/chat →
     *             all clients subscribed to that topic receive it
     *
     * WHY /topic AND NOT /queue?
     * /topic = publish-subscribe (one message → many subscribers)
     *          Correct for chat — everyone in the stream sees the message
     * /queue = point-to-point (one message → one specific user)
     *          Correct for private messages, notifications to one user
     *
     * We use /topic because chat is broadcast by nature.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        /*
         * Enable the simple in-memory message broker for /topic destinations.
         *
         * "Simple" broker = runs in the same JVM, no external dependency.
         * This is correct for MVP. For production scale, swap this for
         * a full broker (RabbitMQ, ActiveMQ) using enableStompBrokerRelay().
         * The rest of the code doesn't change — only this config line.
         */
        registry.enableSimpleBroker("/topic");

        /*
         * Prefix for messages routed to @MessageMapping controllers.
         * Client sends to /app/chat/send → maps to @MessageMapping("/chat/send")
         *
         * WHY A PREFIX?
         * Without it, /chat/send could be ambiguous — is it an HTTP
         * endpoint or a WebSocket destination? The /app prefix makes
         * WebSocket application destinations unambiguous.
         */
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers the WebSocket endpoint that clients connect to.
     *
     * ENDPOINT: /ws
     * Full path (with context-path): /api/ws
     * This is what the frontend connects to first to establish
     * the WebSocket/STOMP session.
     *
     * SockJS FALLBACK:
     * withSockJS() enables SockJS protocol support.
     * SockJS tries native WebSocket first. If the browser or
     * network doesn't support it (corporate proxies, old browsers),
     * it falls back to HTTP long-polling transparently.
     * The frontend SockJS client handles this automatically.
     *
     * WHY ALLOW CORS HERE SEPARATELY?
     * The HTTP CORS config (CorsConfig.java) has NO effect on
     * WebSocket connections. The WebSocket handshake is a separate
     * HTTP upgrade request that needs its own allowed origins.
     * setAllowedOrigins() here controls who can initiate that upgrade.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }
}