package dev.kaloyanyordanov.llmgateway.management;

import dev.kaloyanyordanov.llmgateway.error.ApiError;
import dev.kaloyanyordanov.llmgateway.error.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the management API ({@code /api/*}) with an admin key supplied via the
 * {@code x-admin-key} header. A missing or wrong key is rejected with {@code 401}
 * in the locked error envelope. The comparison is constant-time.
 *
 * <p>One exception, and only when {@code demoReadOpen} is set (the gateway is in
 * demo mode, where telemetry is stub data over no real spend): unauthenticated
 * {@code GET} requests to the read-only telemetry paths ({@code /api/stats},
 * {@code /api/clients}, {@code /api/usage}) are allowed through so a public demo
 * box shows the system running without a key. The exemption is narrow on purpose —
 * demo-mode <em>and</em> {@code GET} <em>and</em> an exact read path. Writes
 * ({@code POST}/{@code PATCH}), any other path (e.g. {@code /api/metrics}), and the
 * whole surface in live mode stay key-gated, so the {@code live} default leaks
 * nothing.</p>
 */
public class AdminAuthFilter extends OncePerRequestFilter {

    /** Header carrying the admin key. */
    public static final String ADMIN_KEY_HEADER = "x-admin-key";

    private static final String AUTHENTICATION_ERROR = "authentication_error";

    /** Read-only telemetry paths a demo box may serve to {@code GET} without a key. */
    private static final Set<String> PUBLIC_READ_PATHS =
            Set.of("/api/stats", "/api/clients", "/api/usage");

    private final String adminKey;
    private final boolean demoReadOpen;
    private final ObjectWriter errorWriter;

    public AdminAuthFilter(String adminKey, boolean demoReadOpen, JsonMapper jsonMapper) {
        this.adminKey = adminKey;
        this.demoReadOpen = demoReadOpen;
        this.errorWriter = jsonMapper.writer();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isPublicDemoRead(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(ADMIN_KEY_HEADER);
        if (presented == null || !constantTimeEquals(presented, adminKey)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * True only for the demo-mode public-read exemption: demo mode is open, the
     * method is {@code GET}, and the path is one of the exact read-only telemetry
     * paths. Everything else falls through to the admin-key check.
     */
    private boolean isPublicDemoRead(HttpServletRequest request) {
        return demoReadOpen
                && HttpMethod.GET.matches(request.getMethod())
                && PUBLIC_READ_PATHS.contains(pathWithinApplication(request));
    }

    /** Request path with any servlet context path stripped, for exact matching. */
    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ApiError error = ApiErrors.of(AUTHENTICATION_ERROR, "Invalid or missing admin key");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(errorWriter.writeValueAsString(error));
    }
}
