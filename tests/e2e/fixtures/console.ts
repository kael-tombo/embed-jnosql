// Node.js type declarations are intentionally excluded from this minimal E2E dependency set.
// @ts-nocheck
import { randomBytes } from "node:crypto";
import { spawn, spawnSync } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, mkdir, realpath, rm, unlink, readFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { expect, test as base } from "@playwright/test";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../..",
);
const javaFixtureClass = "org.embeddedjnosql.db.console.ConsoleInteractiveTestServer";
const startupTimeoutMs = 120_000;
const javaClasspathPlugin = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath";

type Credentials = {
  username: string;
  password: string;
  apiKey: string;
};

type ConsoleServer = {
  baseURL: string;
  credentials: Credentials;
};

type WorkerFixtures = {
  consoleServer: ConsoleServer;
  workerBaseURL: string;
};

type TestFixtures = {
  page: import("@playwright/test").Page;
  anonymousPage: import("@playwright/test").Page;
};

function appendTail(current: string, chunk: string): string {
  const combined = current + chunk;
  return combined.length > 32_000 ? combined.slice(-32_000) : combined;
}

function redactOutput(output: string, credentials: Credentials): string {
  return [credentials.username, credentials.password, credentials.apiKey]
    .reduce((safe, secret) => safe.replaceAll(secret, "[redacted]"), output)
    .trim();
}

function generatedCredentials(): Credentials {
  return {
    username: `e2e-${randomBytes(12).toString("hex")}`,
    password: `${randomBytes(32).toString("base64url")}Aa1!`,
    apiKey: randomBytes(48).toString("hex"),
  };
}

function javaExecutable(): string {
  const executableName = process.platform === "win32" ? "java.exe" : "java";
  if (process.env.JAVA_HOME) {
    return path.join(process.env.JAVA_HOME, "bin", executableName);
  }

  const result = spawnSync("java", ["-XshowSettings:properties", "-version"], {
    encoding: "utf8",
    windowsHide: true,
  });
  const settings = [result.stdout, result.stderr].filter(Boolean).join("\n");
  const javaHome = settings.match(/^\s*java\.home\s*=\s*(.+)$/m)?.[1]?.trim();
  return javaHome ? path.join(javaHome, "bin", executableName) : "java";
}

function prepareJavaClasspath(classpathFile: string): void {
  const wrapper = process.platform === "win32" ? "mvnw.cmd" : "./mvnw";
  const args = [
    "-q",
    "-DskipTests",
    "test-compile",
    javaClasspathPlugin,
    "-DincludeScope=test",
    `-Dmdep.outputFile=${classpathFile}`,
  ];
  const windowsCommand = [
    `call "${path.join(repositoryRoot, wrapper)}"`,
    ...args.map((argument) => `"${argument.replaceAll('"', '""')}"`),
  ].join(" ");
  const result = spawnSync(
    process.platform === "win32" ? process.env.ComSpec ?? "cmd.exe" : wrapper,
    process.platform === "win32" ? ["/d", "/s", "/c", windowsCommand] : args,
    {
      cwd: repositoryRoot,
      encoding: "utf8",
      maxBuffer: 10 * 1024 * 1024,
      timeout: startupTimeoutMs,
      windowsHide: true,
      windowsVerbatimArguments: process.platform === "win32",
    },
  );

  if (result.error || result.status !== 0) {
    const output = [result.stdout, result.stderr, result.error?.message]
      .filter(Boolean)
      .join("\n");
    throw new Error(`Could not compile the Java console fixture or resolve its classpath.\n${output}`);
  }

}

async function readClasspath(classpathFile: string): Promise<string> {
  const output = await readFile(classpathFile, "utf8");
  const classpath = output.trim();
  if (!classpath) {
    throw new Error("Maven produced an empty Java test classpath.");
  }
  return classpath;
}

async function waitForServer(
  child: ReturnType<typeof spawn>,
  getReadyURL: () => string | undefined,
  getOutput: () => string,
  getSpawnError: () => Error | undefined,
  credentials: Credentials,
): Promise<string> {
  const deadline = Date.now() + startupTimeoutMs;

  while (Date.now() < deadline) {
    const spawnError = getSpawnError();
    if (spawnError) {
      throw new Error(
        `Could not start the Java console fixture: ${redactOutput(spawnError.message, credentials)}`,
      );
    }

    if (child.exitCode !== null || child.signalCode !== null) {
      throw new Error(
        `Console fixture exited before becoming ready.\n${redactOutput(getOutput(), credentials)}`,
      );
    }

    const baseURL = getReadyURL();
    if (baseURL) {
      try {
        const response = await fetch(baseURL, {
          redirect: "manual",
          signal: AbortSignal.timeout(2_000),
        });
        await response.body?.cancel();
        if (response.status >= 200 && response.status < 400) {
          return baseURL;
        }
      } catch {
        // The ready line can arrive just before the socket accepts requests.
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 150));
  }

  throw new Error(
    `Timed out waiting for the loopback console readiness URL.\n${redactOutput(getOutput(), credentials)}`,
  );
}

async function waitForProcessClose(
  child: ReturnType<typeof spawn>,
  timeoutMs: number,
): Promise<boolean> {
  if (child.exitCode !== null || child.signalCode !== null) {
    return true;
  }

  const closed = once(child, "close").then(() => true);
  let timeout: ReturnType<typeof setTimeout> | undefined;
  const didClose = await Promise.race([
    closed,
    new Promise<boolean>((resolve) => {
      timeout = setTimeout(() => resolve(false), timeoutMs);
    }),
  ]);
  if (timeout) {
    clearTimeout(timeout);
  }
  return didClose;
}

