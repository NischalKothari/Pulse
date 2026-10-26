
package com.pulse.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private static final Logger log =
            LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();
        long startTime = System.nanoTime();

        try {
            MDC.put(REQUEST_ID, requestId);
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - startTime
            );

            int status = response.getStatus();

            String message =
                    "HTTP request completed method={} path={} status={} durationMs={}";

            if (status >= 500) {
                log.error(
                        message,
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        durationMs
                );
            } else if (status >= 400) {
                log.warn(
                        message,
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        durationMs
                );
            } else {
                log.info(
                        message,
                        request.getMethod(),
                        request.getRequestURI(),
                        status,
                        durationMs
                );
            }

            MDC.remove(REQUEST_ID);
        }
    }
}
