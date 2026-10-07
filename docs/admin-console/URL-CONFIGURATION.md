# Administration Console URL & Endpoint Configuration

## 1. Overview

The administration console URL is completely configurable at the application and environment levels. The effective URL is dynamically constructed at server startup based on:
- **Scheme**: `http` or `https`
- **Host**: Localhost binding (`127.0.0.1`), specific IP, or hostname
- **Port**: Bound port number (configured, auto-resolved, or ephemeral)
- **Context Path**: Custom prefix (e.g., `/`, `/admin`, `/jnosql-admin`)

Formula:
```
effective_url = {scheme}://{display_host}:{bound_port}{normalized_context_path}/
```

## 2. Configuration Options

### In Java Code (`ConsoleConfig`)
```java
ConsoleConfig config = ConsoleConfig.builder()
        .enabled(true)
        .scheme("http")
        .host("127.0.0.1")
        .port(9090)
        .contextPath("/jnosql-admin")
        .intelligentPort(true)
        .maxPortAttempts(50)
        .portRange(9000, 9100)
        .failIfPreferredPortUnavailable(false)
        .localhostOnly(true)
        .build();
```

### In Spring Boot `application.yml`
```yaml
embedjnosql:
  console:
    enabled: true
    port: 9090
    context-path: /jnosql-admin
    intelligent-port: true
    max-port-attempts: 50
    host: 127.0.0.1
    scheme: http
    fail-if-preferred-port-unavailable: false
    localhost-only: true
```

### In System Properties / Environment Variables
| Property | Environment Variable | Default | Purpose |
|---|---|---|---|
| `embedjnosql.console.enabled` | `EMBEDJNOSQL_CONSOLE_ENABLED` | `false` | Enable console server |
| `embedjnosql.console.port` | `EMBEDJNOSQL_CONSOLE_PORT` | `9090` | Preferred port |
| `embedjnosql.console.context-path` | `EMBEDJNOSQL_CONSOLE_CONTEXT_PATH` | `/` | Context path prefix |
| `embedjnosql.console.host` | `EMBEDJNOSQL_CONSOLE_HOST` | `127.0.0.1` | Network binding host |
| `embedjnosql.console.scheme` | `EMBEDJNOSQL_CONSOLE_SCHEME` | `http` | URL scheme (`http`/`https`) |
| `embedjnosql.console.intelligent-port`| `EMBEDJNOSQL_CONSOLE_INTELLIGENT_PORT`| `true` | Enable collision avoidance |
| `embedjnosql.console.min-port` | `EMBEDJNOSQL_CONSOLE_MIN_PORT` | `9090` | Lower port range limit |
| `embedjnosql.console.max-port` | `EMBEDJNOSQL_CONSOLE_MAX_PORT` | `9140` | Upper port range limit |
| `embedjnosql.console.fail-if-preferred-port-unavailable` | `EMBEDJNOSQL_CONSOLE_FAIL_IF_PREFERRED_PORT_UNAVAILABLE` | `false` | Strict port binding |

## 3. Context Path Normalization Rules

1. Any context path provided is trimmed of leading and trailing whitespace.
2. If blank, empty, or `"/"`, it normalizes to `"/"`.
3. If not starting with `"/"`, a leading `"/"` is prepended (`"admin"` → `"/admin"`).
4. Any trailing `"/"` is stripped for internal route dispatch (`"/admin/"` → `"/admin"`).
5. When building the console URL, the path is formatted as `/{path}/` (`http://localhost:9090/admin/`).
