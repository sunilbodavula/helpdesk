package com.example.helpdesk.service;


import com.example.helpdesk.dto.CommentCreateRequest;
import com.example.helpdesk.dto.CommentResponse;
import com.example.helpdesk.dto.PageResponse;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.mapper.CommentMapper;
import com.example.helpdesk.model.*;
import com.example.helpdesk.repository.CommentRepository;
import com.example.helpdesk.repository.UserRepository;
import com.example.helpdesk.security.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final CommentMapper commentMapper;

    public CommentService(CommentRepository commentRepository,
                          UserRepository userRepository,
                          TicketService ticketService,
                          CommentMapper commentMapper) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.ticketService = ticketService;
        this.commentMapper = commentMapper;
    }

    public PageResponse<CommentResponse> getComments(Long ticketId, Pageable pageable, AuthUser actor){
        ticketService.findVisibleTicket(ticketId, actor);

        Pageable oldestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt", "id"));

        Page<Comment> page = commentRepository.findTicketById(ticketId, oldestFirst);
        return PageResponse.of(page.map(commentMapper::toResponse));
    }

    @Transactional
    public CommentResponse addComment(Long ticketId, CommentCreateRequest request, AuthUser actor){
        Ticket ticket = ticketService.findVisibleTicket(ticketId, actor);
        if(ticket.getStatus() == TicketStatus.CLOSED){
            throw new ConflictException("Ticket is closed and no longer accepts comments");
        }

        User author = userRepository.findById(actor.id())
                .orElseThrow(() -> new BadCredentialsException("Account no longer accepts"));

        Comment comment = Comment.builder()
                .content(request.content().trim())
                .ticket(ticket)
                .author(author)
                .build();

        return commentMapper.toResponse(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(Long ticketId, Long commentId, AuthUser actor){
        ticketService.findVisibleTicket(ticketId, actor);

        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getTicket().getId().equals(ticketId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        boolean isAuthor = comment.getAuthor().getId().equals(actor.id());
        if(actor.role() != Role.ADMIN && !isAuthor){
            throw new ForbiddenException("You can only delete your own comments");
        }
        commentRepository.delete(comment);
    }
}
