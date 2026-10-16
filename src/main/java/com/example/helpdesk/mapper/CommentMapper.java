package com.example.helpdesk.mapper;


import com.example.helpdesk.dto.CommentCreateRequest;
import com.example.helpdesk.dto.CommentResponse;
import com.example.helpdesk.model.Comment;
import org.springframework.stereotype.Component;

@Component
public class CommentMapper {

    public CommentResponse toResponse(Comment comment){
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
