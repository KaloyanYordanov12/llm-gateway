package dev.kaloyanyordanov.llmgateway.provider.openai;

import dev.kaloyanyordanov.llmgateway.provider.ProviderClient;
import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import dev.kaloyanyordanov.llmgateway.proxy.Usage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link ProviderClient} for OpenAI's chat-completions API. Translates the
 * gateway's normalized {@link MessagesRequest}/{@link MessagesResponse} to and
 * from OpenAI's shape over a blocking {@link RestClient}, sending the key as a
 * {@code Bearer} token. Same pattern as the Anthropic adapter.
 */
public class OpenAiProviderClient implements ProviderClient {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SYSTEM_ROLE = "system";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String MESSAGE_TYPE = "message";

    private final String name;
    private final RestClient restClient;
    private final String apiKey;

    /**
     * @param name       provider name
     * @param restClient RestClient pre-configured with the OpenAI base URL
     * @param apiKey     OpenAI API key for the {@code Authorization: Bearer} header
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
            justification = "RestClient is an immutable, thread-safe Spring component; "
                    + "holding the shared instance is intentional, not mutable-state exposure.")
    public OpenAiProviderClient(String name, RestClient restClient, String apiKey) {
        this.name = name;
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public MessagesResponse createMessage(MessagesRequest request) {
        OpenAiChatResponse response = restClient.post()
                .uri(CHAT_PATH)
                .header(AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toOpenAiRequest(request))
                .retrieve()
                .body(OpenAiChatResponse.class);
        return toMessagesResponse(response, request.model());
    }

    static OpenAiChatRequest toOpenAiRequest(MessagesRequest request) {
        List<OpenAiChatMessage> messages = new ArrayList<>();
        if (request.system() != null) {
            messages.add(new OpenAiChatMessage(SYSTEM_ROLE, request.system()));
        }
        if (request.messages() != null) {
            for (Message message : request.messages()) {
                messages.add(new OpenAiChatMessage(message.role(), message.content()));
            }
        }
        return new OpenAiChatRequest(request.model(), messages, request.maxTokens(),
                request.temperature(), request.topP(), request.stopSequences());
    }

    static MessagesResponse toMessagesResponse(OpenAiChatResponse response, String requestedModel) {
        OpenAiChoice choice = firstChoice(response);
        OpenAiChatMessage message = choice != null ? choice.message() : null;
        String content = message != null ? message.content() : null;
        String role = message != null ? message.role() : ASSISTANT_ROLE;
        String stopReason = choice != null ? choice.finishReason() : null;
        String model = response.model() != null ? response.model() : requestedModel;
        return new MessagesResponse(
                response.id(), MESSAGE_TYPE, role, model, content, stopReason, toUsage(response.usage()));
    }

    private static OpenAiChoice firstChoice(OpenAiChatResponse response) {
        List<OpenAiChoice> choices = response.choices();
        return choices == null || choices.isEmpty() ? null : choices.get(0);
    }

    private static Usage toUsage(OpenAiUsage usage) {
        return usage == null ? null : new Usage(usage.promptTokens(), usage.completionTokens());
    }
}
