package com.example.helpdesk.service;

import com.example.helpdesk.dto.AuthResponse;
import com.example.helpdesk.dto.LoginRequest;
import com.example.helpdesk.dto.RegisterRequest;
import com.example.helpdesk.exception.ConflictException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.UserRepository;
import com.example.helpdesk.security.AuthUser;
import com.example.helpdesk.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static com.example.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "auth-service-test-secret-auth-service-test";

    @Mock private UserRepository userRepository;

    // Cost 4 is the minimum, so the tests stay fast. Production uses cost 12.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwtService = new JwtService(SECRET, 60L);

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, encoder, jwtService);
    }

    @Test
    void registeringNormalizesTheInputHashesThePasswordAndAlwaysCreatesAUser() {
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        AuthResponse response = service.register(new RegisterRequest("  Asha  ", "  Asha@Test.COM ", "password123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("asha@test.com");
        assertThat(saved.getName()).isEqualTo("Asha");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(encoder.matches("password123", saved.getPassword())).isTrue();

        AuthUser fromToken = jwtService.parse(response.accessToken());
        assertThat(fromToken.id()).isEqualTo(42L);
        assertThat(fromToken.role()).isEqualTo(Role.USER);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("asha@test.com");
    }

    @Test
    void registeringAnExistingEmailIsAConflict() {
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.of(user(1L, Role.USER)));

        assertThatThrownBy(() -> service.register(new RegisterRequest("Asha", "ASHA@test.com", "password123")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email is already registered");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loggingInReturnsATokenForTheStoredRole() {
        User stored = user(5L, Role.AGENT);
        stored.setPassword(encoder.encode("password123"));
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.of(stored));

        AuthResponse response = service.login(new LoginRequest("  ASHA@test.com", "password123"));

        AuthUser fromToken = jwtService.parse(response.accessToken());
        assertThat(fromToken.id()).isEqualTo(5L);
        assertThat(fromToken.role()).isEqualTo(Role.AGENT);
        assertThat(response.user().id()).isEqualTo(5L);
    }

    @Test
    void aWrongPasswordIsRejected() {
        User stored = user(5L, Role.USER);
        stored.setPassword(encoder.encode("password123"));
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@test.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void anUnknownEmailIsRejected() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@test.com", "password123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailLookIdentical() {
        User stored = user(5L, Role.USER);
        stored.setPassword(encoder.encode("password123"));
        when(userRepository.findByEmail("asha@test.com")).thenReturn(Optional.of(stored));
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        Throwable wrongPassword = catchThrowable(() -> service.login(new LoginRequest("asha@test.com", "nope")));
        Throwable unknownEmail = catchThrowable(() -> service.login(new LoginRequest("ghost@test.com", "nope")));

        assertThat(wrongPassword).isInstanceOf(BadCredentialsException.class);
        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class);
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
    }

    @Test
    void aProfileForAMissingUserIsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}