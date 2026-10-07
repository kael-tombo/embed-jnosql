# Demonstration Runbook & Troubleshooting Guide

This runbook provides step-by-step instructions to build, launch, test, and troubleshoot the EmbedJNoSQL demonstration suite.

---

## Prerequisites
- **JDK**: Java 17 or Java 21+
- **Maven**: 3.8.0 or higher
- **Network Access**: Access to Maven Central for initial framework dependency resolution.

---

## 1. Quick Start (Build & Test Everything)

Run the following commands from the project root:

```bash
# 1. Install EmbedJNoSQL Core and integration extensions
mvn clean install -DskipTests

# 2. Build and install common demo domain
cd demo/demo-common
mvn clean install

# 3. Test each demonstration module
cd ../spring-boot-demo && mvn clean test
cd ../quarkus-demo && mvn clean test
cd ../micronaut-demo && mvn clean test
cd ../vertx-demo && mvn clean test
cd ../end-to-end-validation && mvn clean test
```

---

## 2. Running Standalone Demo Servers

### Spring Boot Demo
```bash
cd demo/spring-boot-demo
mvn spring-boot:run
# Server runs on http://localhost:8080
curl http://localhost:8080/api/products
```

### Quarkus Demo
```bash
cd demo/quarkus-demo
mvn quarkus:dev
# Server runs on http://localhost:8082
curl http://localhost:8082/api/products
```

### Micronaut Demo
```bash
cd demo/micronaut-demo
mvn mn:run
# Server runs on http://localhost:8083
curl http://localhost:8083/api/products
```

### Vert.x Demo
Run the test suite or execute `EcommerceVerticle` programmatically:
```bash
cd demo/vertx-demo
mvn test
```

---

## 3. Troubleshooting & Diagnostics

### Issue: "The process cannot access the file because it is being used by another process" (Windows)
- **Cause**: An unclosed `FileOutputStream` or `FileChannel` in persistent engines (`FileEngine`, `LSMTreeEngine`, `BTreeEngine`).
- **Remedy**: Always ensure `wal.close()` or `db.close()` is explicitly invoked in teardown blocks (`@AfterEach`). EmbedJNoSQL ensures all file descriptors, background flusher executors, and WAL sync threads are cleanly terminated upon `close()`.

### Issue: "No serializable introspection present for type Product" in Micronaut
- **Cause**: Micronaut serde requires `@Serdeable` or `@SerdeImport`.
- **Remedy**: In `SerdeConfiguration.java`, use `@SerdeImport(Product.class)` and `@SerdeImport(Order.class)` for external/shared records.
