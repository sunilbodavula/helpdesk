package com.example.helpdesk.security;

import com.example.helpdesk.model.Ticket;
import org.springframework.data.jpa.domain.Specification;

import java.util.Objects;

public final class TicketAccessPolicy {

    private TicketAccessPolicy(){}

    public static boolean canView(Ticket ticket, AuthUser actor){
        return switch(actor.role()){
            case ADMIN -> true;
            case AGENT -> ticket.getAssignedTo() == null
                    || Objects.equals(ticket.getAssignedTo().getId(), actor.id());
            case USER -> ticket.getCreatedBy() != null
                    && Objects.equals(ticket.getCreatedBy().getId(), actor.id());
        };
    }

    public static Specification<Ticket> visibleTo(AuthUser actor){
        return (root, query, cb) ->
        switch (actor.role()){
            case ADMIN -> cb.conjunction();
            case AGENT -> cb.or(
                    cb.isNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), actor.id()));
            case USER -> cb.equal(root.get("createdBy").get("id"), actor.id());
        };
    }
}
