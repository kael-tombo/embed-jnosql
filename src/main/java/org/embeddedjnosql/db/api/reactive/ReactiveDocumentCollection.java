package org.embeddedjnosql.db.api.reactive;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReactiveDocumentCollection {

    private final DocumentCollection delegate;
    private final ExecutorService executor;

    public ReactiveDocumentCollection(DocumentCollection delegate) {
        this.delegate = delegate;
        this.executor = Executors.newCachedThreadPool();
    }

    public ReactiveDocumentCollection(DocumentCollection delegate, ExecutorService executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    public CompletableFuture<Document> insert(Document doc) {
        return CompletableFuture.supplyAsync(() -> delegate.insert(doc), executor);
    }

    public CompletableFuture<List<Document>> insertAll(List<Document> docs) {
        return CompletableFuture.supplyAsync(() -> delegate.insertAll(docs), executor);
    }

    public CompletableFuture<Document> findById(String id) {
        return CompletableFuture.supplyAsync(() -> delegate.findById(id), executor);
    }

    public CompletableFuture<List<Document>> findAll() {
        return CompletableFuture.supplyAsync(() -> delegate.findAll(), executor);
    }

    public CompletableFuture<List<Document>> find(Query query) {
        return CompletableFuture.supplyAsync(() -> delegate.find(query), executor);
    }

    public CompletableFuture<Optional<Document>> findOne(Query query) {
        return CompletableFuture.supplyAsync(() -> Optional.ofNullable(delegate.findOne(query)), executor);
    }

    public CompletableFuture<Document> update(Document doc) {
        return CompletableFuture.supplyAsync(() -> delegate.update(doc), executor);
    }

    public CompletableFuture<Boolean> deleteById(String id) {
        return CompletableFuture.supplyAsync(() -> delegate.deleteById(id), executor);
    }

    public CompletableFuture<Long> deleteAll(Query query) {
        return CompletableFuture.supplyAsync(() -> delegate.deleteAll(query), executor);
    }

    public CompletableFuture<Long> count() {
        return CompletableFuture.supplyAsync(() -> delegate.count(), executor);
    }

    public CompletableFuture<Boolean> exists(String id) {
        return CompletableFuture.supplyAsync(() -> delegate.exists(id), executor);
    }

    public void close() {
        executor.shutdown();
    }
}
