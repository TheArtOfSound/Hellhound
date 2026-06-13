import crypto from "node:crypto";

// ── v1 (retained for back-compat) ───────────────────────────────────────────

export type ReceiptInput = {
  previousHash?: string;
  userId: string;
  action: string;
  intent: unknown;
  toolResults: unknown;
  memoryDelta: unknown;
};

export type Receipt = {
  schema: "HELLHOUND-NFET-RECEIPT-V1";
  createdAt: string;
  userId: string;
  action: string;
  previousHash: string;
  intentHash: string;
  toolHash: string;
  memoryHash: string;
  receiptHash: string;
};

function hash(value: unknown) {
  return crypto.createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

export function createReceipt(input: ReceiptInput): Receipt {
  const createdAt = new Date().toISOString();
  const previousHash = input.previousHash || "GENESIS";
  const intentHash = hash(input.intent);
  const toolHash = hash(input.toolResults);
  const memoryHash = hash(input.memoryDelta);

  const receiptHash = hash({
    schema: "HELLHOUND-NFET-RECEIPT-V1",
    createdAt, userId: input.userId, action: input.action,
    previousHash, intentHash, toolHash, memoryHash
  });

  return {
    schema: "HELLHOUND-NFET-RECEIPT-V1",
    createdAt, userId: input.userId, action: input.action,
    previousHash, intentHash, toolHash, memoryHash, receiptHash
  };
}

// ── v2 — the multi-surface audit receipt ────────────────────────────────────
//
// The audit's core correction: a hash chain over intent/tool/memory proves a
// receipt existed, not that the run happened as claimed and not whether the
// actions actually worked. v2 seals the decision, every tool call WITH its
// observed outcome (action outcome verification), evidence ids, answer diff,
// uncertainty, safety boundaries, and a single honest run outcome — so the
// receipt is an audit trail for behavior, not a label on generated text.

export type Surface = "android" | "web" | "backend" | "scheduler" | "sandbox";
export type ControlEvent = "retrieve" | "verify" | "branch" | "continue" | "finish" | "refuse";
export type DecisionSource = "heuristic" | "head" | "policy" | "user" | "tool_result";
export type ToolStatus = "success" | "partial" | "failed";
export type Outcome = "success" | "partial" | "failed" | "budget_limit" | "user_cancelled";

export type ToolCall = {
  name: string;
  argsHash: string;
  resultHash: string;
  status: ToolStatus;     // verified against the observed result, not assumed
  durationMs: number;
  observation: string;    // the observation step that justifies status
};

export type ReceiptV2 = {
  schema: "HELLHOUND-NFET-RECEIPT-V2";
  runId: string;
  userId: string;
  createdAt: string;
  surface: Surface;
  provider: { vendor: string; model: string; temperature: number | null };
  intentHash: string;
  intentSummary: string;
  decisionPacket: {
    mode: string; confidence: number;
    toolNeed: number; memoryNeed: number; receiptNeed: number;
  };
  controlEvents: ControlEvent[];
  decisionSources: DecisionSource[];
  toolCalls: ToolCall[];
  evidenceIds: string[];
  answerDiff: { changedText: boolean | null; baseCompared: boolean; claimChanges: number };
  uncertainty: { lowConfidenceSpans: number; runScore: number | null };
  safety: { blockedActions: string[]; confirmationRequired: boolean; confirmationResult: "n/a" | "granted" | "denied" };
  outcome: Outcome;
  previousHash: string;
  receiptHash: string;
};

/** Short, secret-free intent summary for the receipt (never the raw secret-bearing prompt). */
export function sanitizeIntent(intent: unknown): string {
  const text = typeof intent === "string" ? intent : JSON.stringify(intent ?? "");
  return text
    .replace(/\b(sk-[A-Za-z0-9]+|Bearer\s+\S+|[A-Za-z0-9_-]{32,})\b/g, "[redacted]")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 160);
}

/**
 * Action outcome verification (the audit's P0): map a tool's RESULT to an honest
 * status with an observation, instead of assuming the call worked.
 */
