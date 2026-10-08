package com.example.helpdesk.service;


import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    @Autowired
    public TicketService(TicketRepository ticketRepository){
        this.ticketRepository = ticketRepository;
    }

    public List<Ticket> getAllTickets(){
        return ticketRepository.findAll();
    }

    public Ticket getTicketById(Long id){
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found with id " + id));
    }

    public Ticket createTicket(Ticket ticket){
        return ticketRepository.save(ticket);
    }

    public Ticket updateTicket(Long id, Ticket updatedTicket){
        Ticket existing = getTicketById(id);

        existing.setTitle(updatedTicket.getTitle());
        existing.setDescription(updatedTicket.getDescription());
        existing.setStatus(updatedTicket.getStatus());
        existing.setPriority(updatedTicket.getPriority());
        existing.setAssignedTo(updatedTicket.getAssignedTo());

        return ticketRepository.save(existing);
    }

    public void deleteTicket(Long id){
        Ticket existing = getTicketById(id);
        ticketRepository.delete(existing);
    }
}
