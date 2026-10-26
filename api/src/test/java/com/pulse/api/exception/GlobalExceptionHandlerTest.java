
package com.pulse.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionReturnsInternalServerError() {
        Exception exception =
                new RuntimeException("Sensitive database details");

        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpectedException(exception);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        assertEquals(500, response.getBody().status());

        assertEquals(
                "An unexpected error occurred",
                response.getBody().message()
        );

        assertNotNull(response.getBody().timestamp());

        assertFalse(
                response.getBody().message()
                        .contains("Sensitive database details")
        );
    }
}
