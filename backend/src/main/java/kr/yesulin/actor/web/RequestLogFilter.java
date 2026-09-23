package kr.yesulin.actor.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One line per API call: method, path, status, time. Paths carry only random document ids; bodies,
 * file names and query strings are never logged, so no applicant data reaches the log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger("kr.yesulin.actor.access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - started) / 1_000_000;
            int status = response.getStatus();
            String line = "{} {} -> {} {}ms ip={}";
            Object[] args = {request.getMethod(), request.getRequestURI(), status, millis, request.getRemoteAddr()};
            if (status >= 500) {
                log.warn(line, args);
            } else {
                log.info(line, args);
            }
        }
    }
}
