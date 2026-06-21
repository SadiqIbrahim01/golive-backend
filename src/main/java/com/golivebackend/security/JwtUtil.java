package com.golivebackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Utility component for generating and validating JWT tokens.
 *
 * Uses JJWT 0.12.x API — generateToken(), validateToken(), extractUsername().
 *
 * The secret is injected from application.yml (backed by JWT_SECRET env var).
 * In production, JWT_SECRET must be a cryptographically strong random value
 * of at least 256 bits (32 bytes) encoded as a long string.
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        /*
         * JJWT 0.12.x requires a SecretKey, not a raw string.
         * Keys.hmacShaKeyFor() derives a HMAC-SHA key from raw bytes.
         *
         * If the secret is a plain UTF-8 string (as in dev/test), we convert
         * directly to bytes. In production, prefer a base64-encoded secret
         * and use Decoders.BASE64.decode(secret) here.
         */
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT for the given username.
     *
     * Claims included:
     *   - sub   (subject) → username
     *   - iat   (issued at) → now
     *   - exp   (expiry) → now + expirationMs
     *   - role  → "ROLE_ADMIN" (used by JwtAuthFilter to set authority)
     *
     * @param username the admin username
     * @return signed compact JWT string
     */
    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", "ROLE_ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token signature and expiry.
     *
     * @param token the raw JWT string
     * @return true if the token is valid and not expired
     */
    public boolean validateToken(String token) {
        try {
            parseAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Extracts the subject (username) from a validated token.
     *
     * Callers must call validateToken() first or accept potential JwtException.
     *
     * @param token the raw JWT string
     * @return the username stored in the subject claim
     */
    public String extractUsername(String token) {
        return parseAllClaims(token).getSubject();
    }

    /**
     * Returns the expiry Instant so the login response can include it.
     *
     * @param token the raw JWT string
     * @return expiration as Instant
     */
    public Instant extractExpiry(String token) {
        return parseAllClaims(token).getExpiration().toInstant();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Claims parseAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
