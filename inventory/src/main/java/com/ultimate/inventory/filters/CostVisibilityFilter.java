package com.ultimate.inventory.filters;

import com.ultimate.inventory.utils.CostVisibility;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Request threads are reused: forget the previous caller's cost visibility after each request. */
@Component
public class CostVisibilityFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain) throws ServletException, IOException {
        CostVisibility.clear();
        try {
            chain.doFilter(request, response);
        } finally {
            CostVisibility.clear();
        }
    }
}
