export type AgentMode =
  | "chat"
  | "research"
  | "news"
  | "code"
  | "planning"
  | "memory"
  | "paper"
  | "warning";

export type AgentDecision = {
  mode: AgentMode;
  shouldSearch: boolean;
  shouldReadUrl: boolean;
  shouldRunCode: boolean;
  shouldWriteMemory: boolean;
  shouldCreateReceipt: boolean;
  shouldScheduleNudge: boolean;
  answerStyle: "hellhound" | "technical" | "brief" | "paper";
  nextAction: string;
  confidence: number;
};

const modePatterns: Array<[AgentMode, RegExp]> = [
  ["research", /\b(research|find|look up|sources|current|latest|web)\b/i],
  ["news", /\b(news|article|today|happened|breaking|cool story)\b/i],
  ["code", /\b(code|npm|repo|build|error|terminal|install|script|github)\b/i],
  ["planning", /\b(plan|roadmap|strategy|next step|project|goal)\b/i],
  ["paper", /\b(paper|abstract|latex|citation|architecture|math)\b/i],
  ["memory", /\b(remember|save|learn|store|preference)\b/i],
  ["warning", /\b(risk|danger|legal|security|exploit|attack|harm)\b/i]
];

export function decideAgentAction(input: string): AgentDecision {
  const text = input.trim();
  const matched = modePatterns.find(([, rx]) => rx.test(text));
  const mode = matched?.[0] ?? "chat";

  const shouldSearch =
    mode === "research" ||
    mode === "news" ||
    /\b(current|latest|today|web|internet|article|source)\b/i.test(text);

  const shouldReadUrl = /https?:\/\/\S+/i.test(text);
  const shouldRunCode = mode === "code" && /\b(run|test|build|install|npm|execute)\b/i.test(text);

  return {
    mode,
    shouldSearch,
    shouldReadUrl,
    shouldRunCode,
    shouldWriteMemory: true,
    shouldCreateReceipt: true,
    shouldScheduleNudge: mode === "planning" || mode === "code" || mode === "paper",
    answerStyle: mode === "paper" ? "paper" : mode === "code" ? "technical" : "hellhound",
    nextAction: shouldRunCode ? "sandbox.run" : shouldSearch ? "web.search" : "answer",
    confidence: matched ? 0.82 : 0.55
  };
}

export function buildDecisionPacket(input: string) {
  const decision = decideAgentAction(input);
  return {
    input,
    decision,
    latent: {
      surfaceNeed: decision.mode === "chat" ? 0.72 : 0.42,
      toolNeed: decision.shouldSearch || decision.shouldRunCode ? 0.88 : 0.2,
      memoryNeed: decision.shouldWriteMemory ? 0.78 : 0.1,
      receiptNeed: decision.shouldCreateReceipt ? 0.9 : 0.1
    }
  };
}
