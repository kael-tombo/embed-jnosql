# Intelligent Port Management & Collision Resolution

## 1. Motivation

In embedded microservice deployments, test execution environments, CI/CD pipelines, and local developer workstations, port collisions on static ports (such as `8080` or `9090`) are frequent causes of startup failure.

EmbedJNoSQL provides authoritative, kernel-level port detection and collision avoidance through `PortManager`.

## 2. Binding Lifecycle & Behavior

```
               [ Start PortManager.bindServer(config) ]
                                │
                                ▼
                       Is port <= 0? (Ephemeral)
                       ├── Yes ──► Bind port 0 (OS allocates) ──► SUCCESS
                       └── No
                                │
                                ▼
                   Attempt Bind preferredPort
                       ├── Success ──► Return bound port ──► SUCCESS
                       └── Fails (SocketException / In Use)
                                │
                                ▼
                Is failIfPreferredPortUnavailable == true?
                OR intelligentPort == false?
                       ├── Yes ──► Throw PortConflictException ──► FAIL
                       └── No
                                │
                                ▼
                Iterate p in [minPort .. maxPort] (excluding preferred)
                       ├── Finds free port p ──► Bind p ──► SUCCESS
                       └── Range exhausted ──► Throw PortConflictException ──► FAIL
```

## 3. Strict vs. Flexible Modes

| Mode | Configuration | Behavior on Collision |
|---|---|---|
| **Intelligent (Default)** | `intelligentPort=true`, `failIfPreferredPortUnavailable=false` | Searches `[minPort, maxPort]` sequentially for next available socket. Logs warning explaining the alternate port selected. |
| **Strict Preferred** | `failIfPreferredPortUnavailable=true` | Throws `PortConflictException` immediately if preferred port cannot be bound. |
| **Strict Range** | `intelligentPort=true`, `minPort=9000, maxPort=9005` | Constrains probing strictly to the specified boundary. Fails if all 6 ports are taken. |
| **Ephemeral Dynamic** | `port=0` | Asks OS kernel to assign any free ephemeral port. Never collides. Ideal for automated testing. |

## 4. Diagnostics & Logging

When a collision occurs and an alternate port is bound, the server logs a clear diagnostic event:
```
[PortManager] Preferred port 9090 on 127.0.0.1 is occupied
[PortManager] Intelligent fallback: bound to port 9091 on 127.0.0.1
[EmbedJNoSQLServer] Administration Console available at: http://localhost:9091/jnosql-admin/
```
