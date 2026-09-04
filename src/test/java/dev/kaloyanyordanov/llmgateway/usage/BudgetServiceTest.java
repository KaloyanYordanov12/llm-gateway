package dev.kaloyanyordanov.llmgateway.usage;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.kaloyanyordanov.llmgateway.metrics.GatewayMetrics;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetServiceTest {

    private static final long CLIENT_ID = 7L;

    private final UsageService usageService = mock(UsageService.class);
    private final GatewayMetrics metrics = mock(GatewayMetrics.class);
    private final BudgetService service = new BudgetService(usageService, metrics);

    private void stubSpend(String spent) {
        when(usageService.totalsForClient(CLIENT_ID))
                .thenReturn(new UsageTotals(CLIENT_ID, 0, 0, new BigDecimal(spent), 0));
    }

    @Test
    void noBudgetNeverRejectsAndSkipsTheSpendLookup() {
        assertThatCode(() -> service.enforce(CLIENT_ID, null)).doesNotThrowAnyException();

        // With no cap there is no reason to query spend at all.
        verifyNoInteractions(usageService);
    }

    @Test
    void spendBelowBudgetPasses() {
        stubSpend("0.50");

        assertThatCode(() -> service.enforce(CLIENT_ID, new BigDecimal("1.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void spendExactlyAtBudgetIsRejectedAtTheBoundary() {
        stubSpend("1.00");

        assertThatThrownBy(() -> service.enforce(CLIENT_ID, new BigDecimal("1.00")))
                .isInstanceOf(BudgetExceededException.class);
        verify(metrics).budgetRejection(CLIENT_ID);
    }

    @Test
    void aPassingRequestIsNotCountedAsABudgetRejection() {
        stubSpend("0.50");

        service.enforce(CLIENT_ID, new BigDecimal("1.00"));

        verify(metrics, never()).budgetRejection(CLIENT_ID);
    }

    @Test
    void spendOverBudgetIsRejected() {
        stubSpend("1.50");

        assertThatThrownBy(() -> service.enforce(CLIENT_ID, new BigDecimal("1.00")))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("1.5")
                .hasMessageContaining("1.0");
    }

    @Test
    void spendJustUnderBudgetPassesAtTheBoundary() {
        stubSpend("0.99999999");

        assertThatCode(() -> service.enforce(CLIENT_ID, new BigDecimal("1.00")))
                .doesNotThrowAnyException();
    }
}
