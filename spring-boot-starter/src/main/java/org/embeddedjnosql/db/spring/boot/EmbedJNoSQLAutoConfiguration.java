package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbedJNoSQLProperties.class)
@ConditionalOnProperty(prefix = "embedjnosql", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmbedJNoSQLAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EmbedJNoSQLAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public EmbedJNoSQL embeddedjnosqlDB(EmbedJNoSQLProperties properties) {
        var builder = EmbedJNoSQL.embed()
                .storageEngine(properties.getStorageEngine())
                .autoFlush(properties.isAutoFlush())
                .flushIntervalMs(properties.getFlushIntervalMs());

        if (properties.getDataDir() != null && !properties.getDataDir().isBlank()) {
            builder.persistTo(properties.getDataDir());
        }

        // Configure Console
        if (properties.getConsole() != null) {
            var cp = properties.getConsole();
            var consoleConfig = ConsoleConfig.builder()
                    .enabled(cp.isEnabled())
                    .port(cp.getPort())
                    .contextPath(cp.getContextPath())
                    .intelligentPort(cp.isIntelligentPort())
                    .maxPortAttempts(cp.getMaxPortAttempts())
                    .host(cp.getHost())
                    .scheme(cp.getScheme())
                    .minPort(cp.getMinPort())
                    .maxPort(cp.getMaxPort())
                    .failIfPreferredPortUnavailable(cp.isFailIfPreferredPortUnavailable())
                    .startupTimeoutMs(cp.getStartupTimeoutMs())
                    .localhostOnly(cp.isLocalhostOnly())
                    .build();
            builder.console(consoleConfig);
        }

        // Configure Security
        if (properties.getSecurity() != null) {
            var sp = properties.getSecurity();
            var secBuilder = SecurityConfig.builder()
                    .authEnabled(sp.isAuthEnabled())
                    .apiKey(sp.getApiKey())
                    .adminUsername(sp.getAdminUsername())
                    .adminPassword(sp.getAdminPassword())
                    .corsEnabled(sp.isCorsEnabled())
                    .allowedOrigins(sp.getAllowedOrigins())
                    .sessionTtlMs(sp.getSessionTtlMs())
                    .csrfEnabled(sp.isCsrfEnabled())
                    .rateLimitEnabled(sp.isRateLimitEnabled())
                    .rateLimitRequestsPerMinute(sp.getRateLimitRequestsPerMinute())
                    .bruteForceProtectionEnabled(sp.isBruteForceProtectionEnabled())
                    .maxFailedLoginAttempts(sp.getMaxFailedLoginAttempts())
                    .lockoutDurationMs(sp.getLockoutDurationMs())
                    .passwordHashingEnabled(sp.isPasswordHashingEnabled())
                    .auditLoggingEnabled(sp.isAuditLoggingEnabled())
                    .securityHeadersEnabled(sp.isSecurityHeadersEnabled())
                    .minTlsVersion(sp.getMinTlsVersion());
            if (sp.getSslPort() > 0 && sp.getSslKeystorePath() != null) {
                secBuilder.ssl(sp.getSslPort(), sp.getSslKeystorePath(), sp.getSslKeystorePassword());
            }
            builder.security(secBuilder.build());
        }

        EmbedJNoSQL db = EmbedJNoSQL.create(builder.buildConfig());
        if (db.consoleUrl() != null) {
            logger.info("==========================================================================");
            logger.info("EmbedJNoSQL Administration Console: {}", db.consoleUrl());
            logger.info("==========================================================================");
        }
        return db;
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbedJNoSQLTemplate embeddedjnosqlDBTemplate(EmbedJNoSQL embeddedjnosqlDB) {
        return new EmbedJNoSQLTemplate(embeddedjnosqlDB);
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbedJNoSQLServer embeddedjnosqlDBServer(EmbedJNoSQL embeddedjnosqlDB) {
        return embeddedjnosqlDB.consoleServer();
    }
}
