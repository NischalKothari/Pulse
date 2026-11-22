
package com.pulse.api.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulse.api.user.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Autowired
    private JwtService jwtService;

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(
                get("/api/v1/users/me")
                        .header("Authorization", "Bearer definitely-not-a-jwt")
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAuthorizationSchemeReturns401() throws Exception {
        mockMvc.perform(
                get("/api/v1/users/me")
                        .header("Authorization", "Basic abc123")
        ).andExpect(status().isUnauthorized());
    }


    @Test
    void expiredTokenReturns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );

        Instant now = Instant.now();

        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(key)
                .compact();

        mockMvc.perform(
                get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + expiredToken)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithWrongSecretReturns401() throws Exception {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "AnotherTestSecretKey12345678901234567890"
                        .getBytes(StandardCharsets.UTF_8)
        );

        String invalidSignatureToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(
                        Instant.now().plusSeconds(300)
                ))
                .signWith(wrongKey)
                .compact();

        mockMvc.perform(
                get("/api/v1/users/me")
                        .header(
                                "Authorization",
                                "Bearer " + invalidSignatureToken
                        )
        ).andExpect(status().isUnauthorized());
    }


    @Test
    void validTokenAuthenticatesRequest() throws Exception {
        Instant now = Instant.now();

        User testUser = new User(
                UUID.randomUUID(),
                "valid-token-test@example.com",
                "unused-test-password-hash",
                now,
                now
        );

        String token = jwtService.generateToken(testUser);

        mockMvc.perform(
                get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
        ).andExpect(status().isOk());
    }

}
