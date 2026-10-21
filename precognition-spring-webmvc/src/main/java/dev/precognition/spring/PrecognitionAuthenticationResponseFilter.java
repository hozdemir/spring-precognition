package dev.precognition.spring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Collections;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/** Preserves early authentication/CSRF responses while letting the official client recognize them. */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PrecognitionAuthenticationResponseFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var values = Collections.list(request.getHeaders("Precognition"));
        if (values.size() != 1 || !"true".equals(values.get(0).trim())) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            private void mark(int status) {
                if (status == 401 || status == 403) {
                    setHeader("Precognition", "true");
                    setHeader("Cache-Control", "no-store");
                }
            }
            @Override public void setStatus(int status) { mark(status); super.setStatus(status); }
            @Override public void sendError(int status) throws IOException { mark(status); super.sendError(status); }
            @Override public void sendError(int status, String message) throws IOException {
                mark(status); super.sendError(status, message);
            }
        });
    }
}
