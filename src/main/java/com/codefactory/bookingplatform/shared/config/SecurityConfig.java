package com.codefactory.bookingplatform.shared.config;

import com.codefactory.bookingplatform.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/password-recovery-requests",
            "/api/v1/auth/password-resets",
            "/api/v1/registrations/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(SecurityProperties securityProperties, ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SupabaseJwtAuthConverter supabaseJwtAuthConverter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                SupabaseJwtAuthConverter.toAuthenticationConverter(supabaseJwtAuthConverter)))
                        .authenticationEntryPoint(problemDetailEntryPoint())
                        .accessDeniedHandler(problemDetailAccessDeniedHandler()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problemDetailEntryPoint())
                        .accessDeniedHandler(problemDetailAccessDeniedHandler()))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(securityProperties.jwksUri())
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.ES256);
                    algorithms.add(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
                })
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(securityProperties.jwtIssuer())));
        return decoder;
    }

    private AuthenticationEntryPoint problemDetailEntryPoint() {
        return (request, response, authException) ->
                writeProblem(response, request, ErrorCode.AUTH_REQUIRED, ErrorCode.AUTH_REQUIRED.defaultMessage());
    }

    private AccessDeniedHandler problemDetailAccessDeniedHandler() {
        return (request, response, ex) ->
                writeProblem(response, request, ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.defaultMessage());
    }

    private void writeProblem(HttpServletResponse response, HttpServletRequest request,
                              ErrorCode code, String message) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), message);
        problem.setTitle(code.status().getReasonPhrase());
        problem.setType(URI.create("https://bookingplatform.codefactory.com/errors/" + code.name().toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("traceId", MDC.get("traceId"));
        problem.setProperty("timestamp", Instant.now().toString());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
