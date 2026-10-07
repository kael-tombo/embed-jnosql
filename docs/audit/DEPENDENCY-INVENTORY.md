# EmbedJNoSQL — Dependency Inventory

**Audit Date**: September 9, 2026  
**Auditor**: Security & Dependency Auditor  

---

## 1. Core Engine Dependencies (`pom.xml`)

| Group ID | Artifact ID | Version | Scope | Justification & Transitive Footprint |
|---|---|---|---|---|
| `com.fasterxml.jackson.core` | `jackson-databind` | 2.17.0 | Compile | High-performance JSON serialization for document collections and REST DTOs. |
| `com.fasterxml.jackson.datatype` | `jackson-datatype-jsr310` | 2.17.0 | Compile | Java 8/17 Date & Time (`Instant`, `LocalDate`) serialization support. |
| `jakarta.enterprise` | `jakarta.enterprise.cdi-api` | 4.0.1 | Provided / Optional | CDI annotation support; marked `provided` so standalone apps have zero runtime CDI overhead. |
| `org.slf4j` | `slf4j-api` | 2.0.12 | Compile | Industry standard logging facade. |
| `org.slf4j` | `slf4j-simple` | 2.0.12 | Runtime / Optional | Default logger for CLI / standalone usage; marked optional to avoid framework binding conflicts. |
| `org.junit.jupiter` | `junit-jupiter` | 5.10.2 | Test | Unit and integration test framework. |

## 2. Dependency Audit & Vulnerability Posture
- **Zero JNI Dependencies**: No native binaries or OS-specific C wrappers (e.g., zero RocksDB JNI).
- **Vulnerabilities (CVEs)**: Jackson 2.17.0 and SLF4J 2.0.12 are current releases with 0 known high/critical CVEs.
- **Transitive Bloat**: Core JAR footprint is under 3MB, maintaining lightweight embedded characteristics.
