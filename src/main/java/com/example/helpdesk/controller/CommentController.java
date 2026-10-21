package com.example.helpdesk.controller;


import com.example.helpdesk.dto.CommentCreateRequest;
import com.example.helpdesk.dto.CommentResponse;
import com.example.helpdesk.dto.PageResponse;
import com.example.helpdesk.security.AuthUser;
import com.example.helpdesk.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Comments", description = "Comments follow the visibility of their ticket")
@RestController
@RequestMapping("/api/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService){
        this.commentService = commentService;
    }

    @Operation(summary = "List a ticket's comments, oldest first")
    @GetMapping
    public ResponseEntity<PageResponse<CommentResponse>> getComments(
            @PathVariable Long ticketId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthUser principal
            ){
        return ResponseEntity.ok(commentService.getComments(ticketId, pageable, principal));
    }

    @Operation(summary = "Add a comment (closed tickets reject new comments)")
    @PostMapping
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable Long ticketId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal AuthUser principal
    ){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(ticketId, request, principal));
    }

    @Operation(summary = "Delete a comment (its author or an admin)")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal AuthUser actor
    ){
        commentService.deleteComment(ticketId, commentId, actor);
        return ResponseEntity.noContent().build();
    }


}
