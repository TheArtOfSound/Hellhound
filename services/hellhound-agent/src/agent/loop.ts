import { buildDecisionPacket } from "../lolm/controller.js";
import { readMemory, writeMemory } from "../memory/store.js";
import { createReceipt } from "../receipts/receipt.js";
import { searchNews, searchWeb } from "../tools/web.js";
import { runSandbox } from "../tools/sandbox.js";
import { answerWithProvider, ProviderMessage } from "../providers/answer.js";

export type AgentRunInput = {
  userId: string;
  prompt: string;
  provider?: {
    apiKey?: string;
    baseUrl: string;
    model: string;
    temperature?: number;
  };
};

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

  if (decisionPacket.decision.shouldSearch) {
    const isNews = decisionPacket.decision.mode === "news" || /\b(news|article|latest|today)\b/i.test(input.prompt);
    toolResults.search = isNews
      ? await searchNews(input.prompt)
      : await searchWeb(input.prompt);
  }

  if (decisionPacket.decision.shouldRunCode) {
    toolResults.sandbox = await runSandbox({
      kind: "command",
      command: input.prompt,
      timeoutMs: 20000
    }).catch((error) => ({ error: error.message }));
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

  const receipt = createReceipt({
    userId: input.userId,
    action: decisionPacket.decision.nextAction,
    intent: input.prompt,
    toolResults,
    memoryDelta
  });

  return {
    reply,
    decision: decisionPacket.decision,
    toolResults,
    memoryDelta,
    receipt
  };
}
