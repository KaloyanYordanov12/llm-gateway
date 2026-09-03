package dev.kaloyanyordanov.llmgateway.cache;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Computes a deterministic cache key for a request: the SHA-256 of the canonical
 * JSON over exactly the locked field set ({@code model}, {@code system},
 * {@code messages}, {@code temperature}, {@code top_p}, {@code max_tokens},
 * {@code stop_sequences}). The set is assembled explicitly, so fields outside it —
 * notably {@code stream} — never affect the key, and a streamed and a non-streamed
 * request for the same messages share a cache entry.
 */
@Component
public class CacheKeyGenerator {

    // Nested objects (e.g. messages) serialize with sorted properties for stability.
    private final JsonMapper canonicalMapper = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .build();

    /**
     * @param request the request to key
     * @return a hex-encoded SHA-256 of the request's canonical JSON
     */
    public String key(MessagesRequest request) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("model", request.model());
        canonical.put("system", request.system());
        canonical.put("messages", request.messages());
        canonical.put("temperature", request.temperature());
        canonical.put("top_p", request.topP());
        canonical.put("max_tokens", request.maxTokens());
        canonical.put("stop_sequences", request.stopSequences());
        return HexFormat.of().formatHex(sha256(canonicalMapper.writeValueAsString(canonical)));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
