package dev.kaloyanyordanov.llmgateway.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiProviderClientMappingTest {

    @Test
    void requestFoldsSystemPromptAndMapsFields() {
        MessagesRequest request = new MessagesRequest(
                "gpt-x", "be brief", List.of(new Message("user", "hi")), 0.5, 0.9, 100, List.of("STOP"));

        OpenAiChatRequest openAi = OpenAiProviderClient.toOpenAiRequest(request);

        assertThat(openAi.model()).isEqualTo("gpt-x");
        assertThat(openAi.messages()).hasSize(2);
        assertThat(openAi.messages().get(0).role()).isEqualTo("system");
        assertThat(openAi.messages().get(0).content()).isEqualTo("be brief");
        assertThat(openAi.messages().get(1).role()).isEqualTo("user");
        assertThat(openAi.messages().get(1).content()).isEqualTo("hi");
        assertThat(openAi.maxTokens()).isEqualTo(100);
        assertThat(openAi.temperature()).isEqualTo(0.5);
        assertThat(openAi.topP()).isEqualTo(0.9);
        assertThat(openAi.stop()).containsExactly("STOP");
    }

    @Test
    void requestWithoutSystemHasNoSystemMessage() {
        MessagesRequest request =
                new MessagesRequest("gpt-x", null, List.of(new Message("user", "hi")), null, null, 10, null);

        OpenAiChatRequest openAi = OpenAiProviderClient.toOpenAiRequest(request);

        assertThat(openAi.messages()).singleElement()
                .satisfies(m -> assertThat(m.role()).isEqualTo("user"));
    }

    @Test
    void responseMapsToNormalizedShape() {
        OpenAiChatResponse response = new OpenAiChatResponse("cmpl-1", "gpt-x",
                List.of(new OpenAiChoice(0, new OpenAiChatMessage("assistant", "hello"), "stop")),
                new OpenAiUsage(12, 5, 17));

        MessagesResponse mapped = OpenAiProviderClient.toMessagesResponse(response, "gpt-x");

        assertThat(mapped.id()).isEqualTo("cmpl-1");
        assertThat(mapped.type()).isEqualTo("message");
        assertThat(mapped.role()).isEqualTo("assistant");
        assertThat(mapped.content()).isEqualTo("hello");
        assertThat(mapped.model()).isEqualTo("gpt-x");
        assertThat(mapped.stopReason()).isEqualTo("stop");
        assertThat(mapped.usage().inputTokens()).isEqualTo(12);
        assertThat(mapped.usage().outputTokens()).isEqualTo(5);
    }

    @Test
    void responseWithNoChoicesDegradesGracefully() {
        OpenAiChatResponse response = new OpenAiChatResponse("id", "gpt-x", List.of(), null);

        MessagesResponse mapped = OpenAiProviderClient.toMessagesResponse(response, "req-model");

        assertThat(mapped.content()).isNull();
        assertThat(mapped.role()).isEqualTo("assistant");
        assertThat(mapped.stopReason()).isNull();
        assertThat(mapped.usage()).isNull();
    }

    @Test
    void responseWithoutModelFallsBackToRequestedModel() {
        OpenAiChatResponse response = new OpenAiChatResponse("id", null,
                List.of(new OpenAiChoice(0, new OpenAiChatMessage("assistant", "x"), "stop")), null);

        MessagesResponse mapped = OpenAiProviderClient.toMessagesResponse(response, "fallback-model");

        assertThat(mapped.model()).isEqualTo("fallback-model");
    }
}
