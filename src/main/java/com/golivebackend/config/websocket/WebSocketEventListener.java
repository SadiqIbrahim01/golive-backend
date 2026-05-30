package com.golivebackend.config.websocket;

import com.golivebackend.stream.service.StreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to WebSocket session lifecycle events to maintain
 * accurate real-time viewer counts.
 *
 * WHY ConcurrentHashMap?
 * WebSocket events fire on different threads simultaneously.
 * ConcurrentHashMap is thread-safe — multiple events can
 * read/write without corrupting the map.
 * HashMap is not thread-safe and would cause silent data loss
 * under concurrent access.
 *
 * LIFECYCLE:
 *   SessionSubscribeEvent → viewer joined a stream topic
 *   SessionDisconnectEvent → viewer left (for ANY reason)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final StreamService streamService;

    /*
     * Tracks which stream each WebSocket session is watching.
     * Key:   STOMP session ID (unique per connection)
     * Value: streamId they subscribed to
     *
     * This map is in-memory — it resets if the server restarts.
     * That's acceptable: viewer counts reset on restart anyway.
     */
    private final Map<String, UUID> sessionStreamMap = new ConcurrentHashMap<>();

    /**
     * Fires when a client subscribes to any STOMP topic.
     * We only care about stream chat subscriptions —
     * /topic/streams/{streamId}/chat
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId   = accessor.getSessionId();

        if (destination == null || sessionId == null) return;

        /*
         * Only react to stream chat subscriptions.
         * Pattern: /topic/streams/{uuid}/chat
         *
         * We ignore other topics (future topics won't accidentally
         * trigger viewer count changes).
         */
        if (!destination.startsWith("/topic/streams/") || !destination.endsWith("/chat")) {
            return;
        }

        try {
            /*
             * Parse the streamId from the destination path.
             * /topic/streams/{streamId}/chat
             * Split by "/" → ["", "topic", "streams", "{streamId}", "chat"]
             * Index 3 = streamId
             */
            String[] parts = destination.split("/");
            UUID streamId = UUID.fromString(parts[3]);

            sessionStreamMap.put(sessionId, streamId);
            streamService.incrementViewerCount(streamId);

            log.info("Viewer joined stream {} — sessionId: {}", streamId, sessionId);

        } catch (IllegalArgumentException e) {
            log.warn("Could not parse streamId from destination: {}", destination);
        }
    }

    /**
     * Fires when a WebSocket session disconnects.
     * This ALWAYS fires — tab close, network drop, crash, clean disconnect.
     * This is the reliability guarantee that PATCH endpoints can't provide.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (sessionId == null) return;

        UUID streamId = sessionStreamMap.remove(sessionId);

        if (streamId != null) {
            streamService.decrementViewerCount(streamId);
            log.info("Viewer left stream {} — sessionId: {}", streamId, sessionId);
        }
    }
}