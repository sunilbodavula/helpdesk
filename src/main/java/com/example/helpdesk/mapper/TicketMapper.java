package com.example.helpdesk.mapper;


import com.example.helpdesk.dto.TicketResponse;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.User;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    public TicketResponse toResponse(Ticket ticket){
        User creator = ticket.getCreatedBy();
        User assignee = ticket.getAssignedTo();

        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                creator.getId(),
                creator.getName(),
                assignee != null ? assignee.getId() : null,
                assignee != null ? assignee.getName() : null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
