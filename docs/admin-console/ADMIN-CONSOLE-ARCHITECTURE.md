# EmbedJNoSQL Administration Console Architecture

## 1. Overview & System Mission

The EmbedJNoSQL Administration Console provides an embedded, framework-independent, low-overhead HTTP/REST server and web user interface for inspecting, managing, and querying EmbedJNoSQL multi-model databases in real-time.

It runs directly inside the host JVM process, binding to local network interfaces without requiring external dependencies, sidecar containers, or heavy application servers.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Host JVM (Application Process)                                                  │
│                                                                                 │
│  ┌────────────────────────┐         HTTP/REST         ┌──────────────────────┐  │
│  │ Single-Page Web App UI │ ◄───────────────────────► │   EmbedJNoSQLServer     │  │
│  │ (HTML5/CSS3/Vanilla JS)│                           │ (com.sun.net.http)   │  │
│  └────────────────────────┘                           └──────────┬───────────┘  │
│                                                                  │              │
│                                                       ┌──────────▼───────────┐  │
│                                                       │   Security Filters   │  │
│                                                       │ (Auth, CSRF, Rate,   │  │
│                                                       │  BruteForce, Headers)│  │
│                                                       └──────────┬───────────┘  │
│                                                                  │              │
│                                                       ┌──────────▼───────────┐  │
│                                                       │      EmbedJNoSQL API    │  │
│                                                       │ (Documents, KV, CF,  │  │
│                                                       │  Query, Tx, Vectors) │  │
│                                                       └──────────┬───────────┘  │
│                                                                  │              │
│                                                       ┌──────────▼───────────┐  │
│                                                       │    Storage Engine    │  │
│                                                       │ (Memory/File/LSM/BTr)│  │
│                                                       └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 2. Architectural Layers

| Layer | Component | Description |
|---|---|---|
| **Presentation** | `index.html`, `login.html`, `enhancements.js`, `enhancements.css` | Lightweight single-page application built with zero external runtime dependencies. |
| **Transport** | `EmbedJNoSQLServer`, `PortManager`, `HttpsServer` | Built on high-performance JDK `com.sun.net.httpserver.HttpServer`. |
| **Configuration** | `ConsoleConfig`, `SecurityConfig`, `ConfigurationResolver` | Type-safe configuration records with precedence resolution across code, properties, env vars, and defaults. |
| **Security Guard** | `CsrfTokenManager`, `SecureSessionManager`, RateLimiter, FailedLoginTracker | Defense-in-depth security barrier enforcing authentication, CSRF synchronizer tokens, rate limits, brute-force lockout, and OWASP headers. |
| **API Handlers** | 20 HTTP handlers | RESTful endpoints providing full CRUD, query execution, indexing, backup, transaction lifecycle, metrics, and CDC. |
| **Database Core** | `EmbedJNoSQL`, `DocumentCollection`, `KeyValueBucket`, `ColumnFamily` | Multi-model database internals and query engine. |
| **Persistence** | `InMemoryEngine`, `FileEngine`, `LSMTreeEngine`, `BTreeEngine` | Storage SPI providers. |

## 3. Key Design Decisions

1. **JDK-Native HTTP Engine**: Zero third-party web server dependencies (no embedded Tomcat, Jetty, or Netty footprint added to core).
2. **Localhost-Only Default**: Console binds to `127.0.0.1` by default to prevent accidental exposure to external network interfaces.
3. **Collision Avoidance**: Intelligent port manager sequentially probes and binds available ports in a configurable range.
4. **Synchronizer Token CSRF**: State-changing endpoints require single-use/per-session cryptographically random CSRF tokens when session authentication is used.
5. **Brute-Force Lockout**: IP-level tracking automatically locks out clients exceeding configured failure limits.
