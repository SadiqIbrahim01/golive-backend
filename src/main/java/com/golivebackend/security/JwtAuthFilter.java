package com.golivebackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that extracts and validates a JWT Bearer token on every request.
 *
 * Runs ONCE per request (OncePerRequestFilter) — no double-processing.
 *
 * FLOW:
 *   1. Read Authorization header.
 *   2. If it starts with "Bearer ", extract the token.
 *   3. Validate token signature and expiry via JwtUtil.
 *   4. If valid — populate SecurityContextHolder with a ROLE_ADMIN authentication.
 *   5. Pass the request downstream regardless (Spring Security's filter chain
 *      handles 403 if the endpoint requires auth and no authentication was set).
 *
 * WHY NOT SET 401 HERE DIRECTLY?
 * Filters should be thin — they set or don't set authentication.
 * The SecurityConfig.authorizeHttpRequests() rule returns 403/401 as appropriate
 * when authentication is missing on protected paths. Single responsibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // strip "Bearer " prefix

            if (jwtUtil.validateToken(token)) {
                String username = jwtUtil.extractUsername(token);

                /*
                 * Construct a fully-authenticated token.
                 * Credentials are null — we don't need them post-validation.
                 * Authority is ROLE_ADMIN — hardcoded because this system
                 * has exactly one admin role. No lookup needed.
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT authenticated user: {}", username);
            } else {
                log.warn("JWT validation failed for request: {} {}", request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
