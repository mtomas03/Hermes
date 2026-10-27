package it.unibo.hermes.gateway.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authentication filter that intercepts incoming HTTP requests to populate
 * the Spring Security context when a valid JWT bearer token is present.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtProvider jwtProvider;

    /**
     * Creates the filter with the JWT provider.
     *
     * @param jwtProvider the utility component handling token validation and parsing
     */
    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Processes each HTTP request, extracting and validating the bearer token
     * to set up authentication in the security context.
     *
     * @param request  the incoming HTTP servlet request
     * @param response the outgoing HTTP servlet response
     * @param chain    the security filter chain
     * @throws ServletException if a servlet processing exception occurs
     * @throws IOException      if an input or output error occurs during request filtering
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);
        boolean isTokenValid = token != null && jwtProvider.isValid(token);
        if (token != null && !isTokenValid) {
            log.warn("Rejected bearer token on {} {} (pod {})",
                    request.getMethod(), request.getRequestURI(), System.getenv("HOSTNAME"));
        }

        if (isTokenValid && SecurityContextHolder.getContext().getAuthentication() == null) {

            String username = jwtProvider.extractUsername(token);
            UserDetails details = User
                    .withUsername(username)
                    .password("")
                    .roles("USER")
                    .build();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            details, null, details.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts the raw token string from the {@code Authorization} header.
     *
     * @param request the HTTP request containing headers
     * @return the extracted JWT string, or {@code null} if the header is missing or malformed
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
