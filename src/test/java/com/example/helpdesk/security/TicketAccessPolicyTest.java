package com.example.helpdesk.security;

import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import org.junit.jupiter.api.Test;

import static com.example.helpdesk.support.TestData.auth;
import static com.example.helpdesk.support.TestData.ticket;
import static com.example.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;

class TicketAccessPolicyTest {

    private final User owner = user(1L, Role.USER);
    private final User otherUser = user(2L, Role.USER);
    private final User agent1 = user(10L, Role.AGENT);
    private final User agent2 = user(11L, Role.AGENT);
    private final User admin = user(20L, Role.ADMIN);

    @Test
    void anAdminSeesEveryTicket() {
        assertThat(canView(ticket(1, owner, null, TicketStatus.OPEN), admin)).isTrue();
        assertThat(canView(ticket(2, owner, agent1, TicketStatus.IN_PROGRESS), admin)).isTrue();
    }

    @Test
    void aUserSeesOnlyTicketsTheyCreated() {
        assertThat(canView(ticket(1, owner, null, TicketStatus.OPEN), owner)).isTrue();
        assertThat(canView(ticket(2, otherUser, null, TicketStatus.OPEN), owner)).isFalse();
    }

    @Test
    void aCreatorStillSeesTheirTicketAfterItIsAssigned() {
        assertThat(canView(ticket(1, owner, agent1, TicketStatus.IN_PROGRESS), owner)).isTrue();
    }

    @Test
    void beingAssignedDoesNotGiveAUserVisibility() {
        // Assigning to a plain user is blocked elsewhere, but the rule itself must not depend on that.
        assertThat(canView(ticket(1, otherUser, owner, TicketStatus.OPEN), owner)).isFalse();
    }

    @Test
    void aTicketWithoutACreatorIsNotVisibleToAUser() {
        assertThat(canView(ticket(1, null, null, TicketStatus.OPEN), owner)).isFalse();
    }

    @Test
    void anAgentSeesUnassignedTickets() {
        assertThat(canView(ticket(1, owner, null, TicketStatus.OPEN), agent1)).isTrue();
    }

    @Test
    void anAgentSeesTicketsAssignedToThem() {
        assertThat(canView(ticket(1, owner, agent1, TicketStatus.IN_PROGRESS), agent1)).isTrue();
    }

    @Test
    void anAgentDoesNotSeeTicketsAssignedToAnotherAgent() {
        assertThat(canView(ticket(1, owner, agent2, TicketStatus.IN_PROGRESS), agent1)).isFalse();
    }

    @Test
    void anAgentDoesNotSeeATicketTheyCreatedOnceItIsAssignedElsewhere() {
        // Agent visibility ignores who created the ticket. Change this test only if the policy changes.
        assertThat(canView(ticket(1, agent1, agent2, TicketStatus.IN_PROGRESS), agent1)).isFalse();
    }

    private boolean canView(Ticket ticket, User viewer) {
        return TicketAccessPolicy.canView(ticket, auth(viewer));
    }
}