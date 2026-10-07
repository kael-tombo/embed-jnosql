# JNOSQL-EMBED: Test Architecture & Fixtures

Structural guidelines for authoring tests in JNOSQL-EMBED.

---

## 1. Test Harness Base Classes & Fixtures

```
src/test/java/org/embeddedjnosql/db/
├── (Standard Unit Tests)          - Fast in-memory JUnit 5 tests
├── deep/                          - Stress, concurrency, and volume test suites
└── integration/                   - Multi-model and persistence integration suites
```

### Standard In-Memory Test Pattern:
```java
class MyFeatureTest {
    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
}
```

### Standard Disk Persistence Test Pattern:
```java
class MyPersistenceTest {
    private Path tempDir;
    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("embeddedjnosql-test-");
        db = EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.FILE)
                .persistTo(tempDir.toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (db != null && db.isOpen()) {
            db.close();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }
}
```

---

## 2. Assertion Standards
- Prefer specific, informative assertions: `assertEquals(expected, actual, "Descriptive message")`.
- Verify both state and behavior: check returned entity values, collection size, and secondary index presence.
- Avoid assertions that cannot fail: always ensure that the tested condition tests actual mutation or return values.
