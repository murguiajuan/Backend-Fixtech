package com.fixtech.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

class CorsConfigTest {

    private final CorsConfigurationSource source = new CorsConfig()
            .corsConfigurationSource("http://localhost:4200,http://127.0.0.1:4200");

    @Test
    void permiteElPreflightDesdeAngular() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:4200");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:4200");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .contains("POST", "PATCH", "DELETE");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("Authorization")
                .containsIgnoringCase("Content-Type");
    }

    @Test
    void rechazaUnOrigenNoPermitido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, "http://sitio-no-permitido.test");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new CorsFilter(source).doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
