package com.golivebackend.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.golivebackend.security.JwtUtil;

/**
 * Handles admin authentication.
 *
 * POST /admin/auth/login — public endpoint.
 * Validates credentials via Spring Security's AuthenticationManager,
 * then generates and returns a JWT on success.
 *
 * WHY AuthenticationManager AND NOT MANUAL COMPARISON?
 * AuthenticationManager delegates to DaoAuthenticationProvider, which:
 *   - Loads UserDetails via AdminUserDetailsService
 *   - Compares the submitted password against the stored bcrypt hash
 *   - Throws BadCredentialsException on mismatch
 * This keeps credential validation in one canonical place and follows
 * Spring Security's intended extension points.
 */
@Slf4j
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    /**
     * Authenticates the admin and returns a JWT on success.
     *
     * @param request body containing username and password
     * @return 200 with {token, expiresAt} on success
     *         401 if credentials are wrong
     */
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        log.info("Admin login attempt for username: {}", request.username());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException ex) {
            log.warn("Failed admin login attempt for username: {}", request.username());
            return ResponseEntity.status(401).build();
        }

        String token = jwtUtil.generateToken(request.username());
        AdminLoginResponse response = new AdminLoginResponse(
                token,
                jwtUtil.extractExpiry(token)
        );

        log.info("Admin login successful for username: {}", request.username());
        return ResponseEntity.ok(response);
    }
}
