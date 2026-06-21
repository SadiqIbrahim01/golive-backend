package com.golivebackend.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the admin login endpoint.
 *
 * Record — immutable, no boilerplate.
 * Validation annotations enforce that neither field is blank
 * before the controller even processes the request.
 */
public record AdminLoginRequest(
        @NotBlank(message = "Username must not be blank")
        String username,

        @NotBlank(message = "Password must not be blank")
        String password
) {}
