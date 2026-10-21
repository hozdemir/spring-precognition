package dev.precognition.example;

import dev.precognition.spring.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Only demonstrates filter ordering. This is NOT Spring Security or production authentication. */
@Component
@Order(0)
@ConditionalOnProperty(name = "demo.authentication.enabled", havingValue = "true", matchIfMissing = true)
public class DemoAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        if (!request.getMethod().equals("OPTIONS") && java.util.Set.of("/users", "/legacy-users", "/profiles").contains(request.getRequestURI())
                && !"Bearer demo-token".equals(request.getHeader("Authorization"))) {
            response.setStatus(401);
            return;
        }
        chain.doFilter(request, response);
    }
}
