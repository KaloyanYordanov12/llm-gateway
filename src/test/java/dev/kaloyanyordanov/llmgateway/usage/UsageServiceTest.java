package dev.kaloyanyordanov.llmgateway.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsageServiceTest {

    private final UsageRepository repository = mock(UsageRepository.class);
    private final UsageService service = new UsageService(repository);

    @Test
    void recordPersistsAllFields() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.record(7L, "m", 12, 5, new BigDecimal("0.01"));

        ArgumentCaptor<UsageRecord> captor = ArgumentCaptor.forClass(UsageRecord.class);
        verify(repository).save(captor.capture());
        UsageRecord saved = captor.getValue();
        assertThat(saved.getClientId()).isEqualTo(7L);
        assertThat(saved.getModel()).isEqualTo("m");
        assertThat(saved.getInputTokens()).isEqualTo(12);
        assertThat(saved.getOutputTokens()).isEqualTo(5);
        assertThat(saved.getCost()).isEqualByComparingTo("0.01");
    }

    @Test
    void totalsSumTokensAndCost() {
        when(repository.findByClientId(7L)).thenReturn(List.of(
                new UsageRecord(7L, "m", 10, 3, new BigDecimal("0.05")),
                new UsageRecord(7L, "m", 20, 7, new BigDecimal("0.15"))));

        UsageTotals totals = service.totalsForClient(7L);

        assertThat(totals.clientId()).isEqualTo(7L);
        assertThat(totals.totalInputTokens()).isEqualTo(30);
        assertThat(totals.totalOutputTokens()).isEqualTo(10);
        assertThat(totals.totalCost()).isEqualByComparingTo("0.20");
        assertThat(totals.requestCount()).isEqualTo(2);
    }

    @Test
    void overallTotalsSumAcrossAllClients() {
        when(repository.findAll()).thenReturn(List.of(
                new UsageRecord(1L, "m", 10, 3, new BigDecimal("0.05")),
                new UsageRecord(2L, "m", 20, 7, new BigDecimal("0.15"))));

        UsageTotals totals = service.overallTotals();

        assertThat(totals.clientId()).isNull();
        assertThat(totals.totalInputTokens()).isEqualTo(30);
        assertThat(totals.totalCost()).isEqualByComparingTo("0.20");
        assertThat(totals.requestCount()).isEqualTo(2);
    }

    @Test
    void totalsForClientWithNoUsageAreZero() {
        when(repository.findByClientId(9L)).thenReturn(List.of());

        UsageTotals totals = service.totalsForClient(9L);

        assertThat(totals.totalInputTokens()).isZero();
        assertThat(totals.totalOutputTokens()).isZero();
        assertThat(totals.totalCost()).isEqualByComparingTo("0");
        assertThat(totals.requestCount()).isZero();
    }
}
