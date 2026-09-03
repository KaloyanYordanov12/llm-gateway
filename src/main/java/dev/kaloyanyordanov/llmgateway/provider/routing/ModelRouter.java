package dev.kaloyanyordanov.llmgateway.provider.routing;

import dev.kaloyanyordanov.llmgateway.provider.routing.RoutingProperties.RoutingRule;
import dev.kaloyanyordanov.llmgateway.provider.routing.RoutingProperties.RoutingTarget;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Pure, deterministic resolver from a logical model to an ordered list of
 * concrete {@link RouteTarget}s, driven entirely by {@link RoutingProperties}.
 * A model with no rule resolves to {@link Optional#empty()}, which callers treat
 * as "use the v1 default path" — so behavior is unchanged when no rules exist.
 */
@Component
public class ModelRouter {

    private final Map<String, RoutingRule> rulesByModel;

    public ModelRouter(RoutingProperties properties) {
        Map<String, RoutingRule> map = new LinkedHashMap<>();
        for (RoutingRule rule : properties.rules()) {
            map.putIfAbsent(rule.model(), rule);
        }
        this.rulesByModel = Map.copyOf(map);
    }

    /**
     * Resolves a logical model to its ordered concrete targets.
     *
     * @param logicalModel the client-requested model
     * @return the ordered targets, or empty if no rule matches (use the default path)
     */
    public Optional<List<RouteTarget>> resolve(String logicalModel) {
        RoutingRule rule = rulesByModel.get(logicalModel);
        if (rule == null) {
            return Optional.empty();
        }
        List<RoutingTarget> targets = rule.costPreference() ? sortedByCost(rule.targets()) : rule.targets();
        return Optional.of(targets.stream()
                .map(target -> new RouteTarget(target.provider(), target.model()))
                .toList());
    }

    /**
     * @return every provider name referenced by any rule (for startup validation)
     */
    public Set<String> referencedProviders() {
        return rulesByModel.values().stream()
                .flatMap(rule -> rule.targets().stream())
                .map(RoutingTarget::provider)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<RoutingTarget> sortedByCost(List<RoutingTarget> targets) {
        // Stable sort by cost ascending; targets without a cost sort last.
        return targets.stream()
                .sorted(Comparator.comparing(
                        RoutingTarget::cost, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
