package kr.yesulin.actor.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps uploads and file builds per visitor (IP) in a sliding window. Each build parses a HWP and runs
 * the renderer, so one script hammering the API would slow everyone else down. Reads are not limited.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(
            @Value("${yesulin.rate-limit.requests:60}") int limit,
            @Value("${yesulin.rate-limit.window:PT10M}") Duration window) {
        this(limit, window, Clock.systemUTC());
    }

    RateLimitFilter(int limit, Duration window, Clock clock) {
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (allow(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("rate limited ip={} path={}", request.getRemoteAddr(), request.getRequestURI());
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"요청이 너무 많습니다.\"}");
    }

    boolean allow(String visitor) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        Deque<Instant> times = hits.computeIfAbsent(visitor, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
        }
        if (hits.size() > 10_000) {
            // Forget visitors whose window has passed so the map cannot grow without bound.
            hits.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || !entry.getValue().peekLast().isAfter(cutoff);
                }
            });
        }
        return true;
    }
}
