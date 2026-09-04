package dev.kaloyanyordanov.llmgateway.proxy.streaming;

import dev.kaloyanyordanov.llmgateway.cache.ResponseCacheService;
import dev.kaloyanyordanov.llmgateway.metrics.GatewayMetrics;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;
import dev.kaloyanyordanov.llmgateway.usage.PricingService;
import dev.kaloyanyordanov.llmgateway.usage.UnknownModelException;
import dev.kaloyanyordanov.llmgateway.usage.UsageService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Orchestrates a streaming request. Validates the model (fail-secure {@code 400}
 * before any streaming begins), serves a cache hit by replaying the cached
 * completion as SSE, or otherwise runs the upstream stream on a virtual thread:
 * forwarding each delta downstream, and on clean completion recording usage and
 * caching the assembled result. A mid-stream abort records a partial usage record
 * (flagged incomplete) and caches nothing — the spend is never lost or
 * double-counted.
 */
@Service
public class StreamingProxyService {

    private static final long TIMEOUT_MS = 60_000L;
    private static final String END_TURN = "end_turn";
    private static final String MESSAGE_TYPE = "message";
    private static final String ASSISTANT_ROLE = "assistant";

    private final StreamingProviderClient streamingClient;
    private final ResponseCacheService cache;
    private final PricingService pricingService;
    private final UsageService usageService;
    private final Executor streamingExecutor;
    private final GatewayMetrics metrics;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "Collaborators are Spring-managed singletons; holding the shared "
                    + "references is intentional DI, not mutable-state exposure.")
    public StreamingProxyService(StreamingProviderClient streamingClient, ResponseCacheService cache,
            PricingService pricingService, UsageService usageService, Executor streamingExecutor,
            GatewayMetrics metrics) {
        this.streamingClient = streamingClient;
        this.cache = cache;
        this.pricingService = pricingService;
        this.usageService = usageService;
        this.streamingExecutor = streamingExecutor;
        this.metrics = metrics;
    }

    /**
     * Starts a streamed response.
     *
     * @param request  the streaming request
     * @param clientId the authenticated client's id
     * @return the SSE emitter the client consumes
     * @throws UnknownModelException if the model is not in the pricing allowlist
     */
    public SseEmitter stream(MessagesRequest request, Long clientId) {
        if (!pricingService.isKnown(request.model())) {
            throw new UnknownModelException(request.model());
        }
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Optional<MessagesResponse> cached = cache.get(request);
        if (cached.isPresent()) {
            replayFromCache(emitter, cached.get());
            return emitter;
        }
        metrics.providerCall();
        streamingExecutor.execute(() -> runStream(request, clientId, emitter));
        return emitter;
    }

    private void runStream(MessagesRequest request, Long clientId, SseEmitter emitter) {
        StreamAccumulator accumulator = new StreamAccumulator();
        try {
            streamingClient.stream(request, accumulator, delta -> sendDelta(emitter, delta));
            StreamResult result = accumulator.toResult();
            if (result.complete()) {
                recordUsage(clientId, request.model(), result, true);
                cache.put(request, toResponse(request, result));
                trySend(emitter, "done", "");
            } else {
                recordUsage(clientId, request.model(), result, false);
                trySend(emitter, "error", "Stream ended before completion");
            }
            emitter.complete();
        } catch (RuntimeException failure) {
            StreamResult partial = accumulator.toResult();
            if (hasUsage(partial)) {
                recordUsage(clientId, request.model(), partial, false);
            }
            trySend(emitter, "error", "Upstream provider error during stream");
            emitter.complete();
        }
    }

    private void replayFromCache(SseEmitter emitter, MessagesResponse cached) {
        try {
            if (cached.content() != null) {
                emitter.send(SseEmitter.event().name("delta").data(cached.content()));
            }
            emitter.send(SseEmitter.event().name("done").data(""));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void recordUsage(Long clientId, String model, StreamResult result, boolean complete) {
        BigDecimal cost = pricingService.cost(model, result.inputTokens(), result.outputTokens());
        usageService.record(clientId, model, result.inputTokens(), result.outputTokens(), cost, complete);
    }

    private static MessagesResponse toResponse(MessagesRequest request, StreamResult result) {
        String model = result.model() != null ? result.model() : request.model();
        return new MessagesResponse(result.id(), MESSAGE_TYPE, ASSISTANT_ROLE, model,
                result.content(), END_TURN, new Usage(result.inputTokens(), result.outputTokens()));
    }

    private static boolean hasUsage(StreamResult result) {
        return result.inputTokens() > 0 || result.outputTokens() > 0 || !result.content().isEmpty();
    }

    private void sendDelta(SseEmitter emitter, String text) {
        try {
            emitter.send(SseEmitter.event().name("delta").data(text));
        } catch (IOException e) {
            throw new StreamAbortedException(e);
        }
    }

    private void trySend(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException ignored) {
            // client already gone; nothing more to deliver
        }
    }
}
