# Sub-Assessment: Framework Integrations & Standards Compliance

## 1. Purpose and User Outcome
Provide frictionless integration into major JVM frameworks (Spring Boot, Quarkus, Micronaut) and enable standard enterprise annotation mapping (Jakarta NoSQL, Jakarta Persistence, Hibernate) without imposing transitive runtime burdens on plain Java applications.

## 2. Relevant Code & Configuration
- Spring Boot: `spring-boot-starter/`
- Quarkus: `quarkus-extension/` (runtime & deployment modules)
- Micronaut: `micronaut-integration/`
- Annotations: `org.embeddedjnosql.db.adapter.EntityMapper`, `EmbedRepository`

## 3. Findings and Verification

### A. Spring Boot Starter (`spring-boot-starter`)
- **Auto-Configuration**: Automatically registers `EmbedJNoSQL` bean based on `application.properties` (`embedjnosql.data-dir`, `embedjnosql.storage-engine`).
- **Health Indicator**: Exposes `EmbedJNoSQLHealthIndicator` for Spring Boot Actuator (`/actuator/health`).
- **Transaction Management**: Integrates with Spring `@Transactional` via PlatformTransactionManager adapter.

### B. Quarkus Extension (`quarkus-extension`)
- **Build-Time Optimization**: Uses Quarkus build steps to configure native image reflection targets.
- **CDI Injection**: Produces `@ApplicationScoped` `EmbedJNoSQL` instances.

### C. Micronaut Integration (`micronaut-integration`)
- **Factory**: Supplies `@Factory` creating singleton `EmbedJNoSQL` instance from Micronaut configuration.

### D. Jakarta NoSQL & Enterprise Annotations
- **Jakarta NoSQL**: Supports `jakarta.nosql.@Entity`, `@Id`, and `@Column`.
- **Jakarta Persistence (JPA)**: Supports `jakarta.persistence.@Entity`, `@Id`, `@Column`, `@Transient`.
- **Hibernate**: Supports `org.hibernate.annotations.@CreationTimestamp`, `@UpdateTimestamp`, `@UuidGenerator`.
- **Zero Heavyweight Runtime Requirement**: Annotations are inspected dynamically via reflection. If JPA or JNoSQL libraries are absent from the consumer classpath, EmbedJNoSQL operates without error.
- **Verification**: Verified via `JpaAnnotationTest`, `HibernateAnnotationTest`, and `AnnotationDualSupportTest`.
