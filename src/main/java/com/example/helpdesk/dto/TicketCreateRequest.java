package com.example.helpdesk.dto;

import com.example.helpdesk.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TicketCreateRequest (
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @Size(max = 5000, message = "description must be at most 5000 characters")
        String description,

        TicketPriority priority,

        @NotNull(message = "createdById is required")
        Long createdById,

        Long assignedToId
){}