async function stopExactProcess(child: ReturnType<typeof spawn>): Promise<void> {
  if (child.exitCode !== null || child.signalCode !== null) {
    return;
  }

  child.stdin?.end("STOP\n");
  if (await waitForProcessClose(child, 10_000)) {
    return;
  }

  child.kill("SIGTERM");
  if (await waitForProcessClose(child, 5_000)) {
    return;
  }

  child.kill("SIGKILL");
  if (!(await waitForProcessClose(child, 5_000))) {
    throw new Error(`Could not terminate the Java console process with PID ${child.pid}.`);
  }
}

async function removeOwnedTempDirectory(directory: string, resolvedDirectory: string): Promise<void> {
  const resolvedParent = await realpath(os.tmpdir());
  const relative = path.relative(resolvedParent, resolvedDirectory);

  if (
    path.dirname(resolvedDirectory) === resolvedParent &&
    path.basename(resolvedDirectory).startsWith("embedjnosql-console-e2e-") &&
    relative === path.basename(resolvedDirectory)
  ) {
    for (let attempt = 0; attempt < 30; attempt += 1) {
      const currentResolution = await realpath(directory).catch(() => undefined);
      if (currentResolution !== resolvedDirectory) {
        return;
      }

      try {
        await rm(resolvedDirectory, { recursive: true });
        return;
      } catch (error) {
        const code = (error as { code?: string }).code;
        if (!["EBUSY", "EPERM", "ENOTEMPTY"].includes(code ?? "") || attempt === 29) {
          throw error;
        }
        await new Promise((resolve) => setTimeout(resolve, 250));
      }
    }
  }
}

export const test = base.extend<TestFixtures, WorkerFixtures>({
  consoleServer: [
    async ({}, use, workerInfo) => {
      const createdDirectory = await mkdtemp(
        path.join(os.tmpdir(), "embedjnosql-console-e2e-"),
      );
      const resolvedDirectory = await realpath(createdDirectory);
      const dataDirectory = path.join(resolvedDirectory, "data");
      const classpathFile = path.join(
        resolvedDirectory,
        `classpath-${process.pid}-${workerInfo.workerIndex}.txt`,
      );
      const credentials = generatedCredentials();
      let child: ReturnType<typeof spawn> | undefined;

      try {
        await mkdir(dataDirectory);
        prepareJavaClasspath(classpathFile);
        const classpath = await readClasspath(classpathFile);
        await unlink(classpathFile);

        child = spawn(
          javaExecutable(),
          [
            "-cp",
            [
              path.join(repositoryRoot, "target", "test-classes"),
              path.join(repositoryRoot, "target", "classes"),
              classpath,
            ].join(path.delimiter),
            javaFixtureClass,
            "0",
            dataDirectory,
          ],
          {
            cwd: repositoryRoot,
            env: {
              ...process.env,
              E2E_ADMIN_USER: credentials.username,
              E2E_ADMIN_PASSWORD: credentials.password,
              E2E_API_KEY: credentials.apiKey,
            },
            stdio: ["pipe", "pipe", "pipe"],
            windowsHide: true,
          },
        );

        let output = "";
        let partialLine = "";
        let readyURL: string | undefined;
        let spawnError: Error | undefined;
        child.stdout?.on("data", (chunk: Buffer) => {
          output = appendTail(output, chunk.toString("utf8"));
          const lines = (partialLine + chunk.toString("utf8")).split(/\r?\n/);
          partialLine = lines.pop() ?? "";
          for (const line of lines) {
            const match = line.match(/^E2E_SERVER_READY (http:\/\/127\.0\.0\.1:\d+\/)$/);
            if (match) {
              readyURL = match[1];
            }
          }
        });
        child.stderr?.on("data", (chunk: Buffer) => {
          output = appendTail(output, chunk.toString("utf8"));
        });
        child.on("error", (error) => {
          spawnError = error;
          output = appendTail(output, error.message);
        });

        const baseURL = await waitForServer(
          child,
          () => readyURL,
          () => output,
          () => spawnError,
          credentials,
        );
        await use({ baseURL, credentials });
      } finally {
        if (child) {
          await stopExactProcess(child);
        }
        await removeOwnedTempDirectory(createdDirectory, resolvedDirectory);
      }
    },
    { scope: "worker", timeout: startupTimeoutMs },
  ],
  workerBaseURL: [
    async ({ consoleServer }, use) => {
      await use(consoleServer.baseURL);
    },
    { scope: "worker" },
  ],
  baseURL: async ({ workerBaseURL }, use) => {
    await use(workerBaseURL);
  },
  page: async ({ browser, baseURL, consoleServer }, use) => {
    const context = await browser.newContext({ baseURL });
    try {
      const page = await context.newPage();
      await page.goto("/login.html?next=%2F");
      await page.getByLabel("Username").fill(consoleServer.credentials.username);
      await page.getByLabel("Password").fill(consoleServer.credentials.password);
      await page.getByRole("button", { name: "Sign In" }).click();
      await page.waitForURL((url) => url.pathname === "/" || url.pathname.endsWith("/index.html"));
      await expect(page.locator("#chipHealthText")).toHaveText("Healthy");
      await use(page);
    } finally {
      await context.close();
    }
  },
  anonymousPage: async ({ browser, baseURL }, use) => {
    const context = await browser.newContext({ baseURL });
    try {
      const page = await context.newPage();
      await page.goto("/");
      await use(page);
    } finally {
      await context.close();
    }
  },
});

export { expect };
