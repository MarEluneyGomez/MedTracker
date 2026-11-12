package com.medtracker.medtracker.security;

import com.medtracker.medtracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Lee "Authorization: Bearer <token>" y, si es válido y su usuario existe y no
// está dado de baja, deja id + role en el SecurityContext (el rol sale del
// token, no de la base). Si el token falta o es inválido, o el usuario no está
// vigente, no responde nada: sigue sin autenticar y el AuthenticationEntryPoint
// devuelve el 401 en los endpoints protegidos. Falla cerrado: si la consulta
// del usuario falla, tampoco autentica. No es un @Component a propósito: así
// Spring Boot no lo registra también como filtro de servlet y solo corre
// dentro de la cadena de seguridad.
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService.parse(token).filter(this::isActiveUser).ifPresent(user -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            });
        }
        filterChain.doFilter(request, response);
    }

    // El usuario del token debe existir y no estar dado de baja. Si la consulta
    // falla no se autentica; el log no lleva el id, el token ni el mensaje del error.
    private boolean isActiveUser(AuthenticatedUser user) {
        try {
            return userRepository.existsByIdAndDeletedAtIsNull(user.id());
        } catch (RuntimeException e) {
            log.warn("No se pudo comprobar el usuario del token ({}): la request sigue sin autenticar",
                    e.getClass().getName());
            return false;
        }
    }
}
