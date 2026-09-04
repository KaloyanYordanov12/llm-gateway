/**
 * Observability: Micrometer instrumentation ({@link
 * dev.kaloyanyordanov.llmgateway.metrics.GatewayMetrics}), deterministic
 * request-latency percentiles ({@link
 * dev.kaloyanyordanov.llmgateway.metrics.LatencyTracker}), the timing filter, and
 * the admin-guarded Prometheus scrape endpoint. The Prometheus endpoint is served
 * only through {@code /api/metrics}; the public actuator exposure is not widened.
 */
package dev.kaloyanyordanov.llmgateway.metrics;
