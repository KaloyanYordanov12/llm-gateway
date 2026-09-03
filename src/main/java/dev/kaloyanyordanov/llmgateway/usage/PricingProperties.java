package dev.kaloyanyordanov.llmgateway.usage;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The configured pricing table. Its key set is also the model allowlist: a model
 * absent from this table is rejected (fail-secure), keeping accounting exact.
 *
 * @param models model id → {@link ModelRate}
 */
@ConfigurationProperties("gateway.pricing")
public record PricingProperties(Map<String, ModelRate> models) {

    /** Defensive-copy compact constructor (null becomes an empty, immutable map). */
    public PricingProperties {
        models = models == null ? Map.of() : Map.copyOf(models);
    }
}
