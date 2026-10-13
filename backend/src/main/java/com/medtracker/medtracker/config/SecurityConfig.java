package com.medtracker.medtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    // API REST sin estado: no usa sesión ni cookies, así que CSRF no aplica.
    // Mientras no exista el login con JWT (CU-02), todos los endpoints quedan
    // abiertos; al implementarlo, solo /users/register y el login deben
    // seguir siendo públicos.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Es una API JSON, no sirve páginas: se prohíbe cargar cualquier
                // recurso ni enmarcarla, y no se filtra el origen en el Referer.
                // (X-Content-Type-Options, X-Frame-Options, Cache-Control y HSTS
                // sobre HTTPS ya los agrega Spring Security por defecto.)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // Sin usuarios en memoria: la autenticación básica y por formulario están
    // deshabilitadas, así Spring Boot no genera el usuario "user" con una
    // contraseña aleatoria que se imprime en el log al arrancar. Se reemplaza
    // cuando exista el login con JWT.
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    // Hash de contraseñas (RNF-04)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
