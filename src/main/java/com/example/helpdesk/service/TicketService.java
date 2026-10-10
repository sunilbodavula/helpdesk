package com.example.helpdesk.service;


import com.example.helpdesk.dto.TicketCreateRequest;
import com.example.helpdesk.dto.TicketResponse;
import com.example.helpdesk.dto.TicketUpdateRequest;
import com.example.helpdesk.mapper.TicketMapper;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.TicketRepository;
import com.example.helpdesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    public List<TicketResponse> getAllTickets(){
        return ticketRepository.findAll().stream()
                .map(ticketMapper::toResponse)
                .toList();
    }

    public TicketResponse getTicketById(Long id){
        return ticketMapper.toResponse(findTicket(id));
    }

    @Transactional
    public TicketResponse createTicket(TicketCreateRequest request){
        System.out.println("CREATE REQUEST: " + request);
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
                .orElseThrow(() -> new RuntimeException("Ticket not found with id: " + id));
    }

    private User findUser(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }
}

//