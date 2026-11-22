package com.pulse.api.rateLimit;

import com.pulse.api.user.security.JwtService;
import com.pulse.api.user.service.AuthService;
import com.pulse.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = {
                com.pulse.api.user.controller.AuthController.class,
                com.pulse.api.user.controller.UserController.class
        },
        excludeAutoConfiguration = SecurityAutoConfiguration.class,
        properties = {
        "pulse.rate-limit.login.capacity=2",
        "pulse.rate-limit.login.refill-period=PT1H",
        "pulse.rate-limit.registration.capacity=2",
        "pulse.rate-limit.registration.refill-period=PT1H",
        "pulse.rate-limit.authenticated-api.capacity=2",
        "pulse.rate-limit.authenticated-api.refill-period=PT1H"
})
@AutoConfigureMockMvc
@Import({
        com.pulse.api.config.SecurityConfig.class,
        com.pulse.api.user.security.JwtAuthenticationFilter.class,
        RateLimitKeyResolver.class,
        RateLimitConfiguration.class
})
class RateLimitFilterIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @Test
    void filterIsNotRegisteredAsASeparateServletFilterBean() {
        assertEquals(0, applicationContext.getBeansOfType(RateLimitFilter.class).size());
    }

    @Test
    void loginAndRegistrationUseSeparateIpBucketsAnd429Contract() throws Exception {
        String ip = "198.51.100.17";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(remoteAddress(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "3600"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.timestamp").exists());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users/register")
                            .with(remoteAddress(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/users/register")
                        .with(remoteAddress(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void loginBucketIsScopedByRemoteIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(remoteAddress("198.51.100.20"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("198.51.100.21"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedApiBucketUsesUserUuidAndPreflightDoesNotConsumeTokens() throws Exception {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(options("/api/v1/users/me")
                            .header(HttpHeaders.ORIGIN, "https://client.example")
                            .header("Access-Control-Request-Method", "GET")
                            .with(authenticated(firstUser)))
                    .andExpect(result -> assertNotEquals(
                            429, result.getResponse().getStatus()
                    ));
        }

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/users/me")
                            .with(authenticated(firstUser)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticated(firstUser)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticated(secondUser)))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedProtectedRequestsRemain401AndDoNotConsumeUserBucket() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authenticated(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor authenticated(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId,
                null,
                AuthorityUtils.NO_AUTHORITIES
        ));
    }

    private RequestPostProcessor remoteAddress(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
