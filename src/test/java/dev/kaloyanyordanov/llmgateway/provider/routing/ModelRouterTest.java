package dev.kaloyanyordanov.llmgateway.provider.routing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kaloyanyordanov.llmgateway.provider.routing.RoutingProperties.RoutingRule;
import dev.kaloyanyordanov.llmgateway.provider.routing.RoutingProperties.RoutingTarget;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRouterTest {

    private static ModelRouter router(RoutingRule... rules) {
        return new ModelRouter(new RoutingProperties(List.of(rules)));
    }

    @Test
    void modelWithNoRuleResolvesEmpty() {
        ModelRouter router = router();

        assertThat(router.resolve("anything")).isEmpty();
    }

    @Test
    void ruleResolvesLogicalToConcreteTargetsInDeclaredOrder() {
        ModelRouter router = router(new RoutingRule("fast", false, List.of(
                new RoutingTarget("anthropic", "claude-3-5-haiku-20241022", null),
                new RoutingTarget("openai", "gpt-4o-mini", null))));

        assertThat(router.resolve("fast")).hasValueSatisfying(targets -> assertThat(targets)
                .containsExactly(
                        new RouteTarget("anthropic", "claude-3-5-haiku-20241022"),
                        new RouteTarget("openai", "gpt-4o-mini")));
    }

    @Test
    void costPreferenceOrdersTargetsCheapestFirst() {
        ModelRouter router = router(new RoutingRule("fast", true, List.of(
                new RoutingTarget("anthropic", "claude-3-5-haiku-20241022", new BigDecimal("0.25")),
                new RoutingTarget("openai", "gpt-4o-mini", new BigDecimal("0.15")))));

        assertThat(router.resolve("fast")).hasValueSatisfying(targets -> assertThat(targets)
                .containsExactly(
                        new RouteTarget("openai", "gpt-4o-mini"),
                        new RouteTarget("anthropic", "claude-3-5-haiku-20241022")));
    }

    @Test
    void costPreferenceSortsMissingCostLast() {
        ModelRouter router = router(new RoutingRule("fast", true, List.of(
                new RoutingTarget("a", "m-null", null),
                new RoutingTarget("b", "m-cheap", new BigDecimal("0.10")))));

        assertThat(router.resolve("fast")).hasValueSatisfying(targets -> assertThat(targets)
                .containsExactly(new RouteTarget("b", "m-cheap"), new RouteTarget("a", "m-null")));
    }

    @Test
    void firstRuleWinsOnDuplicateLogicalModel() {
        ModelRouter router = router(
                new RoutingRule("fast", false, List.of(new RoutingTarget("openai", "first", null))),
                new RoutingRule("fast", false, List.of(new RoutingTarget("anthropic", "second", null))));

        assertThat(router.resolve("fast")).hasValueSatisfying(targets ->
                assertThat(targets).containsExactly(new RouteTarget("openai", "first")));
    }

    @Test
    void referencedProvidersListsEveryTargetProvider() {
        ModelRouter router = router(
                new RoutingRule("fast", false, List.of(
                        new RoutingTarget("openai", "gpt-4o-mini", null),
                        new RoutingTarget("anthropic", "claude-haiku", null))),
                new RoutingRule("smart", false, List.of(new RoutingTarget("anthropic", "claude-opus", null))));

        assertThat(router.referencedProviders()).containsExactlyInAnyOrder("openai", "anthropic");
    }
}
