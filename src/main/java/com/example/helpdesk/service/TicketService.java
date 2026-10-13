package com.example.helpdesk.service;


import com.example.helpdesk.dto.*;
import com.example.helpdesk.exception.BadRequestException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.TicketMapper;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.TicketRepository;
import com.example.helpdesk.repository.TicketSpecification;
import com.example.helpdesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

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

    public PageResponse<TicketResponse> getTickets(TicketFilter filter, Pageable pageble){
        Page<Ticket> page = ticketRepository.findAll(
                TicketSpecification.from(cleanSearch(filter)),
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



    public TicketResponse getTicketById(Long id){
        return ticketMapper.toResponse(findTicket(id));
    }

    @Transactional
    public TicketResponse createTicket(TicketCreateRequest request){
        User creator = findUser(request.createdById());
        User assignee = request.assignedToId() != null ? findUser(request.assignedToId()) : null;

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
    public TicketResponse updateTicket(Long id, TicketUpdateRequest request){
        Ticket ticket = findTicket(id);
        User assignee = request.assignedToId() != null ? findUser(request.assignedToId()) : null;

        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(request.status());
        ticket.setPriority(request.priority());
        ticket.setAssignedTo(assignee);

        return ticketMapper.toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public void deleteTicket(Long id){
        ticketRepository.delete(findTicket(id));
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