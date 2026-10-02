package kr.yesulin.actor.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the operator API ({@code /api/admin/**}) to whoever holds {@code yesulin.admin-token}, sent as
 * "Authorization: Bearer <token>". Without a configured token the operator API does not exist.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class AdminTokenFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);
    private static final String PREFIX = "Bearer ";
    private final byte[] token;

    public AdminTokenFilter(@Value("${yesulin.admin-token:}") String token) {
        this.token = token.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/api/admin") || path.startsWith("/api/admin/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token.length == 0) {
            reject(response, 404, "ADMIN_DISABLED", "운영자 기능이 꺼져 있습니다.");
            return;
        }
        String header = request.getHeader("Authorization");
        byte[] given = header != null && header.startsWith(PREFIX)
                ? header.substring(PREFIX.length()).strip().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (!MessageDigest.isEqual(token, given)) {
            log.warn("admin token rejected ip={} path={}", request.getRemoteAddr(), request.getRequestURI());
            reject(response, 401, "ADMIN_UNAUTHORIZED", "운영자 토큰이 맞지 않습니다.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
