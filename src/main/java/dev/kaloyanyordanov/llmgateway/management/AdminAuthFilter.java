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
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the management API ({@code /api/*}) with an admin key supplied via the
 * {@code x-admin-key} header. A missing or wrong key is rejected with {@code 401}
 * in the locked error envelope. The comparison is constant-time.
 */
public class AdminAuthFilter extends OncePerRequestFilter {

    /** Header carrying the admin key. */
    public static final String ADMIN_KEY_HEADER = "x-admin-key";

    private static final String AUTHENTICATION_ERROR = "authentication_error";

    private final String adminKey;
    private final ObjectWriter errorWriter;

    public AdminAuthFilter(String adminKey, JsonMapper jsonMapper) {
        this.adminKey = adminKey;
        this.errorWriter = jsonMapper.writer();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(ADMIN_KEY_HEADER);
        if (presented == null || !constantTimeEquals(presented, adminKey)) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
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
