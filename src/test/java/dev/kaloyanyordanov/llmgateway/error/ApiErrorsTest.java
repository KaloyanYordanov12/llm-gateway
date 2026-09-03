package dev.kaloyanyordanov.llmgateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiErrorsTest {

    @Test
    void ofBuildsEnvelopeWithGivenRequestId() {
        ApiError error = ApiErrors.of("authentication_error", "missing key", "req-123");

        assertThat(error.type()).isEqualTo("error");
        assertThat(error.error().type()).isEqualTo("authentication_error");
        assertThat(error.error().message()).isEqualTo("missing key");
        assertThat(error.requestId()).isEqualTo("req-123");
    }

    @Test
    void ofGeneratesAValidUuidRequestIdWhenNoneSupplied() {
        ApiError error = ApiErrors.of("invalid_request_error", "bad body");

        assertThat(error.type()).isEqualTo("error");
        assertThat(error.error().type()).isEqualTo("invalid_request_error");
        assertThat(error.error().message()).isEqualTo("bad body");
        // Must be a parseable UUID.
        assertThat(UUID.fromString(error.requestId())).isNotNull();
    }

    @Test
    void generatedRequestIdsAreUnique() {
        ApiError a = ApiErrors.of("api_error", "boom");
        ApiError b = ApiErrors.of("api_error", "boom");

        assertThat(a.requestId()).isNotEqualTo(b.requestId());
    }

    @Test
    void envelopeTypeConstantIsError() {
        assertThat(ApiErrors.ENVELOPE_TYPE).isEqualTo("error");
    }
}
