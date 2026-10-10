package com.example.helpdesk.dto;

import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;

import java.time.LocalDateTime;

public record TicketResponse (
        Long id,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Long createdById,
        String createdByName,
        Long assignedToId,
        String assignedToName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
