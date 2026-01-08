package com.qualtech_ai.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(CustomUserDetailsService userDetailsService, JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            logger.debug("JWT Token found in header: {}" + token.substring(0, Math.min(token.length(), 10)) + "...");
            try {
                // Validate the token first
                if (jwtUtil.validateToken(token)) {
                    username = jwtUtil.extractUsername(token);
                    logger.debug("Successfully extracted username from JWT: {}" + username);
                } else {
                    logger.warn("JWT validation failed for token");
                }
            } catch (JwtException e) {
                logger.error("JWT validation error: " + e.getMessage());
            }
        } else {
            if (authHeader == null) {
                logger.debug("No Authorization header found in request");
            } else {
                logger.warn("Authorization header does not start with Bearer: {}" + authHeader);
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            logger.debug("Setting up SecurityContext for user: {}" + username);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // If token is valid, configure Spring Security to manually set authentication
            if (jwtUtil.validateToken(token, userDetails)) {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Successfully set Authentication in SecurityContext for user: {}" + username);
            } else {
                logger.warn("Final JWT validation with user details failed for user: {}" + username);
            }
        }

        filterChain.doFilter(request, response);
    }
}
