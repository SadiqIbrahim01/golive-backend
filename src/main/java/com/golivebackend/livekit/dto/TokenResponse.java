package com.golivebackend.livekit.dto;

/**
 * Outbound DTO for token generation responses.
 *
 * {
 *   "token": "eyJhbGciOiJIUzI1NiJ9...",
 *   "livekit_url": "wss://your-project.livekit.cloud",
 *   "room_name": "room-abc123"
 * }
 *
 * WHY INCLUDE livekitUrl IN THE RESPONSE?
 * The frontend needs both the token AND the LiveKit server URL
 * to connect. Embedding the URL here means the frontend never
 * hardcodes it — if you migrate LiveKit servers, only the backend
 * config changes.
 *
 * WHY INCLUDE roomName?
 * The LiveKit client SDK needs the room name to join.
 * The frontend gets it here rather than constructing it themselves.
 */
public record TokenResponse(
        String token,
        String livekitUrl,
        String roomName
) { }