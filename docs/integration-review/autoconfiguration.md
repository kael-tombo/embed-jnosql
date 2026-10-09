# Spring Boot Auto-Configuration Contract

**Module**: `embed-jnosql-spring-boot-starter`  
**Boot Version**: 3.2.5  
**Registration**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## Auto-Configuration Activation

```java
@ConditionalOnProperty(prefix = "embedjnosql", name = "enabled",
    havingValue = "true", matchIfMissing = true)
```

Activates by default. Disable with `embedjnosql.enabled=false`.

---

## Beans Registered

### `EmbedJNoSQL` (primary)
```java
@Bean(destroyMethod = "close")
@ConditionalOnMissingBean
```
- Configured from `EmbedJNoSQLProperties`
- Wrapped in `EmbedJNoSQLTxRoutingPostProcessor` when `transactions-enabled=true`
- The tx-routing proxy ensures `@Transactional` collection access routes through MVCC

### `EmbedJNoSQLTemplate`
```java
@Bean
@ConditionalOnMissingBean
```
Facade providing `documents(name)`, `keyValues(name)`, `columns(name)` accessors.

### `EmbedJNoSQLTransactionManager`
```java
@ConditionalOnProperty("embedjnosql.transactions.enabled", matchIfMissing = true)
@ConditionalOnMissingBean(PlatformTransactionManager.class)
```
Auto-backed off when application defines its own `PlatformTransactionManager` (e.g., for JPA coexistence).

### `EmbedEntityCollectionsInitializer`
Materializes a document collection per scanned `@Entity` at startup.  
Enabled when `embedjnosql.repositories-enabled=true` and `embedjnosql.auto-create-collections=true`.

---

## Configuration Properties

**Prefix**: `embedjnosql`

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Master switch |
| `storage-engine` | `StorageEngineType` | `IN_MEMORY` | `IN_MEMORY`, `FILE`, `LSM_TREE`, `B_TREE` |
| `data-dir` | String | `"data"` | Persistence directory (ignored for `IN_MEMORY`) |
| `auto-flush` | boolean | `true` | Background WAL flush |
| `flush-interval-ms` | int | `1000` | WAL flush interval (ms) |
| `transactions-enabled` | boolean | `true` | MVCC `@Transactional` support |
| `repositories-enabled` | boolean | `true` | Auto-register `EmbedRepository` per `@Entity` |
| `auto-create-collections` | boolean | `true` | Materialize collections at startup |
| `console.enabled` | boolean | `false` | Admin console |
| `console.port` | int | `9090` | Preferred HTTP port |
| `console.intelligent-port` | boolean | `true` | Auto-scan for free port |
| `security.auth-enabled` | boolean | `false` | Console auth |
| `security.admin-username` | String | `"admin"` | Console admin username |

---

## User Override Pattern

To override the `EmbedJNoSQL` bean (e.g., for a custom storage path or configuration):

```java
@Configuration
public class MyConfig {
    @Bean
    public EmbedJNoSQL embedJNoSQL() {
        return EmbedJNoSQL.embed()
            .storageEngine(StorageEngineType.B_TREE)
            .dataDir(Paths.get("/var/lib/myapp/db"))
            .build();
    }
}
```

The `@ConditionalOnMissingBean` on the auto-configured bean causes back-off.

---

## Transaction Manager Back-Off (JPA Coexistence)

When `spring-boot-starter-data-jpa` is also on the classpath, Spring Boot registers a `JpaTransactionManager`. The `EmbedJNoSQLTransactionManager` uses `@ConditionalOnMissingBean(PlatformTransactionManager.class)` and backs off automatically.

In a mixed application, annotate NoSQL service methods explicitly:

```java
@Transactional(transactionManager = "embedJNoSQLTransactionManager")
public void saveOrder(Order order) { ... }
```

---

## Findings

| # | Finding | Severity | Status |
|---|---|---|---|
| AC-01 | `dataDir` defaults to `"data"` even for IN_MEMORY — causes spurious directory creation on some systems | LOW | Fixed (null-check in `EmbedJNoSQL.embed()`) |
| AC-02 | No `@ConfigurationPropertiesBinding` for `StorageEngineType` — mistyped values produce cryptic error | LOW | Documented in startup failure guide |
| AC-03 | Console disabled by default — developers must opt in via `embedjnosql.console.enabled=true` | INFO | Expected behavior |
