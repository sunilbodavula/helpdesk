package com.example.helpdesk.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> data, Pagination pagination) {

    public record Pagination(int page, int limit, long total, int totalPages){

    }

    public static <T> PageResponse<T> of(Page<T> page){
        return new PageResponse<>(
                page.getContent(),
                new Pagination(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages())
        );
    }
}
