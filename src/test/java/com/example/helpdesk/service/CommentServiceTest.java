package com.example.helpdesk.service;

import com.example.helpdesk.dto.CommentCreateRequest;
import com.example.helpdesk.dto.CommentResponse;
import com.example.helpdesk.dto.PageResponse;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.CommentMapper;
import com.example.helpdesk.model.Comment;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.CommentRepository;
import com.example.helpdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.helpdesk.support.TestData.auth;
import static com.example.helpdesk.support.TestData.ticket;
import static com.example.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketService ticketService;

    private CommentService service;

    private final User owner = user(1L, Role.USER);
    private final User agent = user(10L, Role.AGENT);
    private final User admin = user(20L, Role.ADMIN);
    private final Ticket openTicket = ticket(5, owner, null, TicketStatus.OPEN);

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, userRepository, ticketService, new CommentMapper());
    }

    @Test
    void commentingOnAHiddenTicketIsNotFound() {
        when(ticketService.findVisibleTicket(5L, auth(agent))).thenThrow(new ResourceNotFoundException("Ticket", 5L));

        assertThatThrownBy(() -> service.addComment(5L, new CommentCreateRequest("hi"), auth(agent)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(commentRepository, userRepository);
    }

    @Test
    void aClosedTicketRejectsNewComments() {
        when(ticketService.findVisibleTicket(5L, auth(owner)))
                .thenReturn(ticket(5, owner, null, TicketStatus.CLOSED));

        assertThatThrownBy(() -> service.addComment(5L, new CommentCreateRequest("late"), auth(owner)))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(commentRepository, userRepository);
    }

    @Test
    void aNewCommentIsTrimmedAndAttributedToTheLoggedInUser() {
        when(ticketService.findVisibleTicket(5L, auth(owner))).thenReturn(openTicket);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CommentResponse response = service.addComment(5L, new CommentCreateRequest("  Still broken  "), auth(owner));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("Still broken");
        assertThat(saved.getValue().getAuthor().getId()).isEqualTo(1L);
        assertThat(saved.getValue().getTicket().getId()).isEqualTo(5L);
        assertThat(response.authorName()).isEqualTo("User 1");
        assertThat(response.ticketId()).isEqualTo(5L);
    }

    @Test
    void aTokenForADeletedAccountCannotComment() {
        when(ticketService.findVisibleTicket(5L, auth(owner))).thenReturn(openTicket);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComment(5L, new CommentCreateRequest("hi"), auth(owner)))
                .isInstanceOf(BadCredentialsException.class);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void anAuthorCanDeleteTheirOwnComment() {
        Comment comment = comment(9, openTicket, agent);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));

        service.deleteComment(5L, 9L, auth(agent));

        verify(commentRepository).delete(comment);
    }

    @Test
    void aUserCannotDeleteSomeoneElsesComment() {
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment(9, openTicket, agent)));

        assertThatThrownBy(() -> service.deleteComment(5L, 9L, auth(owner)))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void anAdminCanDeleteAnyComment() {
        Comment comment = comment(9, openTicket, agent);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));

        service.deleteComment(5L, 9L, auth(admin));

        verify(commentRepository).delete(comment);
    }

    @Test
    void aCommentIdFromAnotherTicketIsNotFound() {
        Ticket other = ticket(6, owner, null, TicketStatus.OPEN);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment(9, other, agent)));

        assertThatThrownBy(() -> service.deleteComment(5L, 9L, auth(admin)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Comment not found with id: 9");
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void deletingAMissingCommentIsNotFound() {
        when(commentRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteComment(5L, 9L, auth(admin)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingACommentOnAHiddenTicketNeverTouchesTheComments() {
        when(ticketService.findVisibleTicket(5L, auth(agent))).thenThrow(new ResourceNotFoundException("Ticket", 5L));

        assertThatThrownBy(() -> service.deleteComment(5L, 9L, auth(agent)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void listingCommentsOnAHiddenTicketNeverTouchesTheComments() {
        when(ticketService.findVisibleTicket(5L, auth(agent))).thenThrow(new ResourceNotFoundException("Ticket", 5L));

        assertThatThrownBy(() -> service.getComments(5L, PageRequest.of(0, 20), auth(agent)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void commentsAreAlwaysListedOldestFirstWhateverTheClientAsks() {
        Comment first = comment(9, openTicket, agent);
        Pageable requested = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(commentRepository.findByTicketId(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(2, 10), 21));

        PageResponse<CommentResponse> response = service.getComments(5L, requested, auth(owner));

        ArgumentCaptor<Pageable> used = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByTicketId(eq(5L), used.capture());
        assertThat(used.getValue().getPageNumber()).isEqualTo(2);
        assertThat(used.getValue().getPageSize()).isEqualTo(10);
        assertThat(used.getValue().getSort().stream().map(Sort.Order::getProperty))
                .containsExactly("createdAt", "id");
        assertThat(used.getValue().getSort().stream().map(Sort.Order::getDirection))
                .containsOnly(Sort.Direction.ASC);
        assertThat(response.data()).hasSize(1);
        assertThat(response.pagination().page()).isEqualTo(3);
    }

    private Comment comment(long id, Ticket onTicket, User author) {
        return Comment.builder()
                .id(id)
                .content("Comment " + id)
                .ticket(onTicket)
                .author(author)
                .createdAt(LocalDateTime.of(2026, 1, 1, 11, 0))
                .build();
    }
}