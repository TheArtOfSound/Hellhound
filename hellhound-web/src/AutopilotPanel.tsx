import { useEffect, useState } from "react";
import type { ProviderPreset } from "./providers";

type Job = {
  id: string;
  kind: string;
  objective: string;
  status: "running" | "paused" | "done" | "failed";
  plan: string[];
  currentStep: number;
  maxSteps: number;
  outputs: string[];
  reflections: string[];
  memories: string[];
  error?: string;
};

type Props = {
  provider: ProviderPreset;
  apiKey: string;
  temperature: number;
};

const AGENT_URL_KEY = "hellhound-agent-url";

export default function AutopilotPanel({ provider, apiKey, temperature }: Props) {
  const [agentUrl, setAgentUrl] = useState(() => localStorage.getItem(AGENT_URL_KEY) || "http://127.0.0.1:18888");
  const [objective, setObjective] = useState("Say hi, then suggest one useful thing I should do next, then continue without waiting.");
  const [kind, setKind] = useState("conversation");
  const [maxSteps, setMaxSteps] = useState(3);
  const [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    localStorage.setItem(AGENT_URL_KEY, agentUrl);
  }, [agentUrl]);

  useEffect(() => {
    if (!job?.id) return;

    const timer = window.setInterval(async () => {
      try {
        const res = await fetch(`${agentUrl}/autopilot/jobs/${job.id}`);
        const data = await res.json();
        if (data?.job) setJob(data.job);
      } catch {
        // Polling stays quiet unless the user manually starts/actions.
      }
    }, 3000);

    return () => window.clearInterval(timer);
  }, [agentUrl, job?.id]);

  async function start() {
    setError("");

    if (!apiKey.trim()) {
      setError("Add a provider API key first.");
      return;
    }

    const res = await fetch(`${agentUrl}/autopilot/start`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        userId: "bryan",
        objective,
        kind,
        maxSteps,
        provider: {
          apiKey,
          baseUrl: provider.baseUrl,
          model: provider.model,
          temperature
        }
      })
    });

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      setError(data?.error ? JSON.stringify(data.error) : `Autopilot failed: ${res.status}`);
      return;
    }

    setJob(data.job);
  }

  async function action(path: string) {
    if (!job) return;
    setError("");

    const res = await fetch(`${agentUrl}/autopilot/jobs/${job.id}/${path}`, { method: "POST" });
    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
      setError(data?.error || `Action failed: ${res.status}`);
      return;
    }

    setJob(data.job);
  }

  const latest = job?.outputs?.[job.outputs.length - 1] || "No autopilot output yet.";
  const next = job ? job.plan[job.currentStep] || "Finished." : "No job running.";

  return (
    <section className="autopilot-panel">
      <label>Autopilot backend</label>
      <input value={agentUrl} onChange={(e) => setAgentUrl(e.target.value)} />

      <label>Objective</label>
      <textarea value={objective} onChange={(e) => setObjective(e.target.value)} />

      <div className="compact-grid">
        <div>
          <label>Kind</label>
          <select value={kind} onChange={(e) => setKind(e.target.value)}>
            <option value="conversation">conversation</option>
            <option value="book">book</option>
            <option value="paper">paper</option>
            <option value="code">code</option>
            <option value="research">research</option>
            <option value="project">project</option>
            <option value="general">general</option>
          </select>
        </div>
        <div>
          <label>Steps</label>
          <input type="number" min="1" max="80" value={maxSteps} onChange={(e) => setMaxSteps(Number(e.target.value))} />
        </div>
      </div>

      <div className="header-actions">
        <button className="armed" onClick={start}>Start autopilot</button>
        <button onClick={() => action("step")} disabled={!job || job.status === "done"}>Step now</button>
        <button onClick={() => action(job?.status === "paused" ? "resume" : "pause")} disabled={!job || job.status === "done"}>
          {job?.status === "paused" ? "Resume" : "Pause"}
        </button>
      </div>

      {error ? <small>{error}</small> : null}

      {job ? (
        <>
          <small>{job.status} · step {job.currentStep}/{job.maxSteps}</small>
          <small>Next: {next}</small>
          <div className="autopilot-output">{latest}</div>
        </>
      ) : (
        <small>This starts a backend job that keeps stepping without another user prompt.</small>
      )}
    </section>
  );
}
