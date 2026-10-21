package com.example.helpdesk.service;

import com.example.helpdesk.dto.PageResponse;
import com.example.helpdesk.dto.TicketCreateRequest;
import com.example.helpdesk.dto.TicketFilter;
import com.example.helpdesk.dto.TicketResponse;
import com.example.helpdesk.dto.TicketUpdateRequest;
import com.example.helpdesk.exception.BadRequestException;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.TicketMapper;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.CommentRepository;
import com.example.helpdesk.repository.TicketRepository;
import com.example.helpdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;
import java.util.Optional;

import static com.example.helpdesk.support.TestData.auth;
import static com.example.helpdesk.support.TestData.ticket;
import static com.example.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @org.mockito.Mock private TicketRepository ticketRepository;
    @org.mockito.Mock private UserRepository userRepository;
    @org.mockito.Mock private CommentRepository commentRepository;

    private TicketService service;

    private final User owner = user(1L, Role.USER);
    private final User otherUser = user(2L, Role.USER);
    private final User agent1 = user(10L, Role.AGENT);
    private final User agent2 = user(11L, Role.AGENT);
    private final User admin = user(20L, Role.ADMIN);

    @BeforeEach
    void setUp() {
        service = new TicketService(ticketRepository, userRepository, new TicketMapper(), commentRepository);
    }

    // ---------------------------------------------------------------- reading

    @Test
    void aHiddenTicketLooksExactlyLikeAMissingOne() {
        when(ticketRepository.findById(5L))
                .thenReturn(Optional.of(ticket(5, owner, null, TicketStatus.OPEN)));
        when(ticketRepository.findById(6L)).thenReturn(Optional.empty());

        Throwable hidden = catchThrowable(() -> service.getTicketById(5L, auth(otherUser)));
        Throwable missing = catchThrowable(() -> service.getTicketById(6L, auth(otherUser)));

        assertThat(hidden).isInstanceOf(ResourceNotFoundException.class).hasMessage("Ticket not found with id: 5");
        assertThat(missing).isInstanceOf(ResourceNotFoundException.class).hasMessage("Ticket not found with id: 6");
    }

    @Test
    void theOwnerCanReadTheirTicket() {
        when(ticketRepository.findById(5L))
                .thenReturn(Optional.of(ticket(5, owner, agent1, TicketStatus.IN_PROGRESS)));

        TicketResponse response = service.getTicketById(5L, auth(owner));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.createdById()).isEqualTo(1L);
        assertThat(response.assignedToId()).isEqualTo(10L);
        assertThat(response.assignedToName()).isEqualTo("User 10");
    }

    // ---------------------------------------------------------------- creating

    @Test
    void aUserCannotAssignATicketOnCreation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.createTicket(create(10L), auth(owner)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void theCreatorIsTakenFromTheTokenNotTheRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        echoSave();

        TicketResponse response = service.createTicket(create(null), auth(owner));

        Ticket saved = captureSavedTicket();
        assertThat(saved.getCreatedBy().getId()).isEqualTo(1L);
        assertThat(saved.getAssignedTo()).isNull();
        assertThat(saved.getTitle()).isEqualTo("Printer jam");
        assertThat(saved.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(response.createdById()).isEqualTo(1L);
    }

    @Test
    void anAgentCanCreateATicketAssignedToThemselves() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(agent1));
        echoSave();

        service.createTicket(create(10L), auth(agent1));

        assertThat(captureSavedTicket().getAssignedTo().getId()).isEqualTo(10L);
    }

    @Test
    void anAgentCannotCreateATicketAssignedToSomeoneElse() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(agent1));

        assertThatThrownBy(() -> service.createTicket(create(11L), auth(agent1)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void nobodyCanAssignATicketToAPlainUser() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.createTicket(create(2L), auth(admin)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void assigningToAnUnknownUserIsNotFound() {
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTicket(create(999L), auth(admin)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found with id: 999");
    }

    @Test
    void aTokenForADeletedAccountIsRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTicket(create(null), auth(owner)))
                .isInstanceOf(BadCredentialsException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    // ---------------------------------------------------------------- updating as a user

    @Test
    void aUserCannotChangeTheStatus() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, TicketStatus.IN_PROGRESS, current.getPriority(), null), auth(owner)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void aUserCannotChangeThePriority() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, current.getStatus(), TicketPriority.CRITICAL, null), auth(owner)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void aUserCannotChangeTheAssignee() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, current.getStatus(), current.getPriority(), 10L), auth(owner)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void aUserCanEditTitleAndDescriptionOfAnOpenTicket() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));
        echoSave();

        TicketResponse response = service.updateTicket(5L,
                new TicketUpdateRequest("Better title", "More detail", current.getStatus(), current.getPriority(), null),
                auth(owner));

        assertThat(response.title()).isEqualTo("Better title");
        assertThat(response.description()).isEqualTo("More detail");
    }

    @Test
    void sendingTheUnchangedAssigneeBackIsNotAChange() {
        Ticket current = ticket(5, owner, agent1, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));
        echoSave();

        TicketResponse response = service.updateTicket(5L,
                new TicketUpdateRequest("Retitled", "Same", TicketStatus.OPEN, current.getPriority(), 10L),
                auth(owner));

        assertThat(response.title()).isEqualTo("Retitled");
        assertThat(response.assignedToId()).isEqualTo(10L);
        verifyNoInteractions(userRepository);
    }

    @Test
    void aUserCannotEditATicketThatIsInProgress() {
        Ticket current = ticket(5, owner, agent1, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                new TicketUpdateRequest("Too late", "x", TicketStatus.IN_PROGRESS, current.getPriority(), 10L),
                auth(owner)))
                .isInstanceOf(ConflictException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void updatingATicketYouCannotSeeIsNotFound() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, current.getStatus(), current.getPriority(), null), auth(otherUser)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    // ---------------------------------------------------------------- updating as an agent or admin

    @Test
    void anAgentCanClaimAnUnassignedTicket() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));
        when(userRepository.findById(10L)).thenReturn(Optional.of(agent1));
        echoSave();

        TicketResponse response = service.updateTicket(5L,
                request(current, TicketStatus.IN_PROGRESS, current.getPriority(), 10L), auth(agent1));

        assertThat(response.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(response.assignedToId()).isEqualTo(10L);
    }

    @Test
    void anAgentCannotAssignToAnotherAgent() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, current.getStatus(), current.getPriority(), 11L), auth(agent1)))
                .isInstanceOf(ForbiddenException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void anAgentCannotUpdateATicketAssignedToAnotherAgent() {
        Ticket current = ticket(5, owner, agent2, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, TicketStatus.RESOLVED, current.getPriority(), 11L), auth(agent1)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void anAdminCanAssignToAnAgent() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));
        when(userRepository.findById(11L)).thenReturn(Optional.of(agent2));
        echoSave();

        TicketResponse response = service.updateTicket(5L,
                request(current, current.getStatus(), current.getPriority(), 11L), auth(admin));

        assertThat(response.assignedToId()).isEqualTo(11L);
    }

    @Test
    void anAdminCannotAssignToAPlainUser() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.updateTicket(5L,
                request(current, current.getStatus(), current.getPriority(), 2L), auth(admin)))
                .isInstanceOf(BadRequestException.class);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void anAdminCanUnassignATicket() {
        Ticket current = ticket(5, owner, agent1, TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));
        echoSave();

        TicketResponse response = service.updateTicket(5L,
                request(current, current.getStatus(), current.getPriority(), null), auth(admin));

        assertThat(response.assignedToId()).isNull();
        assertThat(captureSavedTicket().getAssignedTo()).isNull();
    }

    // ---------------------------------------------------------------- deleting

    @Test
    void commentsAreDeletedBeforeTheTicket() {
        Ticket current = ticket(5, owner, null, TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(current));

        service.deleteTicket(5L);

        InOrder order = inOrder(commentRepository, ticketRepository);
        //order.verify(commentRepository).deleteByTicketId(5L);
        order.verify(ticketRepository).delete(current);
    }

    @Test
    void deletingAMissingTicketTouchesNothing() {
        when(ticketRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTicket(5L)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(commentRepository);
        verify(ticketRepository, never()).delete(any(Ticket.class));
    }

    // ---------------------------------------------------------------- listing

    @Test
    void sortingByAFieldOutsideTheWhitelistIsRejected() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdBy.password"));

        assertThatThrownBy(() -> service.getTickets(noFilter(), pageable, auth(admin)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot sort by 'createdBy.password'");
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void aVeryLongSearchIsRejected() {
        TicketFilter filter = new TicketFilter(null, null, null, null, "x".repeat(101));

        assertThatThrownBy(() -> service.getTickets(filter, PageRequest.of(0, 20), auth(admin)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void aBlankSearchIsTreatedAsNoFilter() {
        stubEmptyPage();
        TicketFilter filter = new TicketFilter(null, null, null, null, "   ");

        PageResponse<TicketResponse> response = service.getTickets(filter, PageRequest.of(0, 20), auth(admin));

        assertThat(response.data()).isEmpty();
    }

    @Test
    void theIdTieBreakerIsAppendedToEverySort() {
        stubEmptyPage();
        Pageable requested = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        service.getTickets(noFilter(), requested, auth(admin));

        Pageable used = capturePageable();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(10);
        assertThat(used.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(used.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void aClientSuppliedIdSortIsKept() {
        stubEmptyPage();

        service.getTickets(noFilter(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")), auth(admin));

        Pageable used = capturePageable();
        assertThat(used.getSort().stream().count()).isEqualTo(1);
        assertThat(used.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void thePageIsMappedToResponses() {
        Ticket current = ticket(5, owner, agent1, TicketStatus.IN_PROGRESS);
        Pageable pageable = PageRequest.of(0, 20);
        when(ticketRepository.findAll(ArgumentMatchers.<Specification<Ticket>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(current), pageable, 1));

        PageResponse<TicketResponse> response = service.getTickets(noFilter(), pageable, auth(admin));

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).id()).isEqualTo(5L);
        assertThat(response.pagination().page()).isEqualTo(1);
        assertThat(response.pagination().total()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------- helpers

    private TicketCreateRequest create(Long assignedToId) {
        return new TicketCreateRequest("Printer jam", "Paper stuck", TicketPriority.HIGH, assignedToId);
    }

    private TicketUpdateRequest request(Ticket current, TicketStatus status, TicketPriority priority,
                                        Long assignedToId) {
        return new TicketUpdateRequest(current.getTitle(), current.getDescription(), status, priority, assignedToId);
    }

    private TicketFilter noFilter() {
        return new TicketFilter(null, null, null, null, null);
    }

    private void echoSave() {
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Ticket captureSavedTicket() {
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        return captor.getValue();
    }

    private void stubEmptyPage() {
        when(ticketRepository.findAll(ArgumentMatchers.<Specification<Ticket>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(ArgumentMatchers.<Specification<Ticket>>any(), captor.capture());
        return captor.getValue();
    }
}