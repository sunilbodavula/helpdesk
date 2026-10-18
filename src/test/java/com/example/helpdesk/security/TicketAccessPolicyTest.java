package com.example.helpdesk.security;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.User;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TicketAccessPolicyTest {

    private User user(Long id){
        return User.builder().id(id).role(Role.USER).build();
    }

    private Ticket ticketOf(Long creatorId, Long assigneeId){
        return Ticket.builder()
                .createdBy(user(creatorId))
                .assignedTo(assigneeId == null ? null : user(assigneeId))
                .build();
    }

    @Test
    void adminSeesEverything() {
        AuthUser admin = new AuthUser(99L, Role.ADMIN);
        assertThat(TicketAccessPolicy.canView(ticketOf(1L, 2L), admin)).isTrue();
        assertThat(TicketAccessPolicy.canView(ticketOf(1L, null), admin)).isTrue();
    }

    @Test
    void userSeesOnlyOwnTickets() {
        AuthUser userA = new AuthUser(1L, Role.USER);
        assertThat(TicketAccessPolicy.canView(ticketOf(1L, null), userA)).isTrue();
        assertThat(TicketAccessPolicy.canView(ticketOf(2L, null), userA)).isFalse();
    }


    @Test
    void agentSeesUnassignedAndOwnClaims() {
        AuthUser agent1 = new AuthUser(10L, Role.AGENT);
        assertThat(TicketAccessPolicy.canView(ticketOf(1L, null), agent1)).isTrue();
        assertThat(TicketAccessPolicy.canView(ticketOf(1L, 10L), agent1)).isTrue();
        assertThat(TicketAccessPolicy.canView(ticketOf(1L, 11L), agent1)).isFalse();
    }
}
