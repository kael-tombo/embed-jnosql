# CONSOLE-001: Administration Console Startup & URL Resolution

## 1. Feature Specification
- **Feature ID**: CONSOLE-001
- **Component**: `EmbedJNoSQLServer`, `PortManager`, `ConfigurationResolver`
- **Goal**: Start embedded HTTP administration console using configured scheme, host, port, and context path.

## 2. Request / Response Lifecycle Trace
```text
EmbedJNoSQL.create(config)
  → EmbedJNoSQL.startConsoleServer(consoleConfig, securityConfig)
  → ConfigurationResolver.resolveConsoleConfig(consoleConfig)
  → PortManager.bindServer(effectiveConfig)
  → HttpServer.create(new InetSocketAddress(host, port), 0)
  → server.start()
  → consoleUrl = scheme + "://" + displayHost + ":" + boundPort + contextPath
```

## 3. Automated Test Evidence
- **Test File**: [ConsoleFeatureValidationTest.java](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/test/java/org/embeddedjnosql/db/ConsoleFeatureValidationTest.java)
- **Test Method**: `testDynamicStartup`
- **Result**: `assertNotNull(db.consoleServer())`, `assertTrue(port > 0)`, `assertNotNull(db.consoleUrl())`
- **Execution Log**:
```text
[TEST EVIDENCE] EmbedJNoSQL Console running at: http://localhost:58244/
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```
- **Verdict**: **VERIFIED**
