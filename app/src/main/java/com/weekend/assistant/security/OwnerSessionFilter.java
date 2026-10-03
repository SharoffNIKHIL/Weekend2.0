package com.weekend.assistant.security;

import com.weekend.assistant.config.WeekendProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * P1 second layer. Every {@code /api/**} request needs a valid owner session token
 * ({@code Authorization: Bearer <token>}). Layer 1 is the network: Cloud Run internal ingress +
 * IAM, reached only through the tailnet entry node. {@code /jobs/**} is called by Cloud Scheduler /
 * Cloud Tasks with an OIDC token that Cloud Run IAM has already verified.
 */
@Component
public class OwnerSessionFilter extends OncePerRequestFilter {

    private final SessionTokenService tokens;
    private final boolean required;

    public OwnerSessionFilter(SessionTokenService tokens, WeekendProperties props) {
        this.tokens = tokens;
        this.required = props.security().requireSession();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!required) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (tokens.verify(token).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
