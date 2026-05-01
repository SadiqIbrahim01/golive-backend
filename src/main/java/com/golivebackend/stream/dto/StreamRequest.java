package com.golivebackend.stream.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Inbound DTO for stream creation requests.
 *
 * POST /api/streams body:
 * {
 *   "title": "My Gaming Stream"
 * }
 *
 * WHY RECORD AND NOT CLASS?
 * Java 16+ records are immutable data carriers — perfect for DTOs.
 * They generate: constructor, getters, equals, hashCode, toString.
 * A request object should never be mutated after deserialisation.
 *
 * Bean Validation annotations (@NotBlank, @Size) work on records
 * the same as on classes — validated when @Valid is on the
 * controller parameter.
 */
public record StreamRequest(

        /*
         * @NotBlank: rejects null, "", and "   " (whitespace only).
         * More useful than @NotNull for strings — a blank title
         * is as bad as no title.
         *
         * @Size: enforces reasonable length bounds.
         * min=3: "OK" is not a valid stream title
         * max=100: prevents absurdly long titles
         *
         * message: what the validation error says.
         * Returned to the client in the error response body.
         */
        @NotBlank(message = "Stream title must not be blank")
        @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
        String title

) { }