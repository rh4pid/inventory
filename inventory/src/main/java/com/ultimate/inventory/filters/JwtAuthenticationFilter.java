package com.ultimate.inventory.filters;

import com.ultimate.inventory.DTO.ResponseDTO;
import com.ultimate.inventory.utils.Security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final Security security;
    final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(Security security) {
        this.security = security;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null) {
            ResponseDTO result = security.retrieveAccessToken(request);
            if (!result.getStatus()) {
                log.warn("JWT validation failed for {}: {}", request.getRequestURI(), result.getMessage());
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(result));
                return;
            }

            String email = result.getMessage();
            var auth = new JwtAuthenticationToken(email);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }
}
