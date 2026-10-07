package org.embeddedjnosql.db.micronaut;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;

import jakarta.inject.Singleton;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Micronaut {@link Factory} that creates and manages the {@link EmbedJNoSQL} singleton.
 */
@Factory
public class EmbedDBFactory {

    @Nullable
    private EmbedJNoSQL embeddedjnosqlDB;

    @Property(name = "embedjnosql.enabled", defaultValue = "true")
    private boolean enabled = true;

    @Property(name = "embedjnosql.engine", defaultValue = "IN_MEMORY")
    private String engine = "IN_MEMORY";

    @Property(name = "embedjnosql.data-dir", defaultValue = "./data/embedjnosql")
    private String dataDir = "./data/embedjnosql";

    @Property(name = "embedjnosql.auto-flush", defaultValue = "true")
    private boolean autoFlush = true;

    @Property(name = "embedjnosql.flush-interval-ms", defaultValue = "1000")
    private int flushIntervalMs = 1000;

    @Property(name = "embedjnosql.console.enabled", defaultValue = "false")
    private boolean consoleEnabled = false;

    @Property(name = "embedjnosql.console.port", defaultValue = "8080")
    private int consolePort = 8080;

    @Property(name = "embedjnosql.console.path", defaultValue = "/")
    private String consolePath = "/";

    @Property(name = "embedjnosql.console.intelligent-port", defaultValue = "true")
    private boolean intelligentPort = true;

    @Property(name = "embedjnosql.security.enabled", defaultValue = "false")
    private boolean securityEnabled = false;

    @Nullable
    @Property(name = "embedjnosql.security.api-key")
    private String apiKey;

    @Property(name = "embedjnosql.security.admin-username", defaultValue = "admin")
    private String adminUsername = "admin";

    @Nullable
    @Property(name = "embedjnosql.security.admin-password")
    private String adminPassword;

    @Property(name = "embedjnosql.security.cors-enabled", defaultValue = "true")
    private boolean corsEnabled = true;

    @PostConstruct
    void initialize() {
        if (enabled) {
            var builder = EmbedJNoSQLConfig.builder()
                    .storageEngine(parseEngine(engine))
                    .persistTo(dataDir)
                    .autoFlush(autoFlush)
                    .flushIntervalMs(flushIntervalMs);

            if (consoleEnabled) {
                builder.console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(consolePort)
                        .contextPath(consolePath)
                        .intelligentPort(intelligentPort)
                        .build());
            }

            if (securityEnabled || apiKey != null || adminPassword != null) {
                var secBuilder = SecurityConfig.builder()
                        .authEnabled(securityEnabled)
                        .adminUsername(adminUsername)
                        .corsEnabled(corsEnabled);
                if (apiKey != null) secBuilder.apiKey(apiKey);
                if (adminPassword != null) secBuilder.adminPassword(adminPassword);
                builder.security(secBuilder.build());
            }

            embeddedjnosqlDB = EmbedJNoSQL.create(builder.buildConfig());
        }
    }

    @Singleton
    @NonNull
    public EmbedJNoSQL embeddedjnosqlDB() {
        if (embeddedjnosqlDB == null) {
            throw new IllegalStateException("EmbedJNoSQL is not initialized. Check that embedjnosql.enabled=true.");
        }
        return embeddedjnosqlDB;
    }

    @Singleton
    @Nullable
    public EmbedJNoSQLServer embeddedjnosqlDBServer() {
        return embeddedjnosqlDB != null ? embeddedjnosqlDB.consoleServer() : null;
    }

    @PreDestroy
    void stop() {
        if (embeddedjnosqlDB != null && embeddedjnosqlDB.isOpen()) {
            embeddedjnosqlDB.close();
        }
    }

    private EmbedJNoSQLConfig.StorageEngineType parseEngine(String engine) {
        try {
            return EmbedJNoSQLConfig.StorageEngineType.valueOf(engine.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY;
        }
    }
}