package com.example.helpdesk.dto;

import java.time.LocalDateTime;

public record CommentResponse (
        Long id,
        Long ticketId,
        Long authorId,
        String authorName,
        String content,
        LocalDateTime createdAt
){
}
