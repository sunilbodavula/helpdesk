package com.example.helpdesk.integration;

import com.example.helpdesk.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentIntegrationTest extends IntegrationTestBase {

    @Test
    void commentsFollowTicketVisibility() throws Exception {
        Account owner = register("owner");
        Account stranger = register("stranger");
        Account agent = registerWithRole("agent", Role.AGENT);
        long ticketId = createTicket(owner, "Visible to the owner and agents");

        addComment(owner, ticketId, "Still broken")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(owner.id().intValue()));
        addComment(agent, ticketId, "Looking into it").andExpect(status().isCreated());
        addComment(stranger, ticketId, "Can I join?").andExpect(status().isNotFound());
        listComments(stranger, ticketId).andExpect(status().isNotFound());
    }

    @Test
    void commentsAreListedOldestFirst() throws Exception {
        Account owner = register("owner");
        Account agent = registerWithRole("agent", Role.AGENT);
        long ticketId = createTicket(owner, "Order matters");

        addComment(owner, ticketId, "first").andExpect(status().isCreated());
        addComment(agent, ticketId, "second").andExpect(status().isCreated());

        listComments(owner, ticketId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("first"))
                .andExpect(jsonPath("$.data[1].content").value("second"))
                .andExpect(jsonPath("$.pagination.total").value(2));
    }

    @Test
    void closedTicketsRejectNewComments() throws Exception {
        Account owner = register("owner");
        Account admin = registerWithRole("admin", Role.ADMIN);
        long ticketId = createTicket(owner, "Soon closed");

        updateTicket(admin, ticketId, "Soon closed", "CLOSED", null).andExpect(status().isOk());
        addComment(owner, ticketId, "Too late").andExpect(status().isConflict());
    }

    @Test
    void deletingATicketAlsoDeletesItsComments() throws Exception {
        Account owner = register("owner");
        Account admin = registerWithRole("admin", Role.ADMIN);
        long ticketId = createTicket(owner, "Has comments");
        addComment(owner, ticketId, "One").andExpect(status().isCreated());
        addComment(owner, ticketId, "Two").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/tickets/" + ticketId).header(AUTH, bearer(admin)))
                .andExpect(status().isNoContent());

        Long remaining = jdbc.queryForObject(
                "select count(*) from comments where ticket_id = ?", Long.class, ticketId);
        assertThat(remaining).isZero();
    }

    @Test
    void onlyTheAuthorOrAnAdminCanDeleteAComment() throws Exception {
        Account owner = register("owner");
        Account agent = registerWithRole("agent", Role.AGENT);
        Account admin = registerWithRole("admin", Role.ADMIN);
        long ticketId = createTicket(owner, "Moderation");

        String body = addComment(agent, ticketId, "Agent note")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long commentId = objectMapper.readTree(body).get("id").asLong();

        deleteComment(owner, ticketId, commentId).andExpect(status().isForbidden());
        deleteComment(admin, ticketId, commentId).andExpect(status().isNoContent());
    }

    private ResultActions addComment(Account actor, long ticketId, String content) throws Exception {
        return mockMvc.perform(post("/api/tickets/" + ticketId + "/comments")
                .header(AUTH, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", content))));
    }

    private ResultActions listComments(Account actor, long ticketId) throws Exception {
        return mockMvc.perform(get("/api/tickets/" + ticketId + "/comments").header(AUTH, bearer(actor)));
    }

    private ResultActions deleteComment(Account actor, long ticketId, long commentId) throws Exception {
        return mockMvc.perform(delete("/api/tickets/" + ticketId + "/comments/" + commentId)
                .header(AUTH, bearer(actor)));
    }
}