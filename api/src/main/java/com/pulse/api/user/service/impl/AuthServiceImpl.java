package com.pulse.api.user.service.impl;

import com.pulse.api.exception.InvalidCredentialsException;
import com.pulse.api.user.dto.LoginRequest;
import com.pulse.api.user.entity.User;
import com.pulse.api.user.repository.UserRepository;
import com.pulse.api.user.security.JwtService;
import com.pulse.api.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    @Override
    public String login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user =userRepository
                .findByEmail(email)
                .orElseThrow(
                        () -> new InvalidCredentialsException("Invalid email or password")
                );
        if (!passwordEncoder
                .matches(request.password() , user.getPasswordHash())
        ){
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return jwtService.generateToken(user);
    }
}