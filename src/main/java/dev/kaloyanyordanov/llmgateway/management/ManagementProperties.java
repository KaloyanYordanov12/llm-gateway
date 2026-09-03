package dev.kaloyanyordanov.llmgateway.management;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Management-API settings. The admin key guards all {@code /api/*} routes; the
 * default is an obviously-fake dev placeholder and must be overridden via env in
 * production.
 *
 * @param key the admin key required on the {@code x-admin-key} header
 */
@ConfigurationProperties("gateway.admin")
public record ManagementProperties(@DefaultValue("dev-admin-key") String key) {
}
