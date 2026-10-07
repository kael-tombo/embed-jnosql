# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## Reporting a Vulnerability

We take the security of **EmbedJNoSQL** seriously. If you discover a security vulnerability, please do NOT create a public issue on GitHub.

Instead, please send an email to:
**security@embedjnosql.org** (or contact the maintainers directly).

Please include:
1. A description of the vulnerability and potential impact.
2. Steps to reproduce or a minimal proof-of-concept repository.
3. Information about affected versions and environments.

### Response Timeline
- **Initial Response**: Within 48 hours acknowledging receipt.
- **Triage & Remediation Plan**: Within 7 business days.
- **Security Advisory & Patch Release**: Coordinated disclosure once patch is verified.

---

## Embedded Database Security Considerations

### 1. In-Process Execution & Memory Safety
- EmbedJNoSQL executes entirely inside the host JVM process.
- Isolation between tenants is the responsibility of the host application architecture.
- When running in shared environments, do not expose internal collections across tenant boundaries without authentication checks.

### 2. HTTP Developer Console (`EmbedJNoSQLServer`)
- By default, the admin console does not enforce authentication unless configured.
- In production environments, either:
  1. Disable the embedded HTTP server by not invoking `db.startServer()`.
  2. Set a strong API key using `server.setApiKey("your-secure-key")`.
  3. Ensure the admin port (default 8080) is bound to `127.0.0.1` and protected by network firewalls.

### 3. File System Permissions
- Persistent storage engines (`FILE`, `B_TREE`, `LSM_TREE`) write state to the configured `dataDir`.
- Ensure directory permissions restrict write and read access to the OS user running the JVM process.
