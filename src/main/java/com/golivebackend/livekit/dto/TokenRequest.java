package com.golivebackend.livekit.dto;

import com.golivebackend.livekit.model.ParticipantRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Inbound DTO for token generation requests.
 *
 * POST /api/livekit/token body:
 * {
 *   "stream_id": "550e8400-...",
 *   "role": "HOST" | "VIEWER"
 * }
 *
 * WHY ROLE IN THE REQUEST AND NOT DERIVED FROM hostKey?
 * Clean separation of concerns:
 *   - The token endpoint deals with media permissions (role)
 *   - The lifecycle endpoints deal with host identity (hostKey)
 *
 * A HOST must provide their hostKey on the token request so we can
 * verify they are the legitimate host before issuing publish permissions.
 * A VIEWER provides no key — subscribe-only tokens are safe to issue
 * to anyone who knows the streamId (which is in the public watch URL).
 */
public record TokenRequest(

        @NotNull(message = "streamId is required")
        UUID streamId,

        @NotNull(message = "role is required")
        ParticipantRole role,

        /*
         * Required when role = HOST.
         * Null when role = VIEWER — validated in service, not here,
         * because the validation is conditional on role.
         */
        String hostKey

) { }