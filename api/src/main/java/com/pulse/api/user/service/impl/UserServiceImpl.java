package com.pulse.api.user.service.impl;

import com.pulse.api.user.repository.UserRepository;
import com.pulse.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
}
