package com.example.helpdesk.repository;

import com.example.helpdesk.dto.TicketFilter;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;
import org.springframework.data.jpa.domain.Specification;

public final class TicketSpecification {

    private TicketSpecification(){}

    public static Specification<Ticket> from(TicketFilter filter){
        return Specification.where(hasStatus(filter.status()))
                .and(hasPriority(filter.priority()))
                .and(createdBy(filter.createdById()))
                .and(assignedTo(filter.assignedToId()))
                .and(titleContains(filter.search()));
    }

    private static Specification<Ticket> hasStatus(TicketStatus status){
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<Ticket> hasPriority(TicketPriority priority){
        return (root, query, cb) ->
                priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    private static Specification<Ticket> createdBy(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("createdBy").get("id"), userId);
    }

    private static Specification<Ticket> assignedTo(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("assignedTo").get("id"), userId);
    }

    private static Specification<Ticket> titleContains(String search){
        return (root, query, cb) -> {
            if(search == null) return null;
            String pattern = "%" + escapeLike(search.toLowerCase()) + "%";
            return cb.like(cb.lower(root.get("title")), pattern , '\\');
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
