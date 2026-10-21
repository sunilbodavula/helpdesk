package com.example.helpdesk.integration;

import com.example.helpdesk.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketAccessIntegrationTest extends IntegrationTestBase {

    @Test
    void aUserSeesOnlyTheirOwnTickets() throws Exception {
        Account userA = register("usera");
        Account userB = register("userb");
        long ticketId = createTicket(userA, "Printer jam");

        assertThat(visibleTicketIds(userA)).contains(ticketId);
        assertThat(visibleTicketIds(userB)).doesNotContain(ticketId);

        // A hidden ticket looks exactly like a missing one.
        mockMvc.perform(get("/api/tickets/" + ticketId).header(AUTH, bearer(userB)))
                .andExpect(status().isNotFound());
        updateTicket(userB, ticketId, "Hijacked", "OPEN", null)
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanDeleteTickets() throws Exception {
        Account user = register("owner");
        Account admin = registerWithRole("admin", Role.ADMIN);
        long ticketId = createTicket(user, "To be deleted");

        mockMvc.perform(delete("/api/tickets/" + ticketId).header(AUTH, bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/tickets/" + ticketId).header(AUTH, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tickets/" + ticketId).header(AUTH, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aUserCanEditTheTitleButNotStatusOrAssignee() throws Exception {
        Account user = register("editor");
        Account agent = registerWithRole("agent", Role.AGENT);
        long ticketId = createTicket(user, "Original title");

        updateTicket(user, ticketId, "Better title", "OPEN", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Better title"));
        updateTicket(user, ticketId, "Better title", "IN_PROGRESS", null)
                .andExpect(status().isForbidden());
        updateTicket(user, ticketId, "Better title", "OPEN", agent.id())
                .andExpect(status().isForbidden());
    }

    @Test
    void anAgentSeesUnassignedTicketsAndTheirOwnButNotOthers() throws Exception {
        Account owner = register("owner");
        Account agent1 = registerWithRole("agent1", Role.AGENT);
        Account agent2 = registerWithRole("agent2", Role.AGENT);
        long ticketId = createTicket(owner, "Needs an agent");

        assertThat(visibleTicketIds(agent1)).contains(ticketId);
        assertThat(visibleTicketIds(agent2)).contains(ticketId);

        updateTicket(agent1, ticketId, "Needs an agent", "IN_PROGRESS", agent1.id())
                .andExpect(status().isOk());

        assertThat(visibleTicketIds(agent1)).contains(ticketId);
        assertThat(visibleTicketIds(agent2)).doesNotContain(ticketId);
        mockMvc.perform(get("/api/tickets/" + ticketId).header(AUTH, bearer(agent2)))
                .andExpect(status().isNotFound());
    }

    @Test
    void assignmentRulesDependOnTheRole() throws Exception {
        Account owner = register("owner");
        Account plainUser = register("plain");
        Account agent1 = registerWithRole("agent1", Role.AGENT);
        Account agent2 = registerWithRole("agent2", Role.AGENT);
        Account admin = registerWithRole("admin", Role.ADMIN);
        long ticketId = createTicket(owner, "Assign me");

        // An agent may only claim a ticket for themselves.
        updateTicket(agent1, ticketId, "Assign me", "OPEN", agent2.id())
                .andExpect(status().isForbidden());
        // Nobody can assign a ticket to a plain user.
        updateTicket(admin, ticketId, "Assign me", "OPEN", plainUser.id())
                .andExpect(status().isBadRequest());
        // An admin can assign to any agent.
        updateTicket(admin, ticketId, "Assign me", "OPEN", agent2.id())
                .andExpect(status().isOk());
    }

    @Test
    void roleChangesAreAdminOnlyAndNeverOnYourself() throws Exception {
        Account admin = registerWithRole("admin", Role.ADMIN);
        Account user = register("promotee");

        patchRole(user, admin.id(), "AGENT").andExpect(status().isForbidden());
        patchRole(admin, admin.id(), "USER").andExpect(status().isForbidden());
        patchRole(admin, user.id(), "SUPERUSER").andExpect(status().isBadRequest());
        patchRole(admin, user.id(), "AGENT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AGENT"));
    }
}