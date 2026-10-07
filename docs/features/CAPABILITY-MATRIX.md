# JNOSQL-EMBED: Capability Matrix

A technical capability matrix detailing the supported storage models, query options, and operational capabilities across all four storage backends.

---

## 1. Storage Engine Capability Matrix

| Capability | `IN_MEMORY` | `FILE` (WAL) | `B_TREE` | `LSM_TREE` |
|---|:---:|:---:|:---:|:---:|
| **Document Collections** | ✅ Supported | ✅ Supported | ✅ Supported | ✅ Supported |
| **Key-Value Buckets** | ✅ Supported | ✅ Supported | ✅ Supported | ✅ Supported |
| **Redis List / Set / Hash**| ✅ Supported | ✅ Supported | ✅ Supported | ✅ Supported |
| **Column Families** | ✅ Supported | ✅ Supported | ✅ Supported | ✅ Supported |
| **Disk Durability** | ❌ (RAM only) | ✅ Supported | ✅ Supported | ✅ Supported |
| **Crash Recovery (WAL)** | ❌ | ✅ Supported | ✅ Supported | ✅ Supported |
| **Secondary Indexing** | ✅ In-Memory | ✅ Persisted | ✅ Persisted | ✅ Persisted |
| **Multi-Key Transactions** | ✅ Supported | ✅ Supported | ✅ Supported | ✅ Supported |
| **Range Queries** | ✅ Supported | ✅ Supported | ✅ Optimized | ✅ Supported |
| **Background Compaction** | ❌ (Not needed)| ❌ | ❌ | ✅ Supported |

---

## 2. Query Predicate Capability Matrix

| Predicate Operator | Supported in Document Store | Supported with Index Acceleration | Example |
|---|:---:|:---:|---|
| **Equality (`eq`)** | ✅ | ✅ (Instant lookup) | `Query.eq("status", "ACTIVE")` |
| **Inequality (`ne`)** | ✅ | ❌ (Full scan filter) | `Query.ne("role", "GUEST")` |
| **Greater Than (`gt`, `gte`)** | ✅ | ✅ (Range index) | `Query.gt("price", 99.99)` |
| **Less Than (`lt`, `lte`)** | ✅ | ✅ (Range index) | `Query.lt("age", 18)` |
| **Substring (`contains`)** | ✅ | ❌ (Full scan filter) | `Query.contains("title", "NoSQL")` |
| **Membership (`in`)** | ✅ | ❌ (Full scan filter) | `Query.in("tag", List.of("A", "B"))` |
| **Sort / Pagination** | ✅ | ✅ | `q.sort("age", ASC).limit(10)` |

---

## 3. Framework Compatibility Matrix

| Capability | Plain Java 17+ | Spring Boot 3.x | Quarkus 3.x | Micronaut 4.x | Vert.x 4.x |
|---|:---:|:---:|:---:|:---:|:---:|
| **Embedded Injection** | Direct API | `@Autowired EmbedJNoSQL` | `@Inject EmbedJNoSQL` | `@Inject EmbedJNoSQL` | Verticle Context |
| **Document Template** | Fluent API | `EmbedJNoSQLTemplate` | Direct Collection | `EmbedJNoSQLEntityManager`| Worker Thread |
| **Config via File** | `EmbedJNoSQLConfig`| `application.yml` | `application.properties` | `application.yml` | Vert.x JsonObject |
| **Lifecycle Auto-Close**| `try-with-res` | Spring Context Close | Quarkus Shutdown Event | Micronaut Stop | Verticle undeploy |
| **Native Image Ready** | N/A | Experimental | Supported | Experimental | Supported |
