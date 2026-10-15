# Console browser tests

This isolated Playwright project exercises the real Java console fixture and its
seeded engine state. It requires Node.js 20 or newer, Java 17 or newer, and the
repository's Maven Wrapper.

## Install and run

From the repository root:

```powershell
npm --prefix tests/e2e ci
npm --prefix tests/e2e exec -- playwright install chromium
npm --prefix tests/e2e run test
```

Useful scripts:

- `npm --prefix tests/e2e run test` — headless browser suite.
- `npm --prefix tests/e2e run test:headed` — headed browser suite.
- `npm --prefix tests/e2e run test:report` — open the most recent HTML report.

Each Playwright worker compiles the Java test fixture, starts its own server
bound to `127.0.0.1` on port `0`, and receives a unique temporary data directory.
The console stores backups below that worker's data directory. Teardown stops
the exact Java child process before removing only the resolved temporary
directory created for that worker.

Authentication values are generated in memory for each worker and supplied to
the Java fixture through its environment. They are never written to stdout,
readiness files, Playwright traces, videos, screenshots, or reports. Test output
is written below `tests/e2e/.artifacts/`; screenshots, videos, and traces are
disabled to avoid recording credentials.
