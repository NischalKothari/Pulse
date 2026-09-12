package com.pulse.api.user.controller;

import com.pulse.api.user.dto.RegisterRequest;
import com.pulse.api.user.dto.UserResponse;
import com.pulse.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/users")
public class UserController {
    private final UserService userService;
    @PostMapping("register")
    public ResponseEntity<UserResponse> registerUser(@RequestBody @Valid RegisterRequest request){
        UserResponse response = userService.register(request);
        URI location = URI.create("/api/v1/users/" + response.id());
        return ResponseEntity
                .created(location)
                .body(response);
    }
    @GetMapping("/me")
    public ResponseEntity<String> getCurrentUser(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                "Authenticated user: " + authentication.getPrincipal()
        );
    }
}
