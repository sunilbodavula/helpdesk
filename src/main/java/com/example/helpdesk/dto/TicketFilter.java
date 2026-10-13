package com.example.helpdesk.dto;

import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;

public record TicketFilter(
        TicketStatus status,
        TicketPriority priority,
        Long createdById,
        Long assignedToId,
        String search
) {
}
