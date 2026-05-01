package com.golivebackend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single chat message in a stream room.
 *
 * This DTO serves DUAL PURPOSE — inbound and outbound:
 *
 *   INBOUND (client → server):
 *     Client sends: { streamId, senderName, content }
 *     timestamp is null — server sets it
 *
 *   OUTBOUND (server → all subscribers):
 *     Server sends back: { streamId, senderName, content, timestamp }
 *     timestamp now populated
 *
 * WHY ONE DTO FOR BOTH DIRECTIONS?
 * Chat messages are simple and symmetric — the shape going in
 * is almost identical to the shape going out (plus timestamp).
 * Two separate DTOs would add complexity without real benefit here.
 * If chat grows (reactions, message types, threading), split them.
 *
 * WHY A RECORD?
 * Records are immutable — correct for a message that should never
 * be modified after creation. We use a custom constructor to enforce
 * server-side timestamp assignment.
 */
public record ChatMessage(

        @NotNull(message = "streamId is required")
        UUID streamId,

        /*
         * No account system — sender identifies themselves by name.
         * The frontend should default this to "Anonymous" if blank.
         * We enforce it server-side as well.
         */
        @NotBlank(message = "senderName is required")
        @Size(max = 50, message = "senderName must not exceed 50 characters")
        String senderName,

        @NotBlank(message = "Message content must not be blank")
        @Size(max = 500, message = "Message must not exceed 500 characters")
        String content,

        /*
         * Set by the server when the message is received.
         * NOT provided by the client — we never trust client timestamps.
         * Client clocks can be wrong, manipulated, or in wrong timezones.
         * All timestamps are server-authoritative and UTC.
         */
        Instant timestamp

) {

    /**
     * Factory method — creates an outbound message with server timestamp.
     *
     * Called by ChatWebSocketController after receiving the inbound message.
     * Returns a new record with timestamp populated — the version
     * that gets broadcast to all subscribers.
     */
    public static ChatMessage withTimestamp(ChatMessage incoming) {
        return new ChatMessage(
                incoming.streamId(),
                incoming.senderName(),
                incoming.content(),
                Instant.now()
        );
    }
}