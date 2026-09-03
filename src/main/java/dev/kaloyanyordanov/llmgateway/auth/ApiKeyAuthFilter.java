package dev.kaloyanyordanov.llmgateway.auth;

import dev.kaloyanyordanov.llmgateway.error.ApiError;
import dev.kaloyanyordanov.llmgateway.error.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authenticates inbound requests by the {@code x-api-key} header. A missing or
 * invalid key is rejected with {@code 401} in the locked error envelope; a valid
 * key exposes the authenticated {@link Client} as a request attribute and lets
 * the request proceed.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    /** Header carrying the client API key. */
    public static final String API_KEY_HEADER = "x-api-key";

    /** Request attribute under which the authenticated client is exposed. */
    public static final String CLIENT_ATTRIBUTE = "llmgateway.authenticatedClient";

    private static final String AUTHENTICATION_ERROR = "authentication_error";

    private final ClientAuthenticator authenticator;
    private final ObjectWriter errorWriter;

    public ApiKeyAuthFilter(ClientAuthenticator authenticator, JsonMapper jsonMapper) {
        this.authenticator = authenticator;
        // ObjectWriter is immutable/thread-safe; safe to hold as a field.
        this.errorWriter = jsonMapper.writer();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String presentedKey = request.getHeader(API_KEY_HEADER);
        Optional<Client> client = authenticator.authenticate(presentedKey);
        if (client.isEmpty()) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(CLIENT_ATTRIBUTE, client.get());
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ApiError error = ApiErrors.of(AUTHENTICATION_ERROR, "Invalid or missing API key");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, API_KEY_HEADER);
        response.getWriter().write(errorWriter.writeValueAsString(error));
    }
}
