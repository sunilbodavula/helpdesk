package com.example.helpdesk.repository;


import com.example.helpdesk.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    Page<Comment> findTicketById(Long ticketId, Pageable pageable);

    @Modifying
    @Query("delete from Comment c where c.ticket.id = :ticketId")
    void deleteTicketById(@Param("ticketId") Long ticketId);
}
