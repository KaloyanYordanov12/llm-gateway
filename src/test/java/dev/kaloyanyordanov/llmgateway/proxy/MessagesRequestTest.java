package dev.kaloyanyordanov.llmgateway.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessagesRequestTest {

    @Test
    void nullListsArePreserved() {
        MessagesRequest req = new MessagesRequest("m", null, null, null, null, 10, null);

        assertThat(req.messages()).isNull();
        assertThat(req.stopSequences()).isNull();
    }

    @Test
    void listsAreDefensivelyCopiedOnConstruction() {
        List<Message> messages = new ArrayList<>(List.of(new Message("user", "hi")));
        List<String> stops = new ArrayList<>(List.of("STOP"));

        MessagesRequest req = new MessagesRequest("m", null, messages, null, null, 10, stops);

        // Mutating the source lists must not affect the stored request.
        messages.add(new Message("user", "leak"));
        stops.add("LEAK");

        assertThat(req.messages()).hasSize(1);
        assertThat(req.stopSequences()).containsExactly("STOP");
    }

    @Test
    void returnedListsAreUnmodifiable() {
        MessagesRequest req = new MessagesRequest(
                "m", null, List.of(new Message("user", "hi")), null, null, 10, List.of("STOP"));

        assertThatThrownBy(() -> req.messages().add(new Message("user", "x")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> req.stopSequences().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
