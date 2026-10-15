package com.example.helpdesk.service;

import com.example.helpdesk.dto.UserResponse;
import com.example.helpdesk.exception.ForbiddenException;
import com.example.helpdesk.exception.ResourceNotFoundException;
import com.example.helpdesk.model.Role;
import com.example.helpdesk.model.User;
import com.example.helpdesk.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse changeRole(Long targetId, Role newRole, Long actorId){
        if(targetId.equals(actorId)){
            throw new ForbiddenException("You cannot change your own role");
        }
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetId));
        user.setRole(newRole);
        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole(), saved.getCreatedAt());
    }

}
