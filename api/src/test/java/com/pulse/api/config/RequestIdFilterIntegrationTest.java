
package com.pulse.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.context.annotation.Import;
import com.pulse.api.user.entity.User;
import com.pulse.api.user.security.JwtService;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RequestIdTestController.class)
class RequestIdFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void healthRequestReturnsRequestIdAndSuccessfulStatus() throws Exception {
        var result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
    }


    @Test
    void unexpectedExceptionReturns500AndRequestId() throws Exception {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        User testUser = new User(
                userId,
                "request-id-test@example.com",
                "unused-test-password-hash",
                now,
                now
        );

        String token = jwtService.generateToken(testUser);

        var result = mockMvc.perform(
                        get("/__test/request-id/boom")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                .jsonPath("$.status").value(500)
                )
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                .jsonPath("$.message")
                                .value("An unexpected error occurred")
                )
                .andReturn();

        String requestId = result.getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(requestId);
        assertFalse(requestId.isBlank());
    }
}
