package dev.kaloyanyordanov.llmgateway.cache;

import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Computes a deterministic cache key for a request: the SHA-256 of its canonical
 * JSON over the locked field set ({@code model}, {@code system}, {@code messages},
 * {@code temperature}, {@code top_p}, {@code max_tokens}, {@code stop_sequences}).
 * Canonicalization sorts properties so semantically identical requests hash equal.
 */
@Component
public class CacheKeyGenerator {

    private final JsonMapper canonicalMapper = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    /**
     * @param request the request to key
     * @return a hex-encoded SHA-256 of the request's canonical JSON
     */
    public String key(MessagesRequest request) {
        String canonicalJson = canonicalMapper.writeValueAsString(request);
        return HexFormat.of().formatHex(sha256(canonicalJson));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
