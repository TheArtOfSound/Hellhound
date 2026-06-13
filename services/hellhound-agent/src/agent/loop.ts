import { nanoid } from "nanoid";
import { buildDecisionPacket } from "../lolm/controller.js";
import { readMemory, writeMemory } from "../memory/store.js";
import {
  createReceipt,
  createReceiptV2,
  buildToolCalls,
  deriveOutcome,
  type ControlEvent,
  type DecisionSource,
  type Surface
} from "../receipts/receipt.js";
import { searchNews, searchWeb } from "../tools/web.js";
import { runSandbox } from "../tools/sandbox.js";
import { answerWithProvider, ProviderMessage } from "../providers/answer.js";

export type AgentRunInput = {
  userId: string;
  prompt: string;
  surface?: Surface;
  previousHash?: string;
  provider?: {
    apiKey?: string;
    baseUrl: string;
    model: string;
    temperature?: number;
  };
};

function providerVendor(baseUrl: string): string {
  const u = baseUrl.toLowerCase();
  if (u.includes("openrouter")) return "openrouter";
  if (u.includes("anthropic")) return "anthropic";
  if (u.includes("groq")) return "groq";
  if (u.includes("openai")) return "openai";
  return "custom";
}

function hellhoundSystem() {
  return `You are Hellhound.

You are not a polite generic assistant.
You are direct, strategic, and operational.
You do not use fake academic formatting unless asked.
You use tools and memory when they are available.
When web/tool context exists, you cite it plainly by title and URL.
When tool context is thin, you say so.
You should produce next actions, not decorative paragraphs.`;
}

export async function runAgent(input: AgentRunInput) {
  const decisionPacket = buildDecisionPacket(input.prompt);
  const memory = await readMemory(input.userId, 24);

  const toolResults: Record<string, unknown> = {};
  const toolCallLog: Array<{ name: string; args: unknown; result: unknown; durationMs: number }> = [];
  const controlEvents: ControlEvent[] = [];
  // The controller is regex/heuristic mode-routing; state that honestly rather
  // than implying a trained uncertainty head decided.
  const decisionSources: DecisionSource[] = ["heuristic"];

  if (decisionPacket.decision.shouldSearch) {
    const isNews = decisionPacket.decision.mode === "news" || /\b(news|article|latest|today)\b/i.test(input.prompt);
    const t0 = Date.now();
    const result = isNews ? await searchNews(input.prompt) : await searchWeb(input.prompt);
    toolResults.search = result;
    toolCallLog.push({ name: isNews ? "web.news" : "web.search", args: { query: input.prompt }, result, durationMs: Date.now() - t0 });
    controlEvents.push("retrieve");
    decisionSources.push("tool_result");
  }

  if (decisionPacket.decision.shouldRunCode) {
    const t0 = Date.now();
    const result = await runSandbox({
      kind: "command",
      command: input.prompt,
      timeoutMs: 20000
    }).catch((error) => ({ error: error.message }));
    toolResults.sandbox = result;
    toolCallLog.push({ name: "sandbox.run", args: { command: input.prompt }, result, durationMs: Date.now() - t0 });
    controlEvents.push("verify");
    decisionSources.push("tool_result");
  }

  const provider = input.provider || {
    baseUrl: "https://openrouter.ai/api/v1/chat/completions",
    model: "openai/gpt-4o-mini",
    temperature: 0.45
  };

  const messages: ProviderMessage[] = [
    { role: "system", content: hellhoundSystem() },
    { role: "system", content: `Decision packet:\n${JSON.stringify(decisionPacket, null, 2)}` },
    { role: "system", content: `Memory:\n${JSON.stringify(memory, null, 2)}` },
    { role: "system", content: `Tool results:\n${JSON.stringify(toolResults, null, 2)}` },
    { role: "user", content: input.prompt }
  ];

  const reply = await answerWithProvider({
    apiKey: provider.apiKey,
    baseUrl: provider.baseUrl,
    model: provider.model,
    temperature: provider.temperature,
    messages
  });

  const memoryDelta = await writeMemory({
    userId: input.userId,
    kind: "conversation",
    content: `User asked: ${input.prompt}\nHellhound answered: ${reply.slice(0, 1000)}`,
    importance: 0.55
  });

  controlEvents.push("finish");

  // v1 receipt retained for back-compat.
  const receipt = createReceipt({
    userId: input.userId,
    action: decisionPacket.decision.nextAction,
    intent: input.prompt,
    toolResults,
    memoryDelta
  });

  // v2 receipt: decision packet + every tool call WITH its verified outcome +
  // an honest run outcome. An audit trail for behavior, not a label on text.
  const toolCalls = buildToolCalls(toolCallLog);
  const outcome = deriveOutcome(toolCalls, reply);
  const latent = decisionPacket.latent;
  const receiptV2 = createReceiptV2({
    previousHash: input.previousHash,
    runId: nanoid(),
    userId: input.userId,
    surface: input.surface ?? "backend",
    provider: { vendor: providerVendor(provider.baseUrl), model: provider.model, temperature: provider.temperature ?? null },
    intent: input.prompt,
    decisionPacket: {
      mode: decisionPacket.decision.mode,
      confidence: decisionPacket.decision.confidence,
      toolNeed: latent.toolNeed,
      memoryNeed: latent.memoryNeed,
      receiptNeed: latent.receiptNeed
    },
    controlEvents,
    decisionSources,
    toolCalls,
    evidenceIds: Object.keys(toolResults).map((k) => `tool:${k}`),
    safety: {
      blockedActions: [],
      confirmationRequired: decisionPacket.decision.mode === "warning",
      confirmationResult: "n/a"
    },
    outcome
  });

  return {
    reply,
    decision: decisionPacket.decision,
    toolResults,
    memoryDelta,
    receipt,
    receiptV2
  };
}