export function verifyToolOutcome(_name: string, result: unknown): { status: ToolStatus; observation: string } {
  if (result == null) return { status: "failed", observation: "no result returned" };
  if (typeof result === "object" && result !== null && "error" in (result as Record<string, unknown>)) {
    return { status: "failed", observation: `error: ${String((result as Record<string, unknown>).error).slice(0, 120)}` };
  }
  if (Array.isArray(result)) {
    return result.length > 0
      ? { status: "success", observation: `${result.length} item(s) returned` }
      : { status: "partial", observation: "empty result set" };
  }
  if (typeof result === "object") {
    const r = result as Record<string, unknown>;
    if ("exitCode" in r) {
      return r.exitCode === 0
        ? { status: "success", observation: "exit code 0" }
        : { status: "failed", observation: `exit code ${String(r.exitCode)}` };
    }
    const keys = Object.keys(r);
    return keys.length > 0
      ? { status: "success", observation: `result with ${keys.length} field(s)` }
      : { status: "partial", observation: "empty object" };
  }
  const s = String(result);
  return s.trim().length > 0
    ? { status: "success", observation: `${s.length} char(s) returned` }
    : { status: "partial", observation: "empty string" };
}

export function buildToolCalls(
  calls: Array<{ name: string; args: unknown; result: unknown; durationMs: number }>
): ToolCall[] {
  return calls.map((c) => {
    const { status, observation } = verifyToolOutcome(c.name, c.result);
    return {
      name: c.name,
      argsHash: hash(c.args),
      resultHash: hash(c.result),
      status,
      durationMs: Math.max(0, Math.round(c.durationMs)),
      observation
    };
  });
}

/** Honest run outcome from verified tool calls + whether an answer was produced. */
export function deriveOutcome(toolCalls: ToolCall[], answer: string, override?: Outcome): Outcome {
  if (override) return override;
  if (!answer || !answer.trim()) return "failed";
  if (toolCalls.some((t) => t.status === "failed")) return "partial";
  if (toolCalls.some((t) => t.status === "partial")) return "partial";
  return "success";
}

export type ReceiptV2Input = {
  previousHash?: string;
  runId: string;
  userId: string;
  surface: Surface;
  provider: { vendor: string; model: string; temperature?: number | null };
  intent: unknown;
  decisionPacket: ReceiptV2["decisionPacket"];
  controlEvents: ControlEvent[];
  decisionSources: DecisionSource[];
  toolCalls: ToolCall[];
  evidenceIds?: string[];
  answerDiff?: Partial<ReceiptV2["answerDiff"]>;
  uncertainty?: Partial<ReceiptV2["uncertainty"]>;
  safety?: Partial<ReceiptV2["safety"]>;
  outcome: Outcome;
  createdAt?: string;
};

export function createReceiptV2(input: ReceiptV2Input): ReceiptV2 {
  const createdAt = input.createdAt ?? new Date().toISOString();
  const previousHash = input.previousHash || "GENESIS";
  const body = {
    schema: "HELLHOUND-NFET-RECEIPT-V2" as const,
    runId: input.runId,
    userId: input.userId,
    createdAt,
    surface: input.surface,
    provider: {
      vendor: input.provider.vendor,
      model: input.provider.model,
      temperature: input.provider.temperature ?? null
    },
    intentHash: hash(input.intent),
    intentSummary: sanitizeIntent(input.intent),
    decisionPacket: input.decisionPacket,
    controlEvents: input.controlEvents,
    decisionSources: input.decisionSources,
    toolCalls: input.toolCalls,
    evidenceIds: input.evidenceIds ?? [],
    answerDiff: { changedText: null, baseCompared: false, claimChanges: 0, ...input.answerDiff },
    uncertainty: { lowConfidenceSpans: 0, runScore: null, ...input.uncertainty },
    safety: {
      blockedActions: [],
      confirmationRequired: false,
      confirmationResult: "n/a" as const,
      ...input.safety
    },
    outcome: input.outcome,
    previousHash
  };
  const receiptHash = hash(body);
  return { ...body, receiptHash };
}

/** Recompute the hash and confirm the receipt was not altered after sealing. */
export function verifyReceipt(receipt: ReceiptV2): boolean {
  const { receiptHash, ...body } = receipt;
  return hash(body) === receiptHash;
}
