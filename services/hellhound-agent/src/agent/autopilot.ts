import { nanoid } from "nanoid";
import { runAgent } from "./loop.js";

export type AutopilotKind =
  | "book"
  | "paper"
  | "code"
  | "research"
  | "project"
  | "conversation"
  | "general";

type AutopilotProvider = {
  apiKey?: string;
  baseUrl: string;
  model: string;
  temperature?: number;
};

export type AutopilotJob = {
  id: string;
  userId: string;
  kind: AutopilotKind;
  objective: string;
  status: "running" | "paused" | "done" | "failed";
  plan: string[];
  currentStep: number;
  maxSteps: number;
  outputs: string[];
  reflections: string[];
  memories: string[];
  createdAt: string;
  updatedAt: string;
  error?: string;
};

const jobs = new Map<string, AutopilotJob>();
const jobProviders = new Map<string, AutopilotProvider>();
let loopStarted = false;

function now() {
  return new Date().toISOString();
}

function inferKind(objective: string): AutopilotKind {
  if (/\b(book|novel|chapter|story)\b/i.test(objective)) return "book";
  if (/\b(paper|essay|thesis|research paper|latex)\b/i.test(objective)) return "paper";
  if (/\b(code|app|repo|npm|build|program|script)\b/i.test(objective)) return "code";
  if (/\b(research|sources|study|investigate)\b/i.test(objective)) return "research";
  if (/\b(project|business|plan|launch)\b/i.test(objective)) return "project";
  if (/\b(hi|hello|talk|chat|check in)\b/i.test(objective)) return "conversation";
  return "general";
}

function buildInitialPlan(kind: AutopilotKind, objective: string, maxSteps: number) {
  if (kind === "book") {
    return Array.from({ length: maxSteps }, (_, i) => {
      if (i === 0) return "Create title, premise, cast, tone, and chapter roadmap.";
      if (i === maxSteps - 1) return "Read the full manuscript, critique it, record opinions, memories, and final revision notes.";
      return `Write chapter ${i}, then reflect on continuity, character movement, and what the next chapter must do.`;
    });
  }

  if (kind === "code") {
    return [
      "Define requirements and architecture.",
      "Create file/module plan.",
      "Write the first implementation pass.",
      "Run or simulate tests.",
      "Inspect failures and revise.",
      "Document usage and next work."
    ].slice(0, maxSteps);
  }

  if (kind === "paper") {
    return [
      "Define thesis, claims, and structure.",
      "Draft abstract and outline.",
      "Write section one.",
      "Write section two.",
      "Write section three.",
      "Self-review for weak reasoning.",
      "Finalize citations, limitations, and conclusion."
    ].slice(0, maxSteps);
  }

  return Array.from({ length: maxSteps }, (_, i) => {
    if (i === 0) return "Clarify objective, constraints, and success criteria.";
    if (i === maxSteps - 1) return "Review all work, extract memories, and give final opinion.";
    return `Execute autonomous step ${i}: make progress, review it, and choose the next move.`;
  });
}

export function createAutopilotJob(input: {
  userId: string;
  objective: string;
  kind?: AutopilotKind;
  maxSteps?: number;
  provider?: AutopilotProvider;
}) {
  const kind = input.kind || inferKind(input.objective);
  const maxSteps = Math.min(Math.max(input.maxSteps || (kind === "book" ? 52 : 8), 1), 80);

  const job: AutopilotJob = {
    id: nanoid(),
    userId: input.userId,
    kind,
    objective: input.objective,
    status: "running",
    plan: buildInitialPlan(kind, input.objective, maxSteps),
    currentStep: 0,
    maxSteps,
    outputs: [],
    reflections: [],
    memories: [],
    createdAt: now(),
    updatedAt: now()
  };

  jobs.set(job.id, job);
  if (input.provider) jobProviders.set(job.id, input.provider);
  ensureAutopilotLoop();
  return job;
}

export function listAutopilotJobs(userId?: string) {
  return [...jobs.values()]
    .filter((job) => !userId || job.userId === userId)
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export function getAutopilotJob(id: string) {
  return jobs.get(id) || null;
}

export function pauseAutopilotJob(id: string) {
  const job = jobs.get(id);
  if (!job) return null;
  job.status = "paused";
  job.updatedAt = now();
  return job;
}

export function resumeAutopilotJob(id: string) {
  const job = jobs.get(id);
  if (!job) return null;
  if (job.status !== "done") job.status = "running";
  job.updatedAt = now();
  ensureAutopilotLoop();
  return job;
}

export async function runAutopilotStep(job: AutopilotJob) {
  if (job.status !== "running") return job;

  const stepText = job.plan[job.currentStep] || `Continue objective step ${job.currentStep + 1}.`;
  const priorOutput = job.outputs.slice(-3).join("\n\n---\n\n");
  const priorReflection = job.reflections.slice(-3).join("\n\n---\n\n");

  const prompt = `You are Hellhound running in AUTOPILOT.

Objective:
${job.objective}

Job kind:
${job.kind}

Step ${job.currentStep + 1} of ${job.maxSteps}:
${stepText}

Recent output:
${priorOutput || "None yet."}

Recent self-reflection:
${priorReflection || "None yet."}

Instructions:
- Do not wait for the user.
- Produce the actual work for this step.
- Then include a section titled SELF-REVIEW.
- Then include a section titled MEMORY.
- Then include a section titled NEXT.
- If this is a book chapter, write the chapter, then review continuity and emotional direction.
- If this is code, write concrete files/functions or exact commands.
- If this is research, use tool context and produce useful conclusions.
- If this is a check-in or greeting, say hi and propose a useful move.
- Do not be academic unless the task is academic.`;

  try {
    const result = await runAgent({
      userId: job.userId,
      prompt,
      provider: jobProviders.get(job.id)
    });

    const reply = result.reply || "";
    job.outputs.push(reply);

    const reflectionMatch = reply.match(/SELF-REVIEW\s*:?\s*([\s\S]*?)(MEMORY|NEXT|$)/i);
    const memoryMatch = reply.match(/MEMORY\s*:?\s*([\s\S]*?)(NEXT|$)/i);

    if (reflectionMatch?.[1]) job.reflections.push(reflectionMatch[1].trim().slice(0, 4000));
    if (memoryMatch?.[1]) job.memories.push(memoryMatch[1].trim().slice(0, 2000));

    job.currentStep += 1;
    job.updatedAt = now();

    if (job.currentStep >= job.maxSteps) {
      job.status = "done";
      jobProviders.delete(job.id);
    }

    return job;
  } catch (error) {
    job.status = "failed";
    job.error = error instanceof Error ? error.message : String(error);
    job.updatedAt = now();
    return job;
  }
}

function ensureAutopilotLoop() {
  if (loopStarted) return;
  loopStarted = true;

  setInterval(async () => {
    const running = [...jobs.values()].filter((job) => job.status === "running");
    for (const job of running.slice(0, 2)) {
      await runAutopilotStep(job);
    }
  }, 12000);
}
