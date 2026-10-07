package org.embeddedjnosql.db.spring.boot;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toggle matrix for the H2-parity features via {@code ApplicationContextRunner}: every new
 * capability must default ON, switch off cleanly via properties, and yield to user-defined
 * beans.
 */
class EmbedJNoSQLParityTogglesTest {

    @Entity
    @Table(name = "toggle_items")
    static class ToggleItem {
        @Id
        String id;

        ToggleItem() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    @ImportAutoConfiguration(EmbedJNoSQLAutoConfiguration.class)
    static class ToggleConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ToggleConfig.class)
            .withPropertyValues("embedjnosql.storage-engine=IN_MEMORY");

    @Test
    void defaultsOn() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(EmbedJNoSQL.class);
            assertThat(ctx).hasBean("toggleItemRepository");
            assertThat(ctx).hasSingleBean(PlatformTransactionManager.class);
            assertThat(ctx.getBean(EmbedJNoSQL.class).getCollectionNames()).contains("toggle_items");
        });
    }

    @Test
    void repositoriesOff() {
        runner.withPropertyValues("embedjnosql.repositories.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(EmbedJNoSQL.class);
                    assertThat(ctx).doesNotHaveBean("toggleItemRepository");
                    // transactions are independent of repositories
                    assertThat(ctx).hasSingleBean(PlatformTransactionManager.class);
                });
    }

    @Test
    void transactionsOff() {
        runner.withPropertyValues("embedjnosql.transactions.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(PlatformTransactionManager.class);
                    // repositories are independent of transactions
                    assertThat(ctx).hasBean("toggleItemRepository");
                });
    }

    @Test
    void autoCreateCollectionsOff() {
        runner.withPropertyValues("embedjnosql.auto-create-collections=false")
                .run(ctx -> {
                    EmbedJNoSQL db = ctx.getBean(EmbedJNoSQL.class);
                    assertThat(db.getCollectionNames()).doesNotContain("toggle_items");
                    assertThat(ctx).hasBean("toggleItemRepository");
                });
    }

    @Test
    void userTransactionManagerWins() {
        runner.withBean("myTxManager", PlatformTransactionManager.class,
                        () -> new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                            @Override
                            protected Object doGetTransaction() {
                                return new Object();
                            }

                            @Override
                            protected void doBegin(Object o, org.springframework.transaction.TransactionDefinition d) {
                            }

                            @Override
                            protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) {
                            }

                            @Override
                            protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) {
                            }
                        })
                .run(ctx -> {
                    PlatformTransactionManager tm = ctx.getBean(PlatformTransactionManager.class);
                    assertThat(tm).isNotInstanceOf(EmbedJNoSQLTransactionManager.class);
                });
    }

    @Test
    void userNamedRepositoryBeanWinsOverAutoRegistration() {
        runner.withUserConfiguration(UserRepoConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasBean("toggleItemRepository");
                    // exactly one — the user's
                    EmbedRepository<?, ?> repo = (EmbedRepository<?, ?>) ctx.getBean("toggleItemRepository");
                    assertThat(repo.getEntityClass()).isEqualTo(ToggleItem.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserRepoConfig {
        @Bean("toggleItemRepository")
        EmbedRepository<ToggleItem, String> userRepo(EmbedJNoSQL db) {
            return EmbedRepository.of(ToggleItem.class, db);
        }
    }

    /** Probe: the core promise is by-type injection of the fully-parameterized repository. */
    @Configuration(proxyBeanMethods = false)
    static class ByTypeInjectionProbe {
        final EmbedRepository<ToggleItem, String> repo;

        ByTypeInjectionProbe(EmbedRepository<ToggleItem, String> repo) {
            this.repo = repo;
        }
    }

    @Test
    void repositoryInjectsByParameterizedType() {
        runner.withUserConfiguration(ByTypeInjectionProbe.class).run(ctx -> {
            assertThat(ctx).hasBean("toggleItemRepository");
            assertThat(ctx.getBean(ByTypeInjectionProbe.class).repo).isNotNull();
        });
    }
}
