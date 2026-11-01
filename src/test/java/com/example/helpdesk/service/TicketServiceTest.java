package com.example.helpdesk.service;


import com.example.helpdesk.dto.TicketCreateRequest;
import com.example.helpdesk.dto.TicketUpdateRequest;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.TicketMapper;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repository.CommentRepository;
import com.example.helpdesk.repository.TicketRepository;
import com.example.helpdesk.repository.UserRepository;
import com.example.helpdesk.security.AuthUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest  {

    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketMapper ticketMapper;
    @Mock private CommentRepository commentRepository;

    @InjectMocks
    private TicketService ticketService;

    private User userA;
    private AuthUser actorA;

    @BeforeEach
    void setUp(){
        userA = User.builder().id(1L).name("User A").role(Role.USER).build();
        actorA = new AuthUser(1L, Role.USER);
    }

    @Test
    void createTicket_setsCreatorFromToken_notFromRequestBody() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        TicketCreateRequest request = new TicketCreateRequest("Printer jam", "desc", TicketPriority.HIGH, null);
                when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        ticketService.createTicket(request, actorA);
        verify(ticketRepository).save(argThat(t -> t.getCreatedBy().getId().equals(1L)));
    }

    @Test
    void crateTicket_rejectAssigneeFromUserRole(){
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        TicketCreateRequest request = new TicketCreateRequest("Nope", null, null, 5L);

        assertThatThrownBy(() -> ticketService.createTicket(request, actorA))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void findVisibleTicket_throwsNotFound_whenTicketHiddenFromActor(){
        User otherOwner = User.builder().id(2L).role(Role.USER).build();
        Ticket hidden =
                Ticket.builder().id(7L).createdBy(otherOwner).status(TicketStatus.OPEN).build();

        when(ticketRepository.findById(7L)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> ticketService.findVisibleTicket(7L, actorA))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTicket_asUser_cannotChangeStatus(){
        Ticket open = Ticket.builder().id(3L).createdBy(userA).status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM).build();
        when(ticketRepository.findById(3L)).thenReturn(Optional.of(open));

        TicketUpdateRequest request = new TicketUpdateRequest("title", "d", TicketStatus.IN_PROGRESS,
                TicketPriority.MEDIUM, null);

        assertThatThrownBy(() -> ticketService.updateTicket(3L, request, actorA))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateTicket_asUser_anOwnOpenTicket_allowsTitleEdit(){
        Ticket open = Ticket.builder().id(3L).createdBy(userA).status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM).build();
        when(ticketRepository.findById(3L)).thenReturn(Optional.of(open));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketUpdateRequest request = new TicketUpdateRequest("new title", "d", TicketStatus.OPEN,
                TicketPriority.MEDIUM, null);
        ticketService.updateTicket(3L, request, actorA);

        verify(ticketRepository).save(argThat(t -> t.getTitle().equals("new title")));
    }

}
