package com.example.helpdesk.controller;


import com.example.helpdesk.dto.*;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.security.AuthUser;
import com.example.helpdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    @Autowired
    public TicketController(TicketService ticketService){
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TicketResponse>> getAllTickets(
            @RequestParam(required = false)TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(required = false) Long createdById,
            @RequestParam(required = false) Long assignedToId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthUser principal
            ){
        TicketFilter filter = new TicketFilter(status, priority, createdById, assignedToId, search);
        return ResponseEntity.ok(ticketService.getTickets(filter, pageable, principal));

    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long id,
                                                        @AuthenticationPrincipal AuthUser principal){
        return ResponseEntity.ok(ticketService.getTicketById(id, principal));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody TicketCreateRequest request,
                                                       @AuthenticationPrincipal AuthUser principal){
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                       @Valid @RequestBody TicketUpdateRequest request,
                                                       @AuthenticationPrincipal AuthUser principal){
        return ResponseEntity.ok(ticketService.updateTicket(id, request, principal));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id){
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }

}
