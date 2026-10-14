package com.example.helpdesk.security;

import com.example.helpdesk.model.Role;

public record AuthUser(Long id, Role role) {
}
