package com.medtracker.medtracker.config;

import com.medtracker.medtracker.security.JwtAuthenticationFilter;
import com.medtracker.medtracker.security.JwtService;
import com.medtracker.medtracker.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
    // Solo el login y el registro son públicos; el resto exige un JWT válido
    // (Authorization: Bearer <token>). La pertenencia de cada recurso (RN-01,
    // RN-09) se valida en los services con AccessPolicy, usando el usuario del token.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
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
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Sin esto, los errores que Spring reenvía a /error (400, 404,
                        // 409...) se convertirían en 401
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/users/register").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Sin usuarios en memoria: la autenticación básica y por formulario están
    // deshabilitadas, así Spring Boot no genera el usuario "user" con una
    // contraseña aleatoria que se imprime en el log al arrancar. La identidad
    // sale del JWT (JwtAuthenticationFilter), no de este servicio.
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
