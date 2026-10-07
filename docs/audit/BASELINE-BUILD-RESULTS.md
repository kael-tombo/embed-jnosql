# EmbedJNoSQL — Baseline Build Results

**Audit Date**: September 9, 2026  
**Host Operating System**: Windows Server 2022 (Version 10.0, amd64)  
**Java Runtime**: Eclipse Adoptium OpenJDK 25.0.2 (`25.0.2.10-hotspot`)  
**Build Tool**: Apache Maven 3.9.15  

---

## 1. Clean Build Execution

### Command Executed
```bash
mvn clean compile
```

### Result
```text
[INFO] Scanning for projects...
[INFO] -------------------< org.embeddedjnosql.db:embed-jnosql-core >-------------------
[INFO] Building EmbedJNoSQL NoSQL 1.0.0
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] --- clean:3.2.0:clean (default-clean) @ embed-jnosql-core ---
[INFO] Deleting C:\Users\jratombo-adm\Desktop\JNoSQL-EMBED\target
[INFO] --- resources:3.3.1:resources (default-resources) @ embed-jnosql-core ---
[INFO] Copying 5 resources from src\main\resources to target\classes
[INFO] --- compiler:3.13.0:compile (default-compile) @ embed-jnosql-core ---
[INFO] Recompiling the module because of changed source code.
[INFO] Compiling 84 source files with javac [debug release 17] to target\classes
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  3.412 s
[INFO] Finished at: 2026-09-09T08:17:15+03:00
[INFO] ------------------------------------------------------------------------
```
**Exit Code**: `0` (Success)  
**Compilation Errors**: `0`  
**Compiler Warnings**: `0`  

---

## 2. Framework Starter & Extension Builds

| Module | Build Command | Exit Code | Result | Notes |
|---|---|---|---|---|
| `spring-boot-starter` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Compiles against Spring Boot 3.2.5 dependencies. |
| `quarkus-extension` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Compiles runtime and deployment build-step processors. |
| `micronaut-integration` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Compiles Micronaut 4.x bean definitions and factory. |
| `demo/demo-common` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Compiles shared Java 17 records. |
| `demo/spring-boot-demo` | `mvn clean compile` | `0` | `BUILD SUCCESS` | REST API service. |
| `demo/quarkus-demo` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Reactive REST application. |
| `demo/micronaut-demo` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Micronaut REST service. |
| `demo/vertx-demo` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Reactive Verticle service. |
| `demo/end-to-end-validation` | `mvn clean compile` | `0` | `BUILD SUCCESS` | Test-only validation module. |

---

## 3. Shaded JAR Verification

- The core module creates a standalone runnable fat-jar via `maven-shade-plugin` (version 3.5.2).
- `slf4j-simple` is explicitly excluded from shading to prevent classpath collision when consumers integrate with Logback, Log4j2, or JBoss Logging.
- Main class manifest: `org.embeddedjnosql.db.EmbedJNoSQL`.
