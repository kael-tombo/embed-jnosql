package org.embeddedjnosql.db.config;

import java.util.logging.Logger;

/**
 * Resolves effective administration console and security configurations
 * according to strict precedence rules:
 * <ol>
 *   <li>Explicit Programmatic Configuration</li>
 *   <li>Java System Properties (e.g. {@code embedjnosql.console.port})</li>
 *   <li>Operating System Environment Variables (e.g. {@code EMBEDJNOSQL_CONSOLE_PORT})</li>
 *   <li>Framework Defaults</li>
 * </ol>
 */
public final class ConfigurationResolver {

    private static final Logger logger = Logger.getLogger(ConfigurationResolver.class.getName());

    private ConfigurationResolver() {
    }

    /**
     * Resolves the effective {@link ConsoleConfig} using system properties and environment variables
     * over the provided base configuration.
     */
    public static ConsoleConfig resolveConsoleConfig(ConsoleConfig base) {
        if (base == null) {
            base = ConsoleConfig.disabled();
        }

        ConsoleConfig.Builder builder = ConsoleConfig.builder()
                .enabled(base.enabled())
                .port(base.port())
                .contextPath(base.contextPath())
                .intelligentPort(base.intelligentPort())
                .maxPortAttempts(base.maxPortAttempts())
                .host(base.host())
                .scheme(base.scheme())
                .portRange(base.minPort(), base.maxPort())
                .failIfPreferredPortUnavailable(base.failIfPreferredPortUnavailable())
                .startupTimeoutMs(base.startupTimeoutMs())
                .localhostOnly(base.localhostOnly());

        // Check system properties & env vars
        String enabledVal = getPropertyOrEnv("embedjnosql.console.enabled", "EMBEDJNOSQL_CONSOLE_ENABLED");
        if (enabledVal != null && !enabledVal.isBlank()) {
            builder.enabled(Boolean.parseBoolean(enabledVal.trim()));
        }

        String portVal = getPropertyOrEnv("embedjnosql.console.port", "EMBEDJNOSQL_CONSOLE_PORT");
        if (portVal != null && !portVal.isBlank()) {
            try {
                builder.port(Integer.parseInt(portVal.trim()));
            } catch (NumberFormatException e) {
                logger.warning("Invalid port configuration in system property/env: " + portVal);
            }
        }

        String contextPathVal = getPropertyOrEnv("embedjnosql.console.context-path", "EMBEDJNOSQL_CONSOLE_CONTEXT_PATH");
        if (contextPathVal == null) {
            contextPathVal = getPropertyOrEnv("embedjnosql.console.contextPath", "EMBEDJNOSQL_CONSOLE_CONTEXTPATH");
        }
        if (contextPathVal != null && !contextPathVal.isBlank()) {
            builder.contextPath(contextPathVal.trim());
        }

        String hostVal = getPropertyOrEnv("embedjnosql.console.host", "EMBEDJNOSQL_CONSOLE_HOST");
        if (hostVal != null && !hostVal.isBlank()) {
            builder.host(hostVal.trim());
        }

        String schemeVal = getPropertyOrEnv("embedjnosql.console.scheme", "EMBEDJNOSQL_CONSOLE_SCHEME");
        if (schemeVal != null && !schemeVal.isBlank()) {
            builder.scheme(schemeVal.trim());
        }

        String minPortVal = getPropertyOrEnv("embedjnosql.console.min-port", "EMBEDJNOSQL_CONSOLE_MIN_PORT");
        if (minPortVal == null) {
            minPortVal = getPropertyOrEnv("embedjnosql.console.minPort", "EMBEDJNOSQL_CONSOLE_MINPORT");
        }
        if (minPortVal != null && !minPortVal.isBlank()) {
            try {
                builder.minPort(Integer.parseInt(minPortVal.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String maxPortVal = getPropertyOrEnv("embedjnosql.console.max-port", "EMBEDJNOSQL_CONSOLE_MAX_PORT");
        if (maxPortVal == null) {
            maxPortVal = getPropertyOrEnv("embedjnosql.console.maxPort", "EMBEDJNOSQL_CONSOLE_MAXPORT");
        }
        if (maxPortVal != null && !maxPortVal.isBlank()) {
            try {
                builder.maxPort(Integer.parseInt(maxPortVal.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String intelligentVal = getPropertyOrEnv("embedjnosql.console.intelligent-port", "EMBEDJNOSQL_CONSOLE_INTELLIGENT_PORT");
        if (intelligentVal == null) {
            intelligentVal = getPropertyOrEnv("embedjnosql.console.intelligentPort", "EMBEDJNOSQL_CONSOLE_INTELLIGENTPORT");
        }
        if (intelligentVal != null && !intelligentVal.isBlank()) {
            builder.intelligentPort(Boolean.parseBoolean(intelligentVal.trim()));
        }

        String maxAttemptsVal = getPropertyOrEnv("embedjnosql.console.max-port-attempts", "EMBEDJNOSQL_CONSOLE_MAX_PORT_ATTEMPTS");
        if (maxAttemptsVal == null) {
            maxAttemptsVal = getPropertyOrEnv("embedjnosql.console.maxPortAttempts", "EMBEDJNOSQL_CONSOLE_MAXPORTATTEMPTS");
        }
        if (maxAttemptsVal != null && !maxAttemptsVal.isBlank()) {
            try {
                builder.maxPortAttempts(Integer.parseInt(maxAttemptsVal.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String failOnUnavailableVal = getPropertyOrEnv("embedjnosql.console.fail-if-preferred-port-unavailable",
                "EMBEDJNOSQL_CONSOLE_FAIL_IF_PREFERRED_PORT_UNAVAILABLE");
        if (failOnUnavailableVal != null && !failOnUnavailableVal.isBlank()) {
            builder.failIfPreferredPortUnavailable(Boolean.parseBoolean(failOnUnavailableVal.trim()));
        }

        String localhostOnlyVal = getPropertyOrEnv("embedjnosql.console.localhost-only", "EMBEDJNOSQL_CONSOLE_LOCALHOST_ONLY");
        if (localhostOnlyVal != null && !localhostOnlyVal.isBlank()) {
            builder.localhostOnly(Boolean.parseBoolean(localhostOnlyVal.trim()));
        }

        return builder.build();
    }

    /**
     * Resolves the effective {@link SecurityConfig} using system properties and environment variables
     * over the provided base configuration.
     */
    public static SecurityConfig resolveSecurityConfig(SecurityConfig base) {
        if (base == null) {
            base = SecurityConfig.disabled();
        }

        SecurityConfig.Builder builder = SecurityConfig.builder()
                .authEnabled(base.authEnabled())
                .apiKey(base.apiKey())
                .adminUsername(base.adminUsername())
                .adminPassword(base.adminPassword())
                .corsEnabled(base.corsEnabled())
                .allowedOrigins(base.allowedOrigins())
                .sessionTtlMs(base.sessionTtlMs())
                .ssl(base.sslPort(), base.sslKeystorePath(), base.sslKeystorePassword())
                .csrfEnabled(base.csrfEnabled())
                .rateLimitEnabled(base.rateLimitEnabled())
                .rateLimitRequestsPerMinute(base.rateLimitRequestsPerMinute())
                .bruteForceProtectionEnabled(base.bruteForceProtectionEnabled())
                .maxFailedLoginAttempts(base.maxFailedLoginAttempts())
                .lockoutDurationMs(base.lockoutDurationMs())
                .passwordHashingEnabled(base.passwordHashingEnabled())
                .auditLoggingEnabled(base.auditLoggingEnabled())
                .securityHeadersEnabled(base.securityHeadersEnabled())
                .minTlsVersion(base.minTlsVersion());

        String authVal = getPropertyOrEnv("embedjnosql.security.auth-enabled", "EMBEDJNOSQL_SECURITY_AUTH_ENABLED");
        if (authVal == null) {
            authVal = getPropertyOrEnv("embedjnosql.security.authEnabled", "EMBEDJNOSQL_SECURITY_AUTHENABLED");
        }
        if (authVal != null && !authVal.isBlank()) {
            builder.authEnabled(Boolean.parseBoolean(authVal.trim()));
        }

        String apiKeyVal = getPropertyOrEnv("embedjnosql.security.api-key", "EMBEDJNOSQL_SECURITY_API_KEY");
        if (apiKeyVal == null) {
            apiKeyVal = getPropertyOrEnv("embedjnosql.security.apiKey", "EMBEDJNOSQL_SECURITY_APIKEY");
        }
        if (apiKeyVal != null && !apiKeyVal.isBlank()) {
            builder.apiKey(apiKeyVal.trim());
        }

        String userVal = getPropertyOrEnv("embedjnosql.security.admin-username", "EMBEDJNOSQL_SECURITY_ADMIN_USERNAME");
        if (userVal == null) {
            userVal = getPropertyOrEnv("embedjnosql.security.adminUsername", "EMBEDJNOSQL_SECURITY_ADMINUSERNAME");
        }
        if (userVal != null && !userVal.isBlank()) {
            builder.adminUsername(userVal.trim());
        }

        String passVal = getPropertyOrEnv("embedjnosql.security.admin-password", "EMBEDJNOSQL_SECURITY_ADMIN_PASSWORD");
        if (passVal == null) {
            passVal = getPropertyOrEnv("embedjnosql.security.adminPassword", "EMBEDJNOSQL_SECURITY_ADMINPASSWORD");
        }
        if (passVal != null && !passVal.isBlank()) {
            builder.adminPassword(passVal.trim());
        }

        String corsVal = getPropertyOrEnv("embedjnosql.security.cors-enabled", "EMBEDJNOSQL_SECURITY_CORS_ENABLED");
        if (corsVal == null) {
            corsVal = getPropertyOrEnv("embedjnosql.security.corsEnabled", "EMBEDJNOSQL_SECURITY_CORSENABLED");
        }
        if (corsVal != null && !corsVal.isBlank()) {
            builder.corsEnabled(Boolean.parseBoolean(corsVal.trim()));
        }

        String originsVal = getPropertyOrEnv("embedjnosql.security.allowed-origins", "EMBEDJNOSQL_SECURITY_ALLOWED_ORIGINS");
        if (originsVal == null) {
            originsVal = getPropertyOrEnv("embedjnosql.security.allowedOrigins", "EMBEDJNOSQL_SECURITY_ALLOWEDORIGINS");
        }
        if (originsVal != null && !originsVal.isBlank()) {
            builder.allowedOrigins(originsVal.trim());
        }

        String csrfVal = getPropertyOrEnv("embedjnosql.security.csrf-enabled", "EMBEDJNOSQL_SECURITY_CSRF_ENABLED");
        if (csrfVal != null && !csrfVal.isBlank()) {
            builder.csrfEnabled(Boolean.parseBoolean(csrfVal.trim()));
        }

        String rateLimitVal = getPropertyOrEnv("embedjnosql.security.rate-limit-enabled", "EMBEDJNOSQL_SECURITY_RATE_LIMIT_ENABLED");
        if (rateLimitVal != null && !rateLimitVal.isBlank()) {
            builder.rateLimitEnabled(Boolean.parseBoolean(rateLimitVal.trim()));
        }

        String rpmVal = getPropertyOrEnv("embedjnosql.security.rate-limit-rpm", "EMBEDJNOSQL_SECURITY_RATE_LIMIT_RPM");
        if (rpmVal != null && !rpmVal.isBlank()) {
            try {
                builder.rateLimitRequestsPerMinute(Integer.parseInt(rpmVal.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String bruteForceVal = getPropertyOrEnv("embedjnosql.security.brute-force-enabled", "EMBEDJNOSQL_SECURITY_BRUTE_FORCE_ENABLED");
        if (bruteForceVal != null && !bruteForceVal.isBlank()) {
            builder.bruteForceProtectionEnabled(Boolean.parseBoolean(bruteForceVal.trim()));
        }

        String maxFailedVal = getPropertyOrEnv("embedjnosql.security.max-failed-attempts", "EMBEDJNOSQL_SECURITY_MAX_FAILED_ATTEMPTS");
        if (maxFailedVal != null && !maxFailedVal.isBlank()) {
            try {
                builder.maxFailedLoginAttempts(Integer.parseInt(maxFailedVal.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String secHeadersVal = getPropertyOrEnv("embedjnosql.security.headers-enabled", "EMBEDJNOSQL_SECURITY_HEADERS_ENABLED");
        if (secHeadersVal != null && !secHeadersVal.isBlank()) {
            builder.securityHeadersEnabled(Boolean.parseBoolean(secHeadersVal.trim()));
        }

        return builder.build();
    }

    private static String getPropertyOrEnv(String propKey, String envKey) {
        String val = System.getProperty(propKey);
        if (val != null && !val.isBlank()) {
            return val;
        }
        return System.getenv(envKey);
    }
}
