package org.embeddedjnosql.db.api.reactive;

import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class ReactiveKeyValueBucket {

    private final KeyValueBucket delegate;
    private final ExecutorService executor;

    public ReactiveKeyValueBucket(KeyValueBucket delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    public CompletableFuture<Void> put(String key, String value) {
        return CompletableFuture.runAsync(() -> delegate.put(key, value), executor);
    }

    public CompletableFuture<String> get(String key) {
        return CompletableFuture.supplyAsync(() -> delegate.get(key), executor);
    }

    public CompletableFuture<Boolean> delete(String key) {
        return CompletableFuture.supplyAsync(() -> delegate.delete(key), executor);
    }

    public CompletableFuture<Boolean> exists(String key) {
        return CompletableFuture.supplyAsync(() -> delegate.exists(key), executor);
    }

    public CompletableFuture<Long> increment(String key) {
        return CompletableFuture.supplyAsync(() -> delegate.increment(key), executor);
    }

    public CompletableFuture<Long> increment(String key, long delta) {
        return CompletableFuture.supplyAsync(() -> delegate.increment(key, delta), executor);
    }

    public CompletableFuture<Long> decrement(String key) {
        return CompletableFuture.supplyAsync(() -> delegate.decrement(key), executor);
    }

    public KeyValueBucket delegate() {
        return delegate;
    }
}
