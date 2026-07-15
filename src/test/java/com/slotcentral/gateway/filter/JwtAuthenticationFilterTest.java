package com.slotcentral.gateway.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
    }

    @Test
    void shouldPassThroughAuthPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
        request.setRequestURI("/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    @Test
    void shouldPassThroughActuatorHealth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldReturn401WhenNoAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bank/balance");
        request.setRequestURI("/api/bank/balance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldReturn401WhenMalformedToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bank/balance");
        request.setRequestURI("/api/bank/balance");
        request.addHeader("Authorization", "******");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void shouldPassWithStructurallyValidJwt() throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user1\"}".getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("fakesig".getBytes());
        String token = header + "." + payload + "." + signature;

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bank/balance");
        request.setRequestURI("/api/bank/balance");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void validateReturnsTrueForWellFormedJwt() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"user1\"}".getBytes());
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString("fakesig".getBytes());
        assertThat(filter.validate(header + "." + payload + "." + signature)).isTrue();
    }

    @Test
    void validateReturnsFalseForMissingParts() {
        assertThat(filter.validate("onlyone")).isFalse();
        assertThat(filter.validate("two.parts")).isFalse();
        assertThat(filter.validate(null)).isFalse();
        assertThat(filter.validate("")).isFalse();
    }
}
