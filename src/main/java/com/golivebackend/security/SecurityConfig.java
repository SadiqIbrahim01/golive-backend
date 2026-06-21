package com.golivebackend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Central Spring Security configuration.
 *
 * DESIGN DECISIONS:
 *
 * 1. Stateless JWT — no HTTP session is created or used.
 *    SessionCreationPolicy.STATELESS ensures Spring never touches the session.
 *
 * 2. CSRF disabled — CSRF protection is for session-cookie auth. With stateless JWT
 *    (no cookies), CSRF attacks are not possible. Disabling avoids unnecessary
 *    preflight complexity.
 *
 * 3. CORS registered here — Spring Security intercepts requests BEFORE MVC.
 *    The CorsConfigurationSource bean here is applied at the security filter layer,
 *    ensuring CORS headers are added even on 401/403 responses.
 *    The WebMvcConfigurer in CorsConfig.java handles MVC-layer CORS for
 *    non-security-intercepted paths (WebSocket upgrade, etc.).
 *
 * 4. Endpoint authorisation:
 *    - POST /admin/auth/login → public (anyone can attempt login)
 *    - GET/DELETE /admin/** → ROLE_ADMIN required
 *    - Everything else → public (existing stream/livekit/chat endpoints)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AdminUserDetailsService adminUserDetailsService;

    @Value("#{'${cors.allowed-origin}'.split(',')}")
    private String[] allowedOrigins;

    /**
     * BCryptPasswordEncoder bean — shared by SecurityConfig and AdminUserDetailsService.
     * Defined here to avoid circular dependencies (SecurityConfig ← UserDetailsService ← encoder).
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager wired with DaoAuthenticationProvider.
     * Required by AdminAuthController to authenticate login requests.
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return new ProviderManager(provider);
    }

    /**
     * CORS configuration source — registered at the security layer.
     * Mirrors the policy defined in CorsConfig.java so both MVC and
     * Security filter layers use the same rules.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Primary security filter chain.
     *
     * Rule evaluation is TOP-TO-BOTTOM — more specific rules first,
     * catch-all (permitAll) last.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Stateless — no session, no cookies
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF disabled (stateless JWT — no cookies involved)
                .csrf(AbstractHttpConfigurer::disable)

                // CORS via the bean above
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .authorizeHttpRequests(auth -> auth
                        // Admin login is public — the JWT is obtained here
                        .requestMatchers(HttpMethod.POST, "/admin/auth/login").permitAll()

                        // All other /admin/** endpoints require ROLE_ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // WebSocket handshake endpoint — must remain public
                        .requestMatchers("/ws/**").permitAll()

                        // All existing public stream and livekit endpoints
                        .anyRequest().permitAll()
                )

                // Place JWT filter before Spring's username/password filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}
