package com.slotcentral.gateway.security;

/**
 * Pluggable JWT validation interface.
 * TODO: integrate real signature verification once slot-auth-service publishes its signing keys / JWKS endpoint
 */
public interface JwtValidator {
    /**
     * Validates the given JWT token.
     * @param token the raw JWT token string (without "Bearer " prefix)
     * @return true if the token is structurally valid and passes any configured checks
     */
    boolean validate(String token);
}
