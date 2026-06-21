package com.golivebackend.admin;

import java.time.Instant;

/**
 * Response body returned after a successful admin login.
 *
 * token     — the signed JWT to be sent as "Authorization: Bearer <token>"
 * expiresAt — UTC timestamp of token expiry, so the client can auto-logout
 */
public record AdminLoginResponse(
        String token,
        Instant expiresAt
) {}
