package com.example.helpdesk.dto;

import com.example.helpdesk.model.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketCreateRequest (
        @Schema(example = "Printer on floor 3 is jammed")
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @Schema(example = "Paper keeps getting stuck since this morning")
        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description,

        @Schema(example = "HIGH")
        TicketPriority priority,

        @Schema(description = "Agent or admin to assign. Users cannot assign. Agents may only assign themselves.",
                nullable = true)
        Long assignedToId
){}
