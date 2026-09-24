package kr.yesulin.actor.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Browser-side hardening for the app and the API. The page may load only its own scripts, the Pretendard
 * font from jsDelivr and Google Analytics (the hosts Google documents for GA4); API responses — including
 * preview SVGs rendered from uploaded forms — can run nothing even when opened directly, so a crafted form
 * cannot script this origin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public final class SecurityHeadersFilter extends OncePerRequestFilter {
    static final String PAGE_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' https://*.googletagmanager.com",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net",
            "font-src 'self' data: https://cdn.jsdelivr.net",
            "img-src 'self' data: blob: https://*.google-analytics.com https://*.googletagmanager.com",
            "connect-src 'self' https://*.google-analytics.com https://*.analytics.google.com "
                    + "https://*.googletagmanager.com",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");
    static final String API_POLICY = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:; "
            + "frame-ancestors 'none'; sandbox";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean api = request.getRequestURI().startsWith("/api/");
        response.setHeader("Content-Security-Policy", api ? API_POLICY : PAGE_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Strict-Transport-Security", "max-age=31536000");
        if (api) {
            // Applicants' answers and photos must not linger in shared or proxy caches.
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
