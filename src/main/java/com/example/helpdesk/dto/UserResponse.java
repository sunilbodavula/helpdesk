package com.example.helpdesk.dto;

import com.example.helpdesk.model.Role;

import java.time.LocalDateTime;

public record UserResponse(Long id, String name, String email, Role role, LocalDateTime createdAt) {}