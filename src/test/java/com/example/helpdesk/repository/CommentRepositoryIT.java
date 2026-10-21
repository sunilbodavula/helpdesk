package com.example.helpdesk.repository;

import com.example.helpdesk.integration.IntegrationTestBase;
import com.example.helpdesk.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CommentRepositoryIT extends IntegrationTestBase {

    @Autowired private CommentRepository commentRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void findByTicketId_returnsOnlyCommentsForThatTicket(){
        User author = userRepository.save(User.builder()
                .name("A").email("a@test.com").password("x").role(Role.USER).build());

        Ticket ticketOne = ticketRepository.save(Ticket.builder()
                .title("One").createdBy(author).status(TicketStatus.OPEN)
                .priority(TicketPriority.LOW).build());

        Ticket ticketTwo = ticketRepository.save(Ticket.builder()
                .title("Two").createdBy(author).status(TicketStatus.OPEN)
                .priority(TicketPriority.LOW).build());

        commentRepository.save(Comment.builder().ticket(ticketOne).author(author)
                .content("on one").build());

        commentRepository.save(Comment.builder().ticket(ticketTwo).author(author)
                .content("on two").build());

        var page = commentRepository.findByTicketId(ticketOne.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getContent()).isEqualTo("on one");

    }
}
