package org.embeddedjnosql.db.api.reactive;

import org.embeddedjnosql.db.Experimental;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * EXPERIMENTAL: CompletableFuture-style facade over a {@link EmbedJNoSQL} instance.
 *
 * <p>Labeled by product decision (release review round 3): this class has no production caller,
 * no functional test coverage, and no documentation beyond this notice. It ships in the jar, but
 * the API may change or be removed in a future release without notice. Prefer the blocking
 * {@link EmbedJNoSQL} API for anything critical.</p>
 */
@Experimental("no production caller; no functional coverage; API may change or be removed without notice")
public class ReactiveJNoSQL {

    private final EmbedJNoSQL delegate;
    private final ExecutorService executor;

    public ReactiveJNoSQL(EmbedJNoSQL delegate) {
        this.delegate = delegate;
        this.executor = Executors.newCachedThreadPool();
    }

    public ReactiveJNoSQL(EmbedJNoSQL delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    public ReactiveDocumentCollection documentCollection(String name) {
        return new ReactiveDocumentCollection(delegate.documentCollection(name), executor);
    }

    public ReactiveKeyValueBucket keyValueBucket(String name) {
        return new ReactiveKeyValueBucket(delegate.keyValueBucket(name), executor);
    }

    public CompletableFuture<Transaction> beginTransaction() {
        return CompletableFuture.supplyAsync(delegate::beginTransaction, executor);
    }

    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            delegate.close();
            executor.shutdown();
        }, executor);
    }

    public EmbedJNoSQL delegate() {
        return delegate;
    }

    public ExecutorService executor() {
        return executor;
    }

    public static ReactiveJNoSQL wrap(EmbedJNoSQL EmbedJNoSQL) {
        return new ReactiveJNoSQL(EmbedJNoSQL);
    }
}
