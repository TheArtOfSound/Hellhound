import { env } from "../env.js";

export type SandboxJob = {
  kind: "command" | "npm" | "git" | "browser";
  command?: string;
  repo?: string;
  timeoutMs?: number;
};

export async function runSandbox(job: SandboxJob) {
  const response = await fetch(`${env.sandboxUrl}/run`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(job)
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data?.error || `Sandbox failed: ${response.status}`);
  }

  return data;
}
