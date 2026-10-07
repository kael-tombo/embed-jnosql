# Environment Specification

- **Operating System**: Windows 11 / Windows Server x64
- **Java Runtime**: OpenJDK 23.0.1 / Java 17+ compatible bytecode
- **Maven**: Apache Maven 3.9.x
- **Spring Boot**: 3.2.5
- **EmbedJNoSQL Version**: 1.0.0-GA
- **Browser Subagent**: Playwright / Chromium headless browser engine
- **Host Binding**: 127.0.0.1 (Localhost-only secure binding)
- **Primary Ports**:
  - Application Service: 8081
  - Embedded Admin Console: 9090
- **Storage Engine**: `IN_MEMORY`
- **Initial Dataset**: Pre-seeded E-Commerce Catalog (`products`, `inventory`, `price_cache`)
