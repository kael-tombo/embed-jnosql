# EmbedJNoSQL Deep Assessment: Tri-Standard Annotation Interoperability

**Subsystem**: `org.embeddedjnosql.db.adapter.jnosql`  
**Components**: `AnnotationResolver`, `EntityMapper`, Annotation Definitions  
**Status**: Production Verified Across All 3 Standards  

---

## 1. The Tri-Standard Problem & EmbedJNoSQL Solution

Java developers face severe fragmentation when choosing database annotations:
- **Relational Developers**: Use **Jakarta Persistence (JPA)** annotations (`@jakarta.persistence.Entity`, `@Table`, `@Id`, `@Column`).
- **NoSQL Developers**: Use **Eclipse JNoSQL** annotations (`@jakarta.nosql.Entity`, `@Id`, `@Column`).
- **Enterprise Power Users**: Rely on **Hibernate Annotations** (`@UuidGenerator`, `@CreationTimestamp`, `@UpdateTimestamp`, `@Formula`).

EmbedJNoSQL resolves this fragmentation with **Tri-Standard Interoperability**: a unified `AnnotationResolver` that dynamically inspects domain classes for any of the three standards.

---

## 2. Annotation Resolution Hierarchy & Precedence

| Feature | Precedence Order | Supported Annotations |
|---|---|---|
| **Entity / Collection Name** | 1. JPA `@Table(name)`<br>2. JNoSQL `@Entity(value)`<br>3. JPA `@Entity(name)`<br>4. EmbedJNoSQL `@Entity(value)`<br>5. Class Simple Name (lowercase) | `jakarta.persistence.Table`<br>`jakarta.nosql.Entity`<br>`jakarta.persistence.Entity`<br>`org.embeddedjnosql.db.adapter.jnosql.Entity` |
| **Primary Key (`@Id`)** | Any present: JNoSQL `@Id` OR JPA `@Id` OR JPA `@EmbeddedId` OR EmbedJNoSQL `@Id` | `jakarta.nosql.Id`<br>`jakarta.persistence.Id`<br>`jakarta.persistence.EmbeddedId`<br>`org.embeddedjnosql.db.adapter.jnosql.Id` |
| **Field Mapping (`@Column`)** | Specified name or field name; respects `insertable`/`updatable` | `jakarta.nosql.Column`<br>`jakarta.persistence.Column`<br>`org.embeddedjnosql.db.adapter.jnosql.Column` |
| **Ignored Fields (`@Transient`)**| Any present: JPA `@Transient`, keyword `transient`, EmbedJNoSQL `@Transient` | `jakarta.persistence.Transient`<br>`org.embeddedjnosql.db.adapter.jnosql.Transient` |
| **Auto ID Generation** | Hibernate `@UuidGenerator` or JPA `@GeneratedValue` | `org.hibernate.annotations.UuidGenerator`<br>`jakarta.persistence.GeneratedValue` |
| **Audit Timestamps** | Automated `Instant` or `LocalDateTime` stamping on insert/update | `org.hibernate.annotations.CreationTimestamp`<br>`org.hibernate.annotations.UpdateTimestamp` |
| **Computed Fields** | Dynamic expression evaluation on load | `org.hibernate.annotations.Formula` |
| **Enumerations** | String or Ordinal serialization | `jakarta.persistence.Enumerated` |

---

## 3. Classpath Resilience (No Forced Dependencies)

All annotation lookups use defensive reflection (`Class.forName()`). If a project does not include Hibernate or Eclipse JNoSQL on its classpath, EmbedJNoSQL functions without `ClassNotFoundException` or runtime errors.

---

## 4. Identified Gaps & Opportunities for Improvement

1. **Embedded / Composite Keys**: Deeply map `@Embedded` and `@Embeddable` complex nested value objects into document sub-structures.
2. **Inheritance Strategies**: Support `@Inheritance(strategy = SINGLE_TABLE)` for class hierarchies with `@DiscriminatorColumn`.
3. **Lifecycle Annotation Hooks**: Wire `@PrePersist` and `@PreUpdate` methods directly into the `EntityMapper` mutation cycle.
