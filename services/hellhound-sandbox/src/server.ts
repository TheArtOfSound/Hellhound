import Fastify from "fastify";
import cors from "@fastify/cors";
import { spawn } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

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

app.get("/health", async () => ({
  ok: true,
  service: "hellhound-sandbox",
  time: new Date().toISOString()
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

app.listen({ port: Number(process.env.PORT || 8790), host: "0.0.0.0" });
