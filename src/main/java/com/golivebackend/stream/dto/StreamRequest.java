package com.golivebackend.stream.dto;

import com.golivebackend.stream.model.StreamType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StreamRequest(

        @NotBlank(message = "Stream title must not be blank")
        @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
        String title,

        /*
         * Category is optional — host may not always categorise.
         * No @NotBlank here: null and blank are both acceptable.
         * Service layer defaults to null if not provided.
         */
        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        /*
         * StreamType is optional — defaults to SCREEN_SHARE in service
         * if not provided. Keeps the API backwards compatible.
         */
        StreamType streamType

) { }