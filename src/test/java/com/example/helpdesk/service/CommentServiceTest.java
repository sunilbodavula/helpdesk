package com.example.helpdesk.service;


import com.example.helpdesk.dto.CommentCreateRequest;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.CommentMapper;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repository.CommentRepository;
import com.example.helpdesk.repository.UserRepository;
import com.example.helpdesk.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketService ticketService;
    @Mock private CommentMapper commentMapper;

    @InjectMocks
    private CommentService commentService;

    @Test
    void addComment_onClosedTicket_throwsConflict(){
        Ticket closed = Ticket.builder().id(1L).status(TicketStatus.CLOSED).build();
        AuthUser actor = new AuthUser(5L, Role.USER);
        when(ticketService.findVisibleTicket(1L, actor)).thenReturn(closed);

        assertThatThrownBy(() -> commentService.addComment(1L, new CommentCreateRequest("late"),
                actor))
                .isInstanceOf(ConflictException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_byNonAuthorNonAdmin_isForbidden(){
        AuthUser actor = new AuthUser(5L, Role.USER);
        Ticket ticket = Ticket.builder().id(1L).build();
        when(ticketService.findVisibleTicket(1L, actor)).thenReturn(ticket);

        User author = User.builder().id(99L).build();
        Comment comment = Comment.builder().id(10L).ticket(ticket).author(author).build();
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L, actor))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteComment_withMismatchedTicketId_isNotFound(){
        AuthUser actor = new AuthUser(5L, Role.ADMIN);
        Ticket ticket = Ticket.builder().id(1L).build();
        when(ticketService.findVisibleTicket(1L, actor)).thenReturn(ticket);

        Ticket otherTicket = Ticket.builder().id(2L).build();
        Comment comment = Comment.builder().id(10L).ticket(otherTicket).build();
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L, actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
