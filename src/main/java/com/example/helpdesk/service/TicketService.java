package com.example.helpdesk.service;


import com.example.helpdesk.dto.*;
import com.example.helpdesk.exception.BadRequestException;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.TicketMapper;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.TicketRepository;
import com.example.helpdesk.repository.TicketSpecification;
import com.example.helpdesk.repository.UserRepository;
import com.example.helpdesk.security.AuthUser;
import com.example.helpdesk.security.TicketAccessPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketMapper ticketMapper;

    @Autowired
    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            TicketMapper ticketMapper
    ){
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.ticketMapper = ticketMapper;
    }

    private static final List<String> SORTABLE_FIELDS =
            List.of("id", "title", "status", "priority", "createdAt", "updatedAt");

    private static final int MAX_SEARCH_LENGTH = 100;

    public PageResponse<TicketResponse> getTickets(TicketFilter filter, Pageable pageble, AuthUser actor){
        Specification<Ticket> spec = TicketSpecification.from(cleanSearch(filter))
                .and(TicketAccessPolicy.visibleTo(actor));
        Page<Ticket> page = ticketRepository.findAll(
                spec,
                safePageable(pageble)
        );
        return PageResponse.of(page.map(ticketMapper::toResponse));
    }

    private Pageable safePageable(Pageable pageable){
        for (Sort.Order order : pageable.getSort()){
            if(!SORTABLE_FIELDS.contains(order.getProperty())){
                throw new BadRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed: " + String.join(", ", SORTABLE_FIELDS));
            }
        }
        Sort sort = pageable.getSort();
        if(sort.getOrderFor("id") == null){
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private TicketFilter cleanSearch(TicketFilter filter){
        String search = filter.search() == null ? null : filter.search().trim();
        if(search != null && search.isEmpty()){
            search = null;
        }
        if(search != null && search.length() > MAX_SEARCH_LENGTH){
            throw new BadRequestException("search must be at most " + MAX_SEARCH_LENGTH + " characters");
        }
        return new TicketFilter(filter.status(), filter.priority(),
                filter.createdById(), filter.assignedToId(), search);
    }



    public TicketResponse getTicketById(Long id, AuthUser actor){
        return ticketMapper.toResponse(findVisibleTicket(id, actor));
    }

    @Transactional
    public TicketResponse createTicket(TicketCreateRequest request, AuthUser actor){
        User creator = userRepository.findById(actor.id())
                .orElseThrow(() -> new BadCredentialsException("Account no longer exists"));
        User assignee = resolveAssignee(request.assignedToId(), actor);

        Ticket ticket = Ticket.builder()
                .title(request.title())
                .description(request.description())
                .priority(request.priority())
                .createdBy(creator)
                .assignedTo(assignee)
                .build();

        return ticketMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse updateTicket(Long id, TicketUpdateRequest request, AuthUser actor){
        Ticket ticket = findVisibleTicket(id, actor);

        Long currentAssigneeId = ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId();
        boolean assigneeChanged = !Objects.equals(request.assignedToId(), currentAssigneeId);
        boolean statusChanged = request.status() != ticket.getStatus();
        boolean priorityChanged = request.priority() != ticket.getPriority();

        if(actor.role() == Role.USER){
            if(statusChanged || priorityChanged || assigneeChanged){
                throw new ForbiddenException("Only AGENTS and ADMINS can change status, priority or assignee");
            }
            if(ticket.getStatus() != TicketStatus.OPEN){
                throw new ConflictException("Only open tickets can be edited");
            }
        }

        User assignee = assigneeChanged ? resolveAssignee(request.assignedToId(), actor) : ticket.getAssignedTo();

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(request.status());
        ticket.setPriority(request.priority());
        ticket.setAssignedTo(assignee);

        return ticketMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public void deleteTicket(Long id){
        Ticket ticket = ticketRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticketRepository.delete(findTicket(id));
    }

    private Ticket findVisibleTicket(Long id, AuthUser actor){
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        if(!TicketAccessPolicy.canView(ticket, actor)){
            throw new ResourceNotFoundException("Ticket", id);
        }
        return ticket;
    }

    private User resolveAssignee(Long assigneeId, AuthUser actor){
        if(assigneeId == null){
            return null;
        }
        if(actor.role() == Role.USER){
            throw new ForbiddenException("Only agents and admins can assign tickets");
        }
        if(actor.role() == Role.AGENT && !assigneeId.equals(actor.id())){
            throw new ForbiddenException("Agents can only assign tickets to themselves");
        }

        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", assigneeId));
        if(assignee.getRole() == Role.USER){
            throw new BadRequestException("Tickets can only assigned to agents or admins");
        }

        return assignee;
    }

    private Ticket findTicket(Long id){
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    private User findUser(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}

//