import Fastify from "fastify";
import cors from "@fastify/cors";
import { z } from "zod";
import { env } from "./env.js";
import { runAgent } from "./agent/loop.js";
import {
  createAutopilotJob,
  getAutopilotJob,
  listAutopilotJobs,
  pauseAutopilotJob,
  resumeAutopilotJob,
  runAutopilotStep
} from "./agent/autopilot.js";
import { searchNews, searchWeb } from "./tools/web.js";
import { readMemory, writeMemory } from "./memory/store.js";

const app = Fastify({ logger: true });
await app.register(cors, { origin: true });

const AgentRunSchema = z.object({
  userId: z.string().default("local-user"),
  prompt: z.string().min(1),
  provider: z.object({
    apiKey: z.string().optional(),
    baseUrl: z.string(),
    model: z.string(),
    temperature: z.number().optional()
  }).optional()
});

const AutopilotStartSchema = z.object({
  userId: z.string().default("local-user"),
  objective: z.string().min(1),
  kind: z.enum(["book", "paper", "code", "research", "project", "conversation", "general"]).optional(),
  maxSteps: z.number().int().min(1).max(80).optional()
});

app.get("/health", async () => ({
  ok: true,
  service: "hellhound-agent",
  time: new Date().toISOString()
}));

app.post("/agent/run", async (request, reply) => {
  const parsed = AgentRunSchema.safeParse(request.body);
  if (!parsed.success) return reply.code(400).send({ error: parsed.error.flatten() });

  const result = await runAgent(parsed.data);
  return result;
});

app.post("/autopilot/start", async (request, reply) => {
  const parsed = AutopilotStartSchema.safeParse(request.body);
  if (!parsed.success) return reply.code(400).send({ error: parsed.error.flatten() });

  const job = createAutopilotJob(parsed.data);
  return { job };
});

app.get("/autopilot/jobs", async (request) => {
  const query = request.query as any;
  return { jobs: listAutopilotJobs(query?.userId) };
});

app.get("/autopilot/jobs/:id", async (request, reply) => {
  const params = request.params as any;
  const job = getAutopilotJob(params.id);
  if (!job) return reply.code(404).send({ error: "Job not found" });
  return { job };
});

app.post("/autopilot/jobs/:id/step", async (request, reply) => {
  const params = request.params as any;
  const job = getAutopilotJob(params.id);
  if (!job) return reply.code(404).send({ error: "Job not found" });

  const updated = await runAutopilotStep(job);
  return { job: updated };
});

app.post("/autopilot/jobs/:id/pause", async (request, reply) => {
  const params = request.params as any;
  const job = pauseAutopilotJob(params.id);
  if (!job) return reply.code(404).send({ error: "Job not found" });
  return { job };
});

app.post("/autopilot/jobs/:id/resume", async (request, reply) => {
  const params = request.params as any;
  const job = resumeAutopilotJob(params.id);
  if (!job) return reply.code(404).send({ error: "Job not found" });
  return { job };
});

app.post("/search/web", async (request, reply) => {
  const body = request.body as any;
  if (!body?.query) return reply.code(400).send({ error: "Missing query" });
  return { results: await searchWeb(body.query) };
});

app.post("/search/news", async (request, reply) => {
  const body = request.body as any;
  if (!body?.query) return reply.code(400).send({ error: "Missing query" });
  return { results: await searchNews(body.query) };
});

app.post("/memory/write", async (request) => {
  return writeMemory(request.body as any);
});

app.get("/memory/:userId", async (request) => {
  const params = request.params as any;
  return { memories: await readMemory(params.userId) };
});

app.listen({ port: env.port, host: "0.0.0.0" });
