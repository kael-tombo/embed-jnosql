# Contributing to EmbedJNoSQL (JNoSQL-EMBED)

Thank you for contributing to EmbedJNoSQL! This guide outlines our standards, environment setup, and contribution workflows.

---

## 1. Development Environment Setup

### Prerequisites
- **Java**: JDK 17 or JDK 21+
- **Maven**: 3.8.0 or higher
- **Git**: 2.30+

### Cloning & Compiling
```bash
git clone https://github.com/armand-ratombotiana/JNoSQL-EMBED.git
cd JNoSQL-EMBED

# Build core and run unit tests
mvn clean test

# Install core to local maven repository
mvn clean install -DskipTests
```

---

## 2. Project Architecture & Structure

- `src/main/java/org/embeddedjnosql/db/`: Core database engine
  - `nosql/`: Document, Key-Value, and Wide-Column models
  - `storage/`: Pluggable storage engines (`InMemoryEngine`, `FileEngine`, `BTreeEngine`, `LSMTreeEngine`)
  - `transaction/`: MVCC and ACID transaction managers
  - `console/`: Developer HTTP admin server
- `spring-boot-starter/`: Spring Boot 3 auto-configuration and `EmbedJNoSQLTemplate`
- `quarkus-extension/`: Quarkus runtime and deployment modules
- `micronaut-integration/`: Micronaut factory and CDI providers
- `demo/`: Real-world framework demonstration applications
- `docs/`: Comprehensive documentation suite

---

## 3. Contribution Rules & Quality Standards

1. **Zero External Daemon Dependencies**: All features and engines must run purely in-process inside the JVM.
2. **Deterministic Durability**: Any persistent engine change must preserve crash recovery invariants via the Write-Ahead Log (WAL).
3. **No Regressions**:
   - Every pull request must pass all 489 core tests plus all framework demo suites.
   - Run: `mvn clean test` in root and in `demo/`.
4. **Clean File Descriptors**: Always ensure file channels and streams are closed in teardown hooks to avoid Windows file locks.

---

## 4. Submitting Pull Requests

1. Fork the repository and create your branch from `main`:
   ```bash
   git checkout -b feature/my-new-feature
   ```
2. Commit with concise, descriptive commit messages adhering to Conventional Commits:
   - `feat: add compound secondary index support`
   - `fix: close file streams cleanly in WAL recovery`
   - `docs: update storage engine comparison matrix`
3. Push to your fork and submit a Pull Request.
