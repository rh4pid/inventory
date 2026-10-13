package com.ultimate.inventory.filters;


import com.ultimate.inventory.DTO.ResponseDTO;
import com.ultimate.inventory.utils.Security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final Security security;
    final ObjectMapper objectMapper = new ObjectMapper();

    // this is for Constructor injection (preferred over field injection for filters)
    public JwtAuthenticationFilter(Security security) {
        this.security = security;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        System.out.println("ABOUT TO START");
        System.out.println("requestPath " + requestPath);

        //1. Decide whether we need to apply this filter
        String header = request.getHeader("Authorization");
        if (header != null) {
            //2. Check credentials and [authenticate | reject]
            ResponseDTO responseDTO = security.retrieveAccessToken(request);
            if (!responseDTO.getStatus()) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(responseDTO));
                return;
            }

            //3. authenticate
            String email = responseDTO.getMessage();
            var auth = new JwtAuthenticationToken(email);
            var newContext = SecurityContextHolder.createEmptyContext();
            newContext.setAuthentication(auth);
            SecurityContextHolder.setContext(newContext);
        }


        //4. call next
        System.out.println("🐜🐜🐜🐜🐜🐜🐜 MIGHTY ANTS");
        filterChain.doFilter(request, response);
    }


}
