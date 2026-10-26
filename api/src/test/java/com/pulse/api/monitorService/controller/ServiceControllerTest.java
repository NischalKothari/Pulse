
package com.pulse.api.monitorService.controller;

import com.pulse.api.exception.IdempotencyKeyConflictException;
import com.pulse.api.monitorService.dto.CreateServiceRequest;
import com.pulse.api.monitorService.dto.ServiceResponse;
import com.pulse.api.monitorService.service.ServiceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;


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
                Instant.parse("2026-01-01T10:00:00Z")
        );

        Page<ServiceResponse> responsePage = new PageImpl<>(
                List.of(response),
                PageRequest.of(0, 20),
                1
        );

        when(serviceService.getMyServices(userId, 0, 20))
                .thenReturn(responsePage);

        mockMvc.perform(get("/api/v1/services")
                        .param("page", "0")
                        .param("size", "20")
                        .with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Google"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(serviceService).getMyServices(userId, 0, 20);
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

    @Test
    void getMyServices_whenParametersMissing_shouldUseDefaults() throws Exception {
        UUID userId = UUID.randomUUID();

        Page<ServiceResponse> emptyPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 20),
                0
        );

        when(serviceService.getMyServices(userId, 0, 20))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/services")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId, null, List.of()
                                )
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(serviceService).getMyServices(userId, 0, 20);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "?page=-1",
            "?size=0",
            "?size=101",
            "?page=abc"
    })
    void getMyServices_whenPaginationParametersInvalid_shouldReturnBadRequest(
            String query
    ) throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/services" + query)
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId, null, List.of()
                                )
                        )))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(serviceService);
    }


    @Test
    void getMyServices_whenPageIsNegative_shouldReturnStandardErrorResponse()
            throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/services?page=-1")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId, null, List.of()
                                )
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(serviceService);
    }

    @Test
    void getMyServices_whenPageIsNotNumeric_shouldReturnStandardErrorResponse()
            throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/services?page=abc")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        userId, null, List.of()
                                )
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(serviceService);
    }

}
