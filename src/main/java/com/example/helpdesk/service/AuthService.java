package com.example.helpdesk.service;

import com.example.helpdesk.dto.AuthResponse;
import com.example.helpdesk.dto.LoginRequest;
import com.example.helpdesk.dto.RegisterRequest;
import com.example.helpdesk.dto.UserResponse;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.UserRepository;
import com.example.helpdesk.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("Email is already registered");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        return toAuthResponse(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalize(request.email()));
        String hash = user.map(User::getPassword).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return toAuthResponse(user.get());
    }

    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return toUserResponse(user);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                jwtService.getExpiresInSeconds(), toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}