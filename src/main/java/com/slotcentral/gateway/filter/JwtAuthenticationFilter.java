package com.slotcentral.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotcentral.gateway.security.JwtValidator;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(2)
public class JwtAuthenticationFilter implements Filter, JwtValidator {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ObjectMapper objectMapper = new ObjectMapper();

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth") || path.startsWith("/actuator/health");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(httpRequest, httpResponse, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!validate(token)) {
            sendUnauthorized(httpRequest, httpResponse, "Malformed JWT token");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Performs structural JWT validation: checks the token has 3 dot-separated parts where
     * the header and payload are valid Base64url-encoded JSON objects.
     * TODO: integrate real signature verification once slot-auth-service publishes its signing keys / JWKS endpoint
     */
    @Override
    public boolean validate(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            // Validate that header and payload are parseable JSON objects
            objectMapper.readValue(headerBytes, Map.class);
            objectMapper.readValue(payloadBytes, Map.class);
            // Signature part may use non-padded Base64url; just require it to be non-blank
            return !parts[2].isBlank();
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private void sendUnauthorized(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", message);
        body.put("path", request.getRequestURI());
        body.put("correlationId", MDC.get("correlationId"));

        objectMapper.writeValue(response.getWriter(), body);
    }
}
