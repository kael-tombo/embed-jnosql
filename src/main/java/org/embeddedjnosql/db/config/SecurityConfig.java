package org.embeddedjnosql.db.config;

/**
 * Configuration for authentication, authorization, session management, CORS, CSRF,
 * rate limiting, brute-force protection, audit logging, security headers, and SSL/TLS
 * in the embedded EmbedJNoSQL Administration Console.
 */
public record SecurityConfig(
        boolean authEnabled,
        String apiKey,
        String adminUsername,
        String adminPassword,
        boolean corsEnabled,
        String allowedOrigins,
        long sessionTtlMs,
        int sslPort,
        String sslKeystorePath,
        String sslKeystorePassword,
        boolean csrfEnabled,
        boolean rateLimitEnabled,
        int rateLimitRequestsPerMinute,
        boolean bruteForceProtectionEnabled,
        int maxFailedLoginAttempts,
        long lockoutDurationMs,
        boolean passwordHashingEnabled,
        boolean auditLoggingEnabled,
        boolean securityHeadersEnabled,
        String minTlsVersion
) {
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final long DEFAULT_SESSION_TTL_MS = 30 * 60 * 1000L; // 30 minutes
    public static final int DEFAULT_RATE_LIMIT_RPM = 120;
    public static final int DEFAULT_MAX_FAILED_LOGINS = 5;
    public static final long DEFAULT_LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes
    public static final String DEFAULT_MIN_TLS = "TLSv1.2";

    /**
     * Backward-compatible 10-argument constructor.
     */
    public SecurityConfig(
            boolean authEnabled,
            String apiKey,
            String adminUsername,
            String adminPassword,
            boolean corsEnabled,
            String allowedOrigins,
            long sessionTtlMs,
            int sslPort,
            String sslKeystorePath,
            String sslKeystorePassword
    ) {
        this(
                authEnabled,
                apiKey,
                adminUsername,
                adminPassword,
                corsEnabled,
                allowedOrigins,
                sessionTtlMs,
                sslPort,
                sslKeystorePath,
                sslKeystorePassword,
                false,                       // csrfEnabled (opt-in)
                true,                        // rateLimitEnabled
                DEFAULT_RATE_LIMIT_RPM,       // rateLimitRequestsPerMinute
                true,                        // bruteForceProtectionEnabled
                DEFAULT_MAX_FAILED_LOGINS,   // maxFailedLoginAttempts
                DEFAULT_LOCKOUT_DURATION_MS, // lockoutDurationMs
                false,                       // passwordHashingEnabled
                true,                        // auditLoggingEnabled
                true,                        // securityHeadersEnabled
                DEFAULT_MIN_TLS              // minTlsVersion
        );
    }

    public static SecurityConfig disabled() {
        return new SecurityConfig(
                false,
                null,
                DEFAULT_ADMIN_USERNAME,
                null,
                false, // secure default: CORS disabled
                null,
                DEFAULT_SESSION_TTL_MS,
                0,
                null,
                null,
                false, // CSRF disabled when auth is disabled
                false, // rate limit disabled when console security disabled
                DEFAULT_RATE_LIMIT_RPM,
                false,
                DEFAULT_MAX_FAILED_LOGINS,
                DEFAULT_LOCKOUT_DURATION_MS,
                false,
                true,
                true,
                DEFAULT_MIN_TLS
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean authEnabled = false;
        private String apiKey = null;
        private String adminUsername = DEFAULT_ADMIN_USERNAME;
        private String adminPassword = null;
        private boolean corsEnabled = false; // secure default
        private String allowedOrigins = null;
        private long sessionTtlMs = DEFAULT_SESSION_TTL_MS;
        private int sslPort = 0;
        private String sslKeystorePath = null;
        private String sslKeystorePassword = null;
        private boolean csrfEnabled = false;
        private boolean rateLimitEnabled = true;
        private int rateLimitRequestsPerMinute = DEFAULT_RATE_LIMIT_RPM;
        private boolean bruteForceProtectionEnabled = true;
        private int maxFailedLoginAttempts = DEFAULT_MAX_FAILED_LOGINS;
        private long lockoutDurationMs = DEFAULT_LOCKOUT_DURATION_MS;
        private boolean passwordHashingEnabled = false;
        private boolean auditLoggingEnabled = true;
        private boolean securityHeadersEnabled = true;
        private String minTlsVersion = DEFAULT_MIN_TLS;

        public Builder authEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            if (apiKey != null && !apiKey.isBlank()) {
                this.authEnabled = true;
            }
            return this;
        }

        public Builder adminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
            return this;
        }

        public Builder adminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
            if (adminPassword != null && !adminPassword.isBlank()) {
                this.authEnabled = true;
            }
            return this;
        }

        public Builder corsEnabled(boolean corsEnabled) {
            this.corsEnabled = corsEnabled;
            return this;
        }

        public Builder allowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
            return this;
        }

        public Builder sessionTtlMs(long sessionTtlMs) {
            this.sessionTtlMs = sessionTtlMs;
            return this;
        }

        public Builder ssl(int sslPort, String keystorePath, String keystorePassword) {
            this.sslPort = sslPort;
            this.sslKeystorePath = keystorePath;
            this.sslKeystorePassword = keystorePassword;
            return this;
        }

        public Builder csrfEnabled(boolean csrfEnabled) {
            this.csrfEnabled = csrfEnabled;
            return this;
        }

        public Builder rateLimitEnabled(boolean rateLimitEnabled) {
            this.rateLimitEnabled = rateLimitEnabled;
            return this;
        }

        public Builder rateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
            this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
            return this;
        }

        public Builder bruteForceProtectionEnabled(boolean bruteForceProtectionEnabled) {
            this.bruteForceProtectionEnabled = bruteForceProtectionEnabled;
            return this;
        }

        public Builder maxFailedLoginAttempts(int maxFailedLoginAttempts) {
            this.maxFailedLoginAttempts = maxFailedLoginAttempts;
            return this;
        }

        public Builder lockoutDurationMs(long lockoutDurationMs) {
            this.lockoutDurationMs = lockoutDurationMs;
            return this;
        }

        public Builder passwordHashingEnabled(boolean passwordHashingEnabled) {
            this.passwordHashingEnabled = passwordHashingEnabled;
            return this;
        }

        public Builder auditLoggingEnabled(boolean auditLoggingEnabled) {
            this.auditLoggingEnabled = auditLoggingEnabled;
            return this;
        }

        public Builder securityHeadersEnabled(boolean securityHeadersEnabled) {
            this.securityHeadersEnabled = securityHeadersEnabled;
            return this;
        }

        public Builder minTlsVersion(String minTlsVersion) {
            this.minTlsVersion = minTlsVersion;
            return this;
        }

        public SecurityConfig build() {
            return new SecurityConfig(
                    authEnabled,
                    apiKey,
                    adminUsername,
                    adminPassword,
                    corsEnabled,
                    allowedOrigins,
                    sessionTtlMs,
                    sslPort,
                    sslKeystorePath,
                    sslKeystorePassword,
                    csrfEnabled,
                    rateLimitEnabled,
                    rateLimitRequestsPerMinute,
                    bruteForceProtectionEnabled,
                    maxFailedLoginAttempts,
                    lockoutDurationMs,
                    passwordHashingEnabled,
                    auditLoggingEnabled,
                    securityHeadersEnabled,
                    minTlsVersion
            );
        }
    }
}
