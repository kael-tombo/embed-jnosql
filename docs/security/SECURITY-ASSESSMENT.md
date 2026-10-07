# EmbedJNoSQL — Security Architecture Assessment

**Auditor**: Application Security Specialist  
**Date**: September 9, 2026  

---

## 1. Threat Model & Mitigations

| Threat Category | Risk Description | Architecture Mitigation | Status |
|---|---|---|---|
| **Unauthorized API Access** | Malicious requests to embedded HTTP server | Configurable `apiKey` + `SecureSessionManager` with HttpOnly cookies | **MITIGATED** |
| **Denial of Service (OOM)** | Large payload submissions exhausting JVM heap | `maxRequestSizeBytes` enforced (10MB default) | **MITIGATED** |
| **Slowloris / Hanging Queries** | Unbounded aggregation queries stalling threads | `queryTimeoutSeconds` enforced (30s default) | **MITIGATED** |
| **Path Traversal in Backup** | Arbitrary file writing via backup API | Backup paths validated against allowed project directories | **MITIGATED** |
| **Session Hijacking** | XSS stealing authentication tokens | HttpOnly, SameSite=Strict cookies; cryptographically secure 256-bit IDs | **MITIGATED** |
