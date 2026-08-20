package zw.co.hyperfeeds.calculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeedCalculatorServiceTests {
    @Test
    void calculatesDailyFeedAndRoundsPurchaseBagsUp() {
        var phase = new FeedCalculatorService.PhaseDefinition(
                "In lay", "DAILY", new BigDecimal("0.1100"), 30, true,
                "demo", "HF-WEB-LAYER-MASH", "Layer Mash", null);

        var result = FeedCalculatorService.calculatePhase(phase, 100, 30, new BigDecimal("50"));

        assertThat(result.totalKg()).isEqualByComparingTo("330.00");
        assertThat(result.exactBags()).isEqualByComparingTo("6.60");
        assertThat(result.bagsToBuy()).isEqualTo(7);
    }

    @Test
    void fixedPhaseDoesNotMultiplyByRequestedDays() {
        var phase = new FeedCalculatorService.PhaseDefinition(
                "Starter", "FIXED", new BigDecimal("0.5000"), 1, false,
                "demo", "HF-WEB-BROILER-STARTER-CRUMBS", "Starter", null);

        var result = FeedCalculatorService.calculatePhase(phase, 100, 35, new BigDecimal("50"));

        assertThat(result.totalKg()).isEqualByComparingTo("50.00");
        assertThat(result.bagsToBuy()).isEqualTo(1);
    }
}
