
package com.pulse.api.monitorService.controller;

import com.pulse.api.exception.IdempotencyKeyConflictException;
import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.service.ServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.pulse.api.user.security.JwtService;

import com.pulse.api.config.SecurityConfig;
import org.springframework.context.annotation.Import;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;

@WebMvcTest(
        controllers = ServiceController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
        }
)
@Import(SecurityConfig.class)
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceService serviceService;

    @MockitoBean
    private JwtService jwtService;

    private final UUID userId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    private RequestPostProcessor authenticatedAs(UUID id) {
        var authentication =
                new UsernamePasswordAuthenticationToken(
                        id,
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        var securityContext =
                org.springframework.security.core.context
                        .SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(authentication);

        return org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors
                .securityContext(securityContext);
    }

    @Test
    void createService_shouldReturn201() throws Exception {
        CreateServiceRequest request = new CreateServiceRequest(
                "Google", "https://google.com", 60, 5000);

        ServiceResponse response = new ServiceResponse(
                serviceId, "Google", "https://google.com",
                60, 5000, "UNKNOWN",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"));

        when(serviceService.createService(any(CreateServiceRequest.class),
                eq(userId),eq("test-key-123"))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/services")
                                .with(authenticatedAs(userId))
                                .header("Idempotency-Key", "test-key-123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {
                          "name": "Google",
                          "url": "https://google.com",
                          "checkIntervalSeconds": 60,
                          "timeoutMs": 5000
                        }
                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(serviceId.toString()))
                .andExpect(jsonPath("$.name").value("Google"));

        verify(serviceService).createService(any(CreateServiceRequest.class),
                eq(userId),eq("test-key-123"));
    }

    @Test
    void getMyServices_shouldReturn200() throws Exception {
        ServiceResponse response = new ServiceResponse(
                serviceId, "Google", "https://google.com",
                60, 5000, "UNKNOWN",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"));

        when(serviceService.getMyServices(userId))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/services")
                        .with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Google"));
    }

    @Test
    void getServiceById_shouldReturn200() throws Exception {
        ServiceResponse response = new ServiceResponse(
                serviceId, "Google", "https://google.com",
                60, 5000, "UNKNOWN",
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"));

        when(serviceService.getServiceById(serviceId, userId))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/services/{serviceId}", serviceId)
                        .with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceId.toString()));
    }

    @Test
    void deleteService_shouldReturn200() throws Exception {
        when(serviceService.deleteService(serviceId, userId))
                .thenReturn(true);

        mockMvc.perform(delete("/api/v1/services/{serviceId}", serviceId)
                        .with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Service deleted successfully"));

        verify(serviceService).deleteService(serviceId, userId);
    }

    @Test
    void deleteService_whenMissing_shouldReturn404() throws Exception {
        when(serviceService.deleteService(serviceId, userId))
                .thenReturn(false);

        mockMvc.perform(delete("/api/v1/services/{serviceId}", serviceId)
                        .with(authenticatedAs(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Service not found"));
    }

    @Test
    void createService_withoutAuthentication_shouldReturn401()
            throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Google",
                                  "url": "https://google.com",
                                  "checkIntervalSeconds": 60,
                                  "timeoutMs": 5000
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void createServiceWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/services")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Test Service",
                              "url": "https://example.com/health",
                              "checkIntervalSeconds": 60,
                              "timeoutMs": 2000
                            }
                            """))
                .andExpect(status().isBadRequest());

        verify(serviceService, never()).createService(
                any(CreateServiceRequest.class),
                any(UUID.class),
                anyString()
        );
    }

    @Test
    void createServiceWithBlankIdempotencyKeyReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/services")
                        .with(authenticatedAs(userId))
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Test Service",
                              "url": "https://example.com/health",
                              "checkIntervalSeconds": 60,
                              "timeoutMs": 2000
                            }
                            """))
                .andExpect(status().isBadRequest());

        verify(serviceService, never()).createService(
                any(CreateServiceRequest.class),
                any(UUID.class),
                anyString()
        );
    }

    @Test
    void createServiceWithOverlongIdempotencyKeyReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        String longKey = "a".repeat(256);

        mockMvc.perform(post("/api/v1/services")
                        .with(authenticatedAs(userId))
                        .header("Idempotency-Key", longKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Test Service",
                              "url": "https://example.com/health",
                              "checkIntervalSeconds": 60,
                              "timeoutMs": 2000
                            }
                            """))
                .andExpect(status().isBadRequest());

        verify(serviceService, never()).createService(
                any(CreateServiceRequest.class),
                any(UUID.class),
                anyString()
        );
    }


    @Test
    void createServiceWithKeyConflictReturnsConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        String key = "already-used-key";

        when(serviceService.createService(
                any(CreateServiceRequest.class),
                eq(userId),
                eq(key)
        )).thenThrow(new IdempotencyKeyConflictException(
                "Idempotency key was already used with a different request"
        ));

        mockMvc.perform(post("/api/v1/services")
                        .with(authenticatedAs(userId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "name": "Different Service",
                              "url": "https://example.com/health",
                              "checkIntervalSeconds": 60,
                              "timeoutMs": 2000
                            }
                            """))
                .andExpect(status().isConflict());

        verify(serviceService).createService(
                any(CreateServiceRequest.class),
                eq(userId),
                eq(key)
        );
    }

}
