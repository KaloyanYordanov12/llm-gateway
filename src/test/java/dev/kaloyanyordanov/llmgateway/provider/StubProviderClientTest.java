package dev.kaloyanyordanov.llmgateway.provider;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.proxy.Message;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesRequest;
import dev.kaloyanyordanov.llmgateway.proxy.MessagesResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubProviderClientTest {

    private final StubProviderClient stub = new StubProviderClient();

    @Test
    void nameIsStub() {
        assertThat(stub.name()).isEqualTo("stub");
    }

    @Test
    void returnsCannedResponseWithoutAnyNetworkCall() {
        MessagesRequest request = new MessagesRequest(
                "claude-3-5-sonnet-20241022", null, List.of(new Message("user", "hi")), null, null, 10, null);

        MessagesResponse response = stub.createMessage(request);

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.stopReason()).isEqualTo("end_turn");
        assertThat(response.content()).contains("demo stub");
        assertThat(response.model()).isEqualTo("claude-3-5-sonnet-20241022");
        assertThat(response.usage().inputTokens()).isEqualTo(8);
        assertThat(response.usage().outputTokens()).isEqualTo(24);
    }
}
