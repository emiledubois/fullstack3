package com.ecommerce.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/register", "/auth/login", "/auth/logout").permitAll()
                // /auth/interno/** no es público: InternalAuthFilter (Order
                // HIGHEST_PRECEDENCE) ya exigió y verificó la firma HMAC de
                // api-gateway antes de que la petición llegue aquí — este
                // permitAll solo evita que Spring Security la vuelva a
                // bloquear por falta de un Authentication de usuario final.
                .requestMatchers("/auth/interno/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Swagger UI y OpenAPI docs — públicos
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                 "/v3/api-docs/**", "/v3/api-docs").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
