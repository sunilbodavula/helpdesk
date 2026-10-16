package com.example.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank(message = "content is required")
        @Size(max = 5000, message = "content must be at most 5000 characters")
        String content
) {
}
