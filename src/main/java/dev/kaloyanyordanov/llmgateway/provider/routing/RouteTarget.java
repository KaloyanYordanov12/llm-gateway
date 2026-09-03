package dev.kaloyanyordanov.llmgateway.provider.routing;

/**
 * A resolved routing target: which provider to call and which concrete upstream
 * model to send it.
 *
 * @param provider the registered provider name to call
 * @param model    the concrete upstream model id to send
 */
public record RouteTarget(String provider, String model) {
}
