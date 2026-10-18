package com.pulse.api.monitorService.service.impl;

import com.pulse.api.monitorService.dto.CreateServiceRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RequestHashService{

    private final ObjectMapper objectMapper;

    public RequestHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String calculateHash(CreateServiceRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (JacksonException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Failed to calculate request hash", e
            );
        }
    }
}