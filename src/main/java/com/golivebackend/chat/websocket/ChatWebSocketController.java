package com.golivebackend.chat.websocket;

import com.golivebackend.chat.dto.ChatMessage;
import com.golivebackend.common.exception.StreamNotFoundException;
import com.golivebackend.stream.model.StreamStatus;
import com.golivebackend.stream.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

/**
 * WebSocket/STOMP controller for real-time chat.
 *
 * WHY @Controller AND NOT @RestController?
 * @RestController is for HTTP endpoints that return JSON responses.
 * @MessageMapping methods don't return HTTP responses — they push
 * messages to STOMP topics via SimpMessagingTemplate.
 * @Controller is the correct annotation for WebSocket message handlers.
 *
 * HOW MESSAGES FLOW THROUGH THIS CLASS:
 *   1. Client sends STOMP SEND frame to /app/chat/send
 *   2. Spring routes to handleChatMessage() via @MessageMapping
 *   3. We validate the stream is LIVE
 *   4. We enrich message with server timestamp
 *   5. SimpMessagingTemplate broadcasts to /topic/streams/{id}/chat
 *   6. All subscribers on that topic receive the message instantly
 */
@Slf4j
@Controller
@Validated
@RequiredArgsConstructor
public class ChatWebSocketController {

    /*
     * SimpMessagingTemplate: the primary tool for server-initiated
     * message delivery in Spring WebSocket.
     *
     * "Simp" = Simple Messaging Protocol (STOMP is one implementation).
     * convertAndSend() serialises the object to JSON and delivers it
     * to all clients subscribed to the given destination.
     *
     * Spring auto-configures this bean when @EnableWebSocketMessageBroker
     * is present — we just inject it.
     */
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamRepository streamRepository;

    /**
     * Handles incoming chat messages from any participant.
     *
     * @MessageMapping("/chat/send"):
     * Combined with the /app prefix from WebSocketConfig,
     * this method handles STOMP SEND frames to /app/chat/send.
     *
     * @Payload: extracts and deserialises the STOMP message body
     * into a ChatMessage record. Jackson handles the JSON → record
     * conversion automatically.
     *
     * VALIDATION:
     * @Validated on the class + @Valid on the parameter would be
     * ideal, but Spring WebSocket's @MessageMapping doesn't support
     * @Valid on @Payload the same way MVC does.
     * We do manual validation in the method body for now —
     * noted as a known limitation with the upgrade path below.
     *
     * UPGRADE PATH: implement a ChannelInterceptor that validates
     * STOMP message payloads before they reach the controller.
     */
    @MessageMapping("/chat/send")
    public void handleChatMessage(@Payload ChatMessage incomingMessage) {
        log.debug("Chat message received — streamId: {}, sender: {}",
                incomingMessage.streamId(), incomingMessage.senderName());

        /*
         * GUARD 1: Stream must exist.
         * Reject messages for non-existent streams immediately.
         * Without this, anyone could spam topics for fake stream IDs.
         */
        var stream = streamRepository
                .findByStreamId(incomingMessage.streamId())
                .orElseThrow(() -> new StreamNotFoundException(
                        "Stream not found: " + incomingMessage.streamId()
                ));

        /*
         * GUARD 2: Stream must be LIVE.
         * Chat only works during an active stream.
         * Reject messages for CREATED (not started) or ENDED streams.
         *
         * WHY LOG AND RETURN RATHER THAN THROW?
         * In WebSocket context, throwing an exception doesn't return
         * a clean HTTP error to the client — it can terminate the
         * WebSocket session entirely.
         * For a non-critical guard like "stream isn't live yet",
         * silently dropping the message (with a log) is a better
         * user experience than disconnecting the client.
         */
        if (stream.getStatus() != StreamStatus.LIVE) {
            log.warn("Chat rejected — stream {} is not LIVE (status: {})",
                    incomingMessage.streamId(), stream.getStatus());
            return;
        }

        /*
         * GUARD 3: Basic content validation.
         * Reject blank messages or messages exceeding size limit.
         * Same reasoning as above — log and return, don't throw.
         */
        if (incomingMessage.content() == null
                || incomingMessage.content().isBlank()) {
            log.warn("Chat rejected — blank message from: {}",
                    incomingMessage.senderName());
            return;
        }

        if (incomingMessage.content().length() > 500) {
            log.warn("Chat rejected — message too long from: {}",
                    incomingMessage.senderName());
            return;
        }

        /*
         * Enrich the message with a server-authoritative timestamp.
         * The outbound message is what gets broadcast — never the
         * raw inbound message with its null timestamp.
         */
        ChatMessage outboundMessage = ChatMessage.withTimestamp(incomingMessage);

        /*
         * BROADCAST:
         * convertAndSend() delivers to ALL clients subscribed to this topic.
         * The destination is stream-scoped: each stream has its own chat topic.
         * Messages for stream A never appear in stream B's chat.
         *
         * Destination format: /topic/streams/{streamId}/chat
         * This matches what the frontend subscribes to.
         */
        String destination = "/topic/streams/"
                + incomingMessage.streamId()
                + "/chat";

        messagingTemplate.convertAndSend(destination, outboundMessage);

        log.debug("Chat message broadcast — destination: {}, sender: {}",
                destination, outboundMessage.senderName());
    }
}