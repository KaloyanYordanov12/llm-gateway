package dev.kaloyanyordanov.llmgateway.metrics;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the Prometheus scrape under {@code /api/metrics}. It lives on the
 * {@code /api/*} path deliberately, so it inherits the admin-key filter: a public,
 * unauthenticated metrics endpoint would leak internals (per-client ids, spend,
 * traffic shape), so it is gated behind the admin key rather than added to the
 * public actuator {@code exposure.include}.
 */
@RestController
@RequestMapping("/api")
public class PrometheusController {

    private final PrometheusMeterRegistry prometheusRegistry;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "PrometheusMeterRegistry is a Spring-managed singleton; holding the "
                    + "shared reference is intentional DI, not mutable-state exposure.")
    public PrometheusController(PrometheusMeterRegistry prometheusRegistry) {
        this.prometheusRegistry = prometheusRegistry;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String scrape() {
        return prometheusRegistry.scrape();
    }
}
