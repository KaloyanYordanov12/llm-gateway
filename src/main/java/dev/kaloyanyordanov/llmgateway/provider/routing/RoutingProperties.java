package dev.kaloyanyordanov.llmgateway.provider.routing;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Config-driven routing rules. Each rule maps a logical model to an ordered list
 * of concrete (provider, model) targets; adding a rule is configuration, not code.
 * An empty rule set preserves v1 direct-model behavior.
 *
 * <p>Rules are a list (not a map) so logical model names containing dots bind
 * cleanly.</p>
 *
 * @param rules the routing rules, in declaration order
 */
@ConfigurationProperties("gateway.routing")
public record RoutingProperties(List<RoutingRule> rules) {

    /** Defensive-copy compact constructor (absent rules become an empty list). */
    public RoutingProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * A single routing rule.
     *
     * @param model          the logical model this rule matches
     * @param costPreference when true, targets are tried cheapest-first (by {@code cost})
     * @param targets        the ordered concrete targets to try
     */
    public record RoutingRule(
            String model,
            @DefaultValue("false") boolean costPreference,
            List<RoutingTarget> targets) {

        /** Defensive-copy compact constructor (absent targets become an empty list). */
        public RoutingRule {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }

    /**
     * A concrete routing target.
     *
     * @param provider the registered provider name
     * @param model    the concrete upstream model id
     * @param cost     optional routing weight used only for cost-preference ordering
     */
    public record RoutingTarget(String provider, String model, BigDecimal cost) {
    }
}
