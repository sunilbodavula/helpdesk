package com.example.helpdesk.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}