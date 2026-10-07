# EmbedJNoSQL — UI Repository Inventory

**Audit Date**: September 9, 2026  
**Auditor**: Frontend Architecture Lead  
**Scope**: Complete inventory of all frontend assets in `src/main/resources/static/`.

---

## 1. Frontend Asset Inventory

| Asset Path | Type | Size | Purpose & Technical Role |
|---|---|---|---|
| `src/main/resources/static/index.html` | HTML5/CSS3/Vanilla JS | 166.9 KB | Primary single-page web console containing full SPA view markup, embedded stylesheet, and interaction controller. |
| `src/main/resources/static/login.html` | HTML5/CSS3/Vanilla JS | 6.5 KB | Dedicated authentication portal for session-cookie and API-key credential entry. |
| `src/main/resources/static/css/style.css` | CSS3 Stylesheet | 8.2 KB | Supplementary design tokens and layout utility styling. |
| `src/main/resources/static/js/enhancements.js` | JavaScript ES6 | 39.4 KB | Advanced UI widgets, charts, real-time metrics rendering, and syntax highlighting. |
| `src/main/resources/static/logo.svg` | SVG Vector Asset | 2.7 KB | Brand logo icon embedded in header and favicon. |
| `src/main/resources/static/favicon.svg` | SVG Icon | 0.5 KB | Browser favicon. |

## 2. Technical Stack & Runtime Dependencies
- **Core Technology**: Pure Vanilla JavaScript (ES2022+), HTML5 Semantic markup, Modern CSS3 with CSS custom properties.
- **External Frameworks**: None. Zero React, Vue, Angular, or Node.js runtime dependencies.
- **Serving Mechanism**: Embedded JDK `HttpServer` / `HttpsServer` (`StaticHandler`) streaming directly from the core JAR classpath.
- **Typography**: Google Fonts (`Inter`, `JetBrains Mono`) with standard system font fallbacks.
