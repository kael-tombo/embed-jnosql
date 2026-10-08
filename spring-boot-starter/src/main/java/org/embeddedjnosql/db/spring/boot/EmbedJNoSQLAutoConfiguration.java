package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Boot auto-configuration for embed-jnosql — the H2-style embedded NoSQL experience:
 * add the starter (plus the optional {@code hibernate-core} annotation jar for
 * {@code jakarta.persistence}/{@code org.hibernate.annotations} mapping) and you get
 * <ul>
 *   <li>an auto-configured, open {@link EmbedJNoSQL} bean (in-memory by default, durable via
 *       {@code embedjnosql.data-dir}),</li>
 *   <li>an {@link EmbedJNoSQLTemplate} facade,</li>
 *   <li>the admin console ({@code embedjnosql.console.*}, like {@code spring.h2.console.*}),</li>
 *   <li>{@code @Transactional} support through {@link EmbedJNoSQLTransactionManager} with
 *       transaction-routed collections (like {@code DataSourceTransactionManager} + a
 *       transaction-aware datasource proxy),</li>
 *   <li>auto-registered {@code EmbedRepository} beans for every scanned {@code @Entity}
 *       (Spring-Data-style repositories without interfaces), and</li>
 *   <li>startup materialization of entity collections (ddl-auto=update parity,
 *       {@code embedjnosql.auto-create-collections}).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbedJNoSQLProperties.class)
@ConditionalOnProperty(prefix = "embedjnosql", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmbedJNoSQLAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(EmbedJNoSQLAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public EmbedJNoSQL embedJNoSQL(EmbedJNoSQLProperties properties) {
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
        // Publish the routed proxy from birth: a bean finalized during the
        // registerBeanPostProcessors type-checking cascade (repository FactoryBeans resolving
        // their database dependency early) would otherwise cache a raw instance that no BPP
        // can ever wrap. Mirror of TransactionAwareDataSourceProxy built at the DataSource seam.
        return properties.isTransactionsEnabled() ? EmbedJNoSQLTxRoutingPostProcessor.routed(db) : db;
    }

    /**
     * Routes {@code documentCollection()} through the active Spring transaction when one is
     * bound — static so it participates in bean creation from the earliest phase.
     */
    @Bean
    public static EmbedJNoSQLTxRoutingPostProcessor embedJNoSQLTxRoutingPostProcessor(EmbedJNoSQLProperties properties) {
        return new EmbedJNoSQLTxRoutingPostProcessor(properties.isTransactionsEnabled());
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbedJNoSQLTemplate embedJNoSQLTemplate(EmbedJNoSQL embedJNoSQL) {
        return new EmbedJNoSQLTemplate(embedJNoSQL);
    }

    @Bean
    @ConditionalOnMissingBean
    public EmbedJNoSQLServer embedJNoSQLServer(EmbedJNoSQL embedJNoSQL) {
        return embedJNoSQL.consoleServer();
    }

    /**
     * {@code @Transactional} over the MVCC transaction layer. Auto-configured only when the
     * application has not chosen its own {@link PlatformTransactionManager}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "embedjnosql.transactions", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class EmbedJNoSQLTransactionsConfiguration {

        @Bean
        @ConditionalOnMissingBean(PlatformTransactionManager.class)
        public EmbedJNoSQLTransactionManager embedJNoSQLTransactionManager(EmbedJNoSQL embedJNoSQL) {
            return new EmbedJNoSQLTransactionManager(embedJNoSQL);
        }
    }

    /**
     * Auto-registered {@code EmbedRepository}s per {@code @Entity} in the auto-configuration
     * package (the {@code @SpringBootApplication} package, as registered by Spring Boot),
     * plus optional startup materialization of their collections.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "embedjnosql.repositories", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @Import(EmbedEntityRepositoryRegistrar.class)
    static class EmbedJNoSQLRepositoriesConfiguration {

        @Bean
        public EmbedEntityCollectionsInitializer embedEntityCollectionsInitializer(
                EmbedJNoSQL embedJNoSQL, EmbedJNoSQLProperties properties, BeanFactory beanFactory) {
            List<Class<?>> entities = List.of();
            if (properties.isAutoCreateCollections()) {
                try {
                    entities = EmbedEntityScanner.scan(AutoConfigurationPackages.get(beanFactory),
                            EmbedJNoSQLAutoConfiguration.class.getClassLoader());
                } catch (IllegalStateException ignored) {
                    // no auto-configuration package registered: nothing scanned, nothing to materialize
                }
            }
            return new EmbedEntityCollectionsInitializer(embedJNoSQL, entities);
        }
    }
}
