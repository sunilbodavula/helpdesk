package com.example.helpdesk.dto;

import com.example.helpdesk.model.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull(message = "role is required") Role role) {
}
