package dev.kaloyanyordanov.llmgateway.ratelimit;

import dev.kaloyanyordanov.llmgateway.auth.ApiKeyAuthFilter;
import dev.kaloyanyordanov.llmgateway.auth.Client;
import dev.kaloyanyordanov.llmgateway.error.ApiError;
import dev.kaloyanyordanov.llmgateway.error.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Enforces the per-client rate limit. Runs after {@link ApiKeyAuthFilter}, so the
 * authenticated {@link Client} is already on the request. Over-limit requests are
 * rejected with {@code 429} in the locked error envelope.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_ERROR = "rate_limit_error";

    private final RateLimiterService rateLimiter;
    private final ObjectWriter errorWriter;

    public RateLimitFilter(RateLimiterService rateLimiter, JsonMapper jsonMapper) {
        this.rateLimiter = rateLimiter;
        this.errorWriter = jsonMapper.writer();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Object attribute = request.getAttribute(ApiKeyAuthFilter.CLIENT_ATTRIBUTE);
        if (attribute instanceof Client client && !rateLimiter.tryAcquire(client.getId())) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ApiError error = ApiErrors.of(RATE_LIMIT_ERROR, "Rate limit exceeded");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(errorWriter.writeValueAsString(error));
    }
}
