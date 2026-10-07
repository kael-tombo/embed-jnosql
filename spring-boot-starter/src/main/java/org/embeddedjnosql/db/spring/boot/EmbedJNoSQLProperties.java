package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedjnosql")
public class EmbedJNoSQLProperties {

    private boolean enabled = true;
    private StorageEngineType storageEngine = StorageEngineType.IN_MEMORY;
    private String dataDir = "data";
    private boolean autoFlush = true;
    private int flushIntervalMs = 1000;

    private ConsoleProperties console = new ConsoleProperties();
    private SecurityProperties security = new SecurityProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public StorageEngineType getStorageEngine() {
        return storageEngine;
    }

    public void setStorageEngine(StorageEngineType storageEngine) {
        this.storageEngine = storageEngine;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public boolean isAutoFlush() {
        return autoFlush;
    }

    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }

    public int getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(int flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public ConsoleProperties getConsole() {
        return console;
    }

    public void setConsole(ConsoleProperties console) {
        this.console = console;
    }

    public SecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(SecurityProperties security) {
        this.security = security;
    }

    public static class ConsoleProperties {
        private boolean enabled = false;
        private int port = 9090;
        private String contextPath = "/";
        private boolean intelligentPort = true;
        private int maxPortAttempts = 50;
        private String host = "127.0.0.1";
        private String scheme = "http";
        private int minPort = -1;
        private int maxPort = -1;
        private boolean failIfPreferredPortUnavailable = false;
        private long startupTimeoutMs = 10000L;
        private boolean localhostOnly = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getContextPath() {
            return contextPath;
        }

        public void setContextPath(String contextPath) {
            this.contextPath = contextPath;
        }

        public boolean isIntelligentPort() {
            return intelligentPort;
        }

        public void setIntelligentPort(boolean intelligentPort) {
            this.intelligentPort = intelligentPort;
        }

        public int getMaxPortAttempts() {
            return maxPortAttempts;
        }

        public void setMaxPortAttempts(int maxPortAttempts) {
            this.maxPortAttempts = maxPortAttempts;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public int getMinPort() {
            return minPort;
        }

        public void setMinPort(int minPort) {
            this.minPort = minPort;
        }

        public int getMaxPort() {
            return maxPort;
        }

        public void setMaxPort(int maxPort) {
            this.maxPort = maxPort;
        }

        public boolean isFailIfPreferredPortUnavailable() {
            return failIfPreferredPortUnavailable;
        }

        public void setFailIfPreferredPortUnavailable(boolean failIfPreferredPortUnavailable) {
            this.failIfPreferredPortUnavailable = failIfPreferredPortUnavailable;
        }

        public long getStartupTimeoutMs() {
            return startupTimeoutMs;
        }

        public void setStartupTimeoutMs(long startupTimeoutMs) {
            this.startupTimeoutMs = startupTimeoutMs;
        }

        public boolean isLocalhostOnly() {
            return localhostOnly;
        }

        public void setLocalhostOnly(boolean localhostOnly) {
            this.localhostOnly = localhostOnly;
        }
    }

    public static class SecurityProperties {
        private boolean authEnabled = false;
        private String apiKey;
        private String adminUsername = "admin";
        private String adminPassword;
        private boolean corsEnabled = false;
        private String allowedOrigins = null;
        private long sessionTtlMs = 1800000L;
        private int sslPort = 0;
        private String sslKeystorePath;
        private String sslKeystorePassword;
        private boolean csrfEnabled = false;
        private boolean rateLimitEnabled = true;
        private int rateLimitRequestsPerMinute = 120;
        private boolean bruteForceProtectionEnabled = true;
        private int maxFailedLoginAttempts = 5;
        private long lockoutDurationMs = 900000L;
        private boolean passwordHashingEnabled = false;
        private boolean auditLoggingEnabled = true;
        private boolean securityHeadersEnabled = true;
        private String minTlsVersion = "TLSv1.2";

        public boolean isAuthEnabled() {
            return authEnabled;
        }

        public void setAuthEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public boolean isCorsEnabled() {
            return corsEnabled;
        }

        public void setCorsEnabled(boolean corsEnabled) {
            this.corsEnabled = corsEnabled;
        }

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public long getSessionTtlMs() {
            return sessionTtlMs;
        }

        public void setSessionTtlMs(long sessionTtlMs) {
            this.sessionTtlMs = sessionTtlMs;
        }

        public int getSslPort() {
            return sslPort;
        }

        public void setSslPort(int sslPort) {
            this.sslPort = sslPort;
        }

        public String getSslKeystorePath() {
            return sslKeystorePath;
        }

        public void setSslKeystorePath(String sslKeystorePath) {
            this.sslKeystorePath = sslKeystorePath;
        }

        public String getSslKeystorePassword() {
            return sslKeystorePassword;
        }

        public void setSslKeystorePassword(String sslKeystorePassword) {
            this.sslKeystorePassword = sslKeystorePassword;
        }

        public boolean isCsrfEnabled() {
            return csrfEnabled;
        }

        public void setCsrfEnabled(boolean csrfEnabled) {
            this.csrfEnabled = csrfEnabled;
        }

        public boolean isRateLimitEnabled() {
            return rateLimitEnabled;
        }

        public void setRateLimitEnabled(boolean rateLimitEnabled) {
            this.rateLimitEnabled = rateLimitEnabled;
        }

        public int getRateLimitRequestsPerMinute() {
            return rateLimitRequestsPerMinute;
        }

        public void setRateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
            this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
        }

        public boolean isBruteForceProtectionEnabled() {
            return bruteForceProtectionEnabled;
        }

        public void setBruteForceProtectionEnabled(boolean bruteForceProtectionEnabled) {
            this.bruteForceProtectionEnabled = bruteForceProtectionEnabled;
        }

        public int getMaxFailedLoginAttempts() {
            return maxFailedLoginAttempts;
        }

        public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) {
            this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        }

        public long getLockoutDurationMs() {
            return lockoutDurationMs;
        }

        public void setLockoutDurationMs(long lockoutDurationMs) {
            this.lockoutDurationMs = lockoutDurationMs;
        }

        public boolean isPasswordHashingEnabled() {
            return passwordHashingEnabled;
        }

        public void setPasswordHashingEnabled(boolean passwordHashingEnabled) {
            this.passwordHashingEnabled = passwordHashingEnabled;
        }

        public boolean isAuditLoggingEnabled() {
            return auditLoggingEnabled;
        }

        public void setAuditLoggingEnabled(boolean auditLoggingEnabled) {
            this.auditLoggingEnabled = auditLoggingEnabled;
        }

        public boolean isSecurityHeadersEnabled() {
            return securityHeadersEnabled;
        }

        public void setSecurityHeadersEnabled(boolean securityHeadersEnabled) {
            this.securityHeadersEnabled = securityHeadersEnabled;
        }

        public String getMinTlsVersion() {
            return minTlsVersion;
        }

        public void setMinTlsVersion(String minTlsVersion) {
            this.minTlsVersion = minTlsVersion;
        }
    }
}
