package com.example.helpdesk.controller;

import com.example.helpdesk.dto.PageResponse;
import com.example.helpdesk.dto.TicketCreateRequest;
import com.example.helpdesk.dto.TicketFilter;
import com.example.helpdesk.dto.TicketResponse;
import com.example.helpdesk.dto.TicketUpdateRequest;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.security.AuthUser;
import com.example.helpdesk.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Tickets", description = "Create, view and manage tickets. What you can see depends on your role.")
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Operation(summary = "List the tickets you can see",
            description = "USER: own tickets. AGENT: unassigned tickets plus their own. ADMIN: everything. "
                    + "Page numbers start at 1. Sort with sort=createdAt,desc.")
    @GetMapping
    public ResponseEntity<PageResponse<TicketResponse>> getAllTickets(
            @Parameter(description = "Only tickets with this status")
            @RequestParam(required = false) TicketStatus status,
            @Parameter(description = "Only tickets with this priority")
            @RequestParam(required = false) TicketPriority priority,
            @Parameter(description = "Only tickets created by this user id")
            @RequestParam(required = false) Long createdById,
            @Parameter(description = "Only tickets assigned to this user id")
            @RequestParam(required = false) Long assignedToId,
            @Parameter(description = "Case-insensitive text to find in the title")
            @RequestParam(required = false) String search,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthUser principal) {

        TicketFilter filter = new TicketFilter(status, priority, createdById, assignedToId, search);
        return ResponseEntity.ok(ticketService.getTickets(filter, pageable, principal));
    }

    @Operation(summary = "Get one ticket",
            description = "Returns 404 both when the ticket does not exist and when you are not allowed to see it.")
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long id,
                                                        @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.ok(ticketService.getTicketById(id, principal));
    }

    @Operation(summary = "Create a ticket",
            description = "The creator is taken from your token. Only agents and admins may assign it.")
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@Valid @RequestBody TicketCreateRequest request,
                                                       @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ticketService.createTicket(request, principal));
    }

    @Operation(summary = "Update a ticket",
            description = "Send the full ticket. Users can only change the title and description of their own "
                    + "open tickets. Agents may assign only to themselves. Admins may assign to any agent.")
    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> updateTicket(@PathVariable Long id,
                                                       @Valid @RequestBody TicketUpdateRequest request,
                                                       @AuthenticationPrincipal AuthUser principal) {
        return ResponseEntity.ok(ticketService.updateTicket(id, request, principal));
    }

    @Operation(summary = "Delete a ticket and its comments (ADMIN only)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}