package com.example.helpdesk.support;

import com.example.helpdesk.model.*;
import com.example.helpdesk.security.AuthUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

public final class TestData {

    private TestData(){}

    public static User user(Long id, Role role){
        return User.builder()
                .id(id)
                .name("User " + id)
                .email("user" + id + "@test.com")
                .password("stored-hash")
                .role(role)
                .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
                .build();
    }

    public static AuthUser auth(User user){
        return new AuthUser(user.getId(), user.getRole());
    }

    public static Ticket ticket(long id, User creator, User assignee, TicketStatus status){
        return Ticket.builder()
                .id(id)
                .title("Ticket" + id)
                .description("Description" + id)
                .status(status)
                .priority(TicketPriority.MEDIUM)
                .createdBy(creator)
                .assignedTo(assignee)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }
}
