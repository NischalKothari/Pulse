package com.pulse.api.user.service;

import com.pulse.api.user.dto.LoginRequest;

public interface AuthService {
    String login(LoginRequest request);
}
