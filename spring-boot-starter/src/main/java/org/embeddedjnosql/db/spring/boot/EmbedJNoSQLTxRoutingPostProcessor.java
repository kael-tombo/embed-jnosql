package org.embeddedjnosql.db.spring.boot;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Wraps the auto-configured {@link EmbedJNoSQL} bean in a transaction-routing proxy — the
 * embedded-database equivalent of Spring's {@code TransactionAwareDataSourceProxy}. When a
 * Spring transaction opened by {@link EmbedJNoSQLTransactionManager} is bound to the thread
 * for this database, {@code documentCollection(name)} returns a
 * {@link TransactionAwareDocumentCollection} whose mutations stage in the transaction;
 * without an active transaction the call passes straight through.
 *
 * <p>Because the routing sits at the database seam, everything built on the bean —
 * {@link EmbedJNoSQLTemplate}, auto-registered {@code EmbedRepository}s, direct
 * {@code @Autowired EmbedJNoSQL} usage — inherits transactional semantics uniformly.</p>
 *
 * <p>Registered as a {@code static} bean so it participates in bean creation from the
 * earliest lifecycle phase (standard practice for BeanPostProcessors in auto-configuration).</p>
 */
public class EmbedJNoSQLTxRoutingPostProcessor implements BeanPostProcessor {

    private final boolean enabled;

    public EmbedJNoSQLTxRoutingPostProcessor(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
        if (!enabled || !(bean instanceof EmbedJNoSQL db)) {
            return bean;
        }
        return routed(db); // routed() is a no-op for already-proxied beans
    }

    /**
     * Wraps a raw {@link EmbedJNoSQL} in the transaction-routing proxy. Called directly from
     * the auto-configuration {@code embedJNoSQL} {@code @Bean} factory method so the singleton
     * published to the container is the routed proxy from birth — even when the bean is
     * finalized during {@code registerBeanPostProcessors}' type-checking cascade, before BPPs
     * are registered. The {@link BeanPostProcessor} stays as a safety net for user-defined
     * (non-auto-configured) database beans.
     */
    public static EmbedJNoSQL routed(EmbedJNoSQL db) {
        if (db == null || org.springframework.aop.support.AopUtils.isAopProxy(db)) {
            return db; // already routed; never wrap twice
        }
        ProxyFactory proxyFactory = new ProxyFactory(db);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new RoutingInterceptor(db));
        return (EmbedJNoSQL) proxyFactory.getProxy();
    }

    /**
     * The routing seam itself, runnable from any holder of the raw database: produces the
     * {@link TransactionAwareDocumentCollection} that stages into the currently active
     * Spring-bound transaction. Shared with the Spring Data repository layer so writes
     * through bare (instrumentation-bypassed) database references honor {@code @Transactional}
     * identically to writes through the proxied bean.
     */
    public static TransactionAwareDocumentCollection routedCollection(EmbedJNoSQL rawDb, String name,
            org.embeddedjnosql.db.transaction.mvcc.Transaction tx) {
        return new TransactionAwareDocumentCollection(name, rawDb.storageEngine(),
                rawDb.eventBus(), rawDb.metrics(), tx);
    }

    private static class RoutingInterceptor implements MethodInterceptor {

        private final EmbedJNoSQL target;

        RoutingInterceptor(EmbedJNoSQL target) {
            this.target = target;
        }

        @Override
        public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
            Method method = invocation.getMethod();

            // Object methods must hit the target: proxies compare and hash by identity.
            if (ReflectionUtils.isEqualsMethod(method) || ReflectionUtils.isHashCodeMethod(method)
                    || ReflectionUtils.isToStringMethod(method)) {
                return invokeTarget(invocation);
            }

            if ("documentCollection".equals(method.getName())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class) {
                // Key the lookup by the RAW TARGET instance (this.target): Spring's
                // MethodInvocation.getThis() is the proxy's target, and the transaction
                // manager binds resources under exactly that unwrapped instance.
                Transaction tx = SpringTransactionHolder.active(target);
                if (tx != null && tx.isOpen()) {
                    String name = (String) invocation.getArguments()[0];
                    return routedCollection(tx, name);
                }
            }

            return invokeTarget(invocation);
        }

        private Object invokeTarget(MethodInvocation invocation) throws Throwable {
            try {
                return invocation.getMethod().invoke(target, invocation.getArguments());
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        /**
         * Per-call instances are deliberately cheap: all durable state lives in the
         * transaction's staged buffer, so there is nothing to cache (and nothing to leak).
         */
        private TransactionAwareDocumentCollection routedCollection(Transaction tx, String name) {
            return EmbedJNoSQLTxRoutingPostProcessor.routedCollection(target, name, tx);
        }

        private org.embeddedjnosql.db.storage.spi.StorageEngine engine() {
            return target.storageEngine();
        }

        private org.embeddedjnosql.db.core.event.EventBus eventBus() {
            return target.eventBus();
        }

        private org.embeddedjnosql.db.core.metrics.DatabaseMetrics metrics() {
            return target.metrics();
        }
    }
}
