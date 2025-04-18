package org.alfresco.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Filter for authenticating requests against Alfresco.
 */
@Component
public class AuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final AuthenticationService authenticationService;

    @Value("${acl.enabled:false}")
    private boolean aclEnabled;

    @Autowired
    public AuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!aclEnabled || !request.getRequestURI().startsWith("/search")) {
            // Skip authentication if ACL is disabled or not a search request
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String base64Credentials = authHeader.substring("Basic ".length());
                byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
                String credentials = new String(credDecoded, StandardCharsets.UTF_8);
                String[] values = credentials.split(":", 2);

                if (values.length == 2) {
                    String username = values[0];
                    String password = values[1];

                    // Validate credentials against Alfresco
                    if (authenticationService.authenticate(username, password)) {
                        // Authentication successful
                        LOG.debug("Authentication successful for user: {}", username);
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
            } catch (Exception e) {
                LOG.warn("Error processing authentication", e);
            }
        }

        // Authentication failed
        LOG.debug("Authentication failed");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"Alfresco Neural Search\"");
        response.getWriter().write("Unauthorized");
    }
}