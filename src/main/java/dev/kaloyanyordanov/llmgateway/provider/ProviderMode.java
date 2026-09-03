package dev.kaloyanyordanov.llmgateway.provider;

/**
 * Selects which provider the gateway proxies to.
 *
 * <p>{@link #LIVE} is the default on purpose. The dangerous state is silent real
 * spend, so the fail-secure default is the real provider (guarded in dev by a
 * stub base URL and no key), while {@link #DEMO} — which can never spend — is a
 * loud, explicit opt-in for a public box. An unset value defaults to {@code LIVE};
 * an unrecognized value fails binding at startup rather than guessing.</p>
 */
public enum ProviderMode {
    /** Proxy to the real (resilient) provider. */
    LIVE,
    /** Proxy to the no-network stub provider — cannot cost anything. */
    DEMO
}
