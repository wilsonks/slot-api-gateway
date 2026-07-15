package com.slotcentral.gateway.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    @Test
    void shouldAllowRequestsWithinLimit() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldRateLimitAfterExceedingBucket() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(request, resp, chain);
            assertThat(resp.getStatus()).isNotEqualTo(429);
        }

        MockHttpServletResponse rateLimitedResponse = new MockHttpServletResponse();
        filter.doFilter(request, rateLimitedResponse, chain);
        assertThat(rateLimitedResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldUseApiKeyAsClientKeyWhenPresent() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.3");
        request.addHeader("X-Api-Key", "test-api-key");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(429);
        verify(chain).doFilter(request, response);
    }
}
