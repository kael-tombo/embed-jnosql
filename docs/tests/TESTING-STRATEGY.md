# JNOSQL-EMBED: Global Testing Strategy

A holistic view of the testing methodology, pyramid, and verification pipelines in JNOSQL-EMBED.

---

## 1. Testing Pyramid

```
                       ▲
                      / \
                     /   \
                    / E2E \       <-- FullFeatureTest & Real Framework Demos
                   /───────\
                  / Contract\     <-- StorageEngine SPI Contract Tests
                 /───────────\
                / Integration \   <-- FullIntegrationTest, Spring Boot Tests
               /───────────────\
              /    Unit Tests   \  <-- Document, KV, List, Set, Hash, CF Tests
             /───────────────────\
```

---

## 2. Testing Levels

1. **Unit Testing**:
   - Focuses on individual data structure operations (`DocumentCollectionTest`, `KeyValueBucketTest`, `ListBucketTest`, `SetBucketTest`, `HashBucketTest`, `ColumnFamilyTest`).
   - Uses `InMemoryEngine` for sub-millisecond execution.
2. **Integration Testing**:
   - Focuses on multi-model interactions, persistence reload across restarts (`FilePersistenceTest`), and Spring Boot auto-configuration (`EmbedJNoSQLAutoConfigurationTest`).
3. **Deep & Concurrency Testing**:
   - Focuses on multi-threaded stress, race condition detection (`ConcurrencyTest`), snapshot isolation verification (`DeepTransactionTest`), and infrastructure metrics (`DeepInfrastructureTest`).
4. **End-to-End & REST Server Testing**:
   - Focuses on full system testing including the embedded HTTP management server, JSON REST endpoints, authentication filters, and CORS headers (`FullFeatureTest`).
5. **Real-World Framework Demonstration**:
   - Standalone demo applications in `demo/` demonstrating real-world e-commerce domain workflows in Spring Boot, Quarkus, Micronaut, Vert.x, and end-to-end multi-engine validation.

---

## 3. Guiding Principles

- **Zero Mocking of Database Core**: We test real storage engines, real byte serialization, and real collections. Mocking the database engine produces deceptive tests that fail in production.
- **Fast Execution**: In-memory unit tests execute in under 10 seconds across the entire suite of 489+ tests.
- **Strict Cleanup**: Tests utilizing temporary disk directories must recursively delete all temporary files in `@AfterEach` / `@AfterAll` blocks.
