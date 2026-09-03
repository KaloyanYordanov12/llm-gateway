package dev.kaloyanyordanov.llmgateway.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(new PricingProperties(Map.of(
            "m", new ModelRate(new BigDecimal("0.003"), new BigDecimal("0.015")))));

    @Test
    void costIsInputPlusOutputPerThousandTokens() {
        // 1000 in * 0.003/1k + 1000 out * 0.015/1k = 0.018
        assertThat(pricing.cost("m", 1000, 1000)).isEqualByComparingTo("0.018");
    }

    @Test
    void costHandlesFractionalThousands() {
        // 1500 in * 0.003/1k + 500 out * 0.015/1k = 0.0045 + 0.0075 = 0.012
        assertThat(pricing.cost("m", 1500, 500)).isEqualByComparingTo("0.012");
    }

    @Test
    void unknownModelThrows() {
        assertThatThrownBy(() -> pricing.cost("nope", 10, 10))
                .isInstanceOf(UnknownModelException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void isKnownReflectsAllowlist() {
        assertThat(pricing.isKnown("m")).isTrue();
        assertThat(pricing.isKnown("other")).isFalse();
        assertThat(pricing.isKnown(null)).isFalse();
    }
}
