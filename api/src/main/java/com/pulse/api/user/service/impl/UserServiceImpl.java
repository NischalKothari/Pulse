package com.pulse.api.user.service.impl;

import com.pulse.api.exception.DuplicateEmailException;
import com.pulse.api.user.dto.RegisterRequest;
import com.pulse.api.user.dto.UserResponse;
import com.pulse.api.user.entity.User;
import com.pulse.api.user.repository.UserRepository;
import com.pulse.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if(userRepository.existsByEmail(email)){
            throw new DuplicateEmailException("Email already registered");
        }
        Instant now = Instant.now();
        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                now,
                now
        );
        User savedUser = userRepository.save(user);
        return userResponseMapper(savedUser);
    }

    private UserResponse userResponseMapper(User user){
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }

}