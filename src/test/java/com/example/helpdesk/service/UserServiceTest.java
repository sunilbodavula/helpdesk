package com.example.helpdesk.service;

import com.example.helpdesk.dto.UserResponse;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.helpdesk.support.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository);
    }

    @Test
    void anAdminCannotChangeTheirOwnRole() {
        assertThatThrownBy(() -> service.changeRole(20L, Role.USER, 20L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void changingTheRoleOfAMissingUserIsNotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(2L, Role.AGENT, 20L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anAdminCanPromoteAnotherUser() {
        User target = user(2L, Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.changeRole(2L, Role.AGENT, 20L);

        assertThat(response.role()).isEqualTo(Role.AGENT);
        assertThat(target.getRole()).isEqualTo(Role.AGENT);
    }
}