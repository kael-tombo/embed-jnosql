package org.embeddedjnosql.db.nosql.document;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class VersionedDocument {

    private final Document document;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    public VersionedDocument(Document document, long version, Instant createdAt, Instant updatedAt) {
        this.document = document;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static VersionedDocument of(Document doc) {
        var now = Instant.now();
        return new VersionedDocument(doc, 1, now, now);
    }

    public VersionedDocument withDocument(Document newDoc) {
        return new VersionedDocument(newDoc, version + 1, createdAt, Instant.now());
    }

    public Document document() {
        return document;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Document toDocument() {
        var doc = Document.of("version", version)
                .add("createdAt", createdAt.toEpochMilli())
                .add("updatedAt", updatedAt.toEpochMilli())
                .add("data", document.toJson());
        if (document.id() != null) {
            doc.id(document.id());
        }
        return doc;
    }

    public static VersionedDocument fromDocument(Document doc) {
        if (!doc.has("version")) {
            return VersionedDocument.of(doc);
        }
        
        var version = doc.get("version");
        var createdAt = doc.get("createdAt");
        var updatedAt = doc.get("updatedAt");
        var data = doc.get("data");
        
        var dataDoc = data instanceof String s ? Document.fromJson(s) : (Document) data;
        
        return new VersionedDocument(
            dataDoc,
            version instanceof Number n ? n.longValue() : 1,
            createdAt instanceof Number c ? Instant.ofEpochMilli(c.longValue()) : Instant.now(),
            updatedAt instanceof Number u ? Instant.ofEpochMilli(u.longValue()) : Instant.now()
        );
    }
}
