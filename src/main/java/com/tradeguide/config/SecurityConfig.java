package com.tradeguide.config;

import com.tradeguide.service.auth.GoogleOidcUserService;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    @ConditionalOnProperty(prefix = "tradeguide.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tradeguide.auth", name = "enabled", havingValue = "true")
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http, GoogleOidcUserService googleOidcUserService) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/admin/**").denyAll()
                        .requestMatchers(HttpMethod.POST, "/api/members").denyAll()
                        .requestMatchers("/api/members/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(UNAUTHORIZED),
                                request -> request.getRequestURI()
                                        .startsWith(request.getContextPath() + "/api/")
                        ))
                .oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOidcUserService)))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "tradeguide.auth", name = "enabled", havingValue = "true")
    ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret
    ) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException("tradeguide.auth.enabled=true requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET.");
        }
        ClientRegistration google = ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
                .registrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
