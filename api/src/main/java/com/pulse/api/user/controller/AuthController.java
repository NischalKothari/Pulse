package com.pulse.api.user.controller;

import com.pulse.api.user.dto.LoginRequest;
import com.pulse.api.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("login")
    public ResponseEntity<String> login(
            @Valid @RequestBody LoginRequest request
            ){
        String token = authService.login(request);
        return ResponseEntity.ok(token);
    }
}
