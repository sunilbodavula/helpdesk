package com.example.helpdesk.controller;


import com.example.helpdesk.dto.RoleChangeRequest;
import com.example.helpdesk.dto.UserResponse;
import com.example.helpdesk.security.AuthUser;
import com.example.helpdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService){
        this.userService = userService;

    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> changeRole (
            @PathVariable Long id,
            @Valid @RequestBody RoleChangeRequest request,
            @AuthenticationPrincipal AuthUser principal
            ){
        return ResponseEntity.ok(userService.changeRole(id, request.role(), principal.id()));
    }
}
