import Fastify from "fastify";
import cors from "@fastify/cors";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { chromium } from "playwright";

const app = Fastify({ logger: true });
await app.register(cors, { origin: true });

const BLOCKED = [
  "rm -rf /",
  ":(){",
  "mkfs",
  "dd if=",
  "shutdown",
  "reboot"
];

function safeCommand(command: string) {
  return !BLOCKED.some((bad) => command.includes(bad));
}

async function runCommand(command: string, timeoutMs = 20000) {
  if (!safeCommand(command)) {
    throw new Error("Blocked unsafe command.");
  }

  const dir = await mkdtemp(path.join(tmpdir(), "hellhound-"));
  const started = Date.now();

  return new Promise(async (resolve) => {
    let stdout = "";
    let stderr = "";

    const child = spawn("bash", ["-lc", command], {
      cwd: dir,
      env: {
        ...process.env,
        npm_config_yes: "true"
      }
    });

    const kill = setTimeout(() => {
      child.kill("SIGKILL");
    }, timeoutMs);

    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString();
      stdout = stdout.slice(-20000);
    });

    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
      stderr = stderr.slice(-20000);
    });

    child.on("close", async (code) => {
      clearTimeout(kill);
      await rm(dir, { recursive: true, force: true }).catch(() => {});
      resolve({
        code,
        ms: Date.now() - started,
        stdout,
        stderr
      });
    });
  });
}

type BrowserStep = {
  at: string;
  action: string;
  url: string;
  title: string;
  text: string;
  screenshotPngBase64?: string;
};

function safeUrl(input: string) {
  const url = new URL(input);
  if (!["http:", "https:"].includes(url.protocol)) throw new Error("Only http/https URLs are allowed.");
  return url.toString();
}

async function runBrowserObjective(input: { objective: string; startUrl?: string; maxSteps?: number }) {
  const objective = String(input.objective || "").trim();
  if (!objective) throw new Error("Missing objective.");

  const maxSteps = Math.min(Math.max(Number(input.maxSteps || 4), 1), 8);
  const startUrl = safeUrl(input.startUrl || "https://example.com");
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1365, height: 768 } });
  const steps: BrowserStep[] = [];

  async function capture(action: string) {
    const title = await page.title().catch(() => "");
    const text = await page.locator("body").innerText({ timeout: 2500 }).catch(() => "");
    const screenshot = await page.screenshot({ type: "png", fullPage: false }).catch(() => null);
    steps.push({
      at: new Date().toISOString(),
      action,
      url: page.url(),
      title,
      text: text.replace(/\s+/g, " ").trim().slice(0, 1800),
      screenshotPngBase64: screenshot ? screenshot.toString("base64") : undefined
    });
  }

  try {
    await page.goto(startUrl, { waitUntil: "domcontentloaded", timeout: 20000 });
    await capture(`Opened ${startUrl}`);

    const lower = objective.toLowerCase();
    const wantsSearch = /\b(search|google|look up|find|research|latest|news)\b/.test(lower);

    if (wantsSearch && maxSteps > 1) {
      const query = objective.replace(/\b(search|google|look up|find|research|latest|news)\b/gi, "").trim() || objective;
      const q = encodeURIComponent(query);
      await page.goto(`https://www.google.com/search?q=${q}`, { waitUntil: "domcontentloaded", timeout: 20000 });
      await capture(`Searched web for: ${query}`);
    }

    if (maxSteps > 2) {
      const links = await page.locator("a[href]").evaluateAll((nodes) => nodes
        .map((node) => ({ text: (node.textContent || "").trim(), href: (node as HTMLAnchorElement).href }))
        .filter((item) => item.href && /^https?:\/\//.test(item.href))
        .slice(0, 12)
      ).catch(() => [] as Array<{ text: string; href: string }>);

      const target = links.find((item) => item.text.length > 8 && !item.href.includes("google.com")) || links[0];
      if (target?.href) {
        await page.goto(target.href, { waitUntil: "domcontentloaded", timeout: 20000 });
        await capture(`Opened result: ${target.text.slice(0, 90) || target.href}`);
      }
    }

    if (maxSteps > 3) {
      await page.mouse.wheel(0, 700).catch(() => {});
      await page.waitForTimeout(500);
      await capture("Scrolled page and inspected visible content");
    }

    return {
      ok: true,
      objective,
      startedAt: steps[0]?.at,
      completedAt: new Date().toISOString(),
      steps,
      summary: steps.map((step, i) => `${i + 1}. ${step.action} — ${step.title || step.url}`).join("\n")
    };
  } finally {
    await browser.close().catch(() => {});
  }
}

app.get("/health", async () => ({
  ok: true,
  service: "hellhound-sandbox",
  time: new Date().toISOString(),
  tools: ["command", "browser.playwright"]
}));

app.post("/run", async (request, reply) => {
  const body = request.body as any;
  const command = body?.command;

  if (!command || typeof command !== "string") {
    return reply.code(400).send({ error: "Missing command" });
  }

  const result = await runCommand(command, Number(body.timeoutMs || 20000));
  return result;
});

app.post("/browser/run", async (request, reply) => {
  try {
    return await runBrowserObjective(request.body as any);
  } catch (error) {
    return reply.code(400).send({ error: error instanceof Error ? error.message : String(error) });
  }
});

app.listen({ port: Number(process.env.PORT || 8790), host: "0.0.0.0" });
