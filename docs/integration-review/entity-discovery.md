# Entity Discovery and Mapping

**Module**: `spring-boot-starter`  
**Scanner**: `EmbedEntityScanner`  
**Mapper**: `org.embeddedjnosql.db.adapter.jnosql.EntityMapper`  
**Column resolver**: `org.embeddedjnosql.db.spring.boot.data.MappingColumns`

---

## Annotations Recognized

Detection is string-based (no annotation class needs to be on the bootstrap classpath):

| Annotation | Source | Purpose |
|---|---|---|
| `jakarta.persistence.Entity` | Jakarta Persistence 3.x | Marks a class as a scannable entity |
| `jakarta.nosql.Entity` | Jakarta NoSQL | Alternative entity marker |
| `org.embeddedjnosql.db.adapter.jnosql.Entity` | Embed-JNoSQL native | Native entity marker |
| `jakarta.persistence.Id` | Jakarta Persistence | Marks the document ID field |
| `jakarta.persistence.Table(name=...)` | Jakarta Persistence | Sets collection name |
| `jakarta.persistence.Column(name=...)` | Jakarta Persistence | Sets stored field name |
| `jakarta.persistence.Transient` | Jakarta Persistence | Excludes field from storage |
| `jakarta.persistence.Enumerated(STRING)` | Jakarta Persistence | Enum stored as name string |

---

## Collection Naming

Priority:
1. `@Table(name = "explicit_name")` → use that name
2. Class simple name lowercase with underscores (convention)

Example: `@Table(name = "ir_products")` on class `Product` → collection `"ir_products"`.

---

## ID Field Selection

Priority:
1. Field annotated with `@Id` (jakarta.persistence or JNoSQL)
2. Field named `id`

ID values are stored as strings in the document `id` field. Non-string IDs are stringified via `String.valueOf()`.

---

## Field Mapping

| Java Type | Stored As |
|---|---|
| `String` | String |
| `int`, `Integer`, `long`, `Long` | Number |
| `double`, `Double`, `float`, `Float` | Number |
| `boolean`, `Boolean` | Boolean |
| `Enum` | String (enum name) |
| `Instant` | ISO-8601 String |
| `LocalDate`, `LocalDateTime` | String |
| `Date` | ISO-8601 String |
| `Map<String, Object>` | Nested map |
| `List<Object>` | List |
| `@Transient` fields | Not stored |

---

## Scan Scope

Default: Spring Boot's `AutoConfigurationPackages` (the `@SpringBootApplication` package and its sub-packages).

Custom: `@EnableEmbedJpaRepositories(basePackages = {"com.example.model"})` expands the scan.

---

## Startup Collection Materialization

When `embedjnosql.auto-create-collections=true`:
1. `EmbedEntityScanner.scan(packages, classLoader)` discovers all `@Entity` classes
2. `EmbedEntityCollectionsInitializer.afterPropertiesSet()` calls `db.documentCollection(name)` for each
3. Collections are created (no-op if already exist)

This mirrors JPA's `spring.jpa.hibernate.ddl-auto=update` behavior for a document store.

---

## Unsupported Mappings

| JPA Feature | Status | Notes |
|---|---|---|
| `@OneToMany`, `@ManyToOne`, etc. | **IGNORED** | No relationship concept in document store |
| `@GeneratedValue` | **IGNORED** | ID must be caller-supplied or auto-UUID via engine |
| `@Version` | **IGNORED** | MVCC provides isolation; no row-version tracking |
| `@Embedded`, `@Embeddable` | **IGNORED** | Nested objects stored as maps (flat) |
| `@Inheritance` | **IGNORED** | No polymorphic collection strategy |
| Composite `@Id` | **UNSUPPORTED** | Only single-field ID supported |
| `@Converter` / `AttributeConverter` | **UNSUPPORTED** | No JPA converter chain |

---

## Findings

| # | Finding | Severity | Fix |
|---|---|---|---|
| ED-01 | `@Embedded` / `@Embeddable` silently ignored — nested object stored as flat Java object reference | LOW | Document as limitation |
| ED-02 | `@GeneratedValue(IDENTITY/SEQUENCE/TABLE)` silently ignored — caller must supply ID | MEDIUM | Document supported patterns |
| ED-03 | No duplicate-entity detection across packages — same class in two packages produces two repos | LOW | Document as limitation |
