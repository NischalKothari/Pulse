package com.pulse.api.user.service;

import com.pulse.api.user.dto.RegisterRequest;
import com.pulse.api.user.dto.UserResponse;

public interface UserService {
    UserResponse register(RegisterRequest request);
}
