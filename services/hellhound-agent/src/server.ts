import Fastify from "fastify";
import cors from "@fastify/cors";
import { z } from "zod";
import { env } from "./env.js";
import { runAgent } from "./agent/loop.js";
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
