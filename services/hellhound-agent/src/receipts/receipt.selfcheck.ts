// Self-check for HELLHOUND-NFET-RECEIPT-V2 (no test runner in this repo).
// Run: npx tsx src/receipts/receipt.selfcheck.ts   (exits non-zero on failure)
import {
  verifyToolOutcome, buildToolCalls, deriveOutcome,
  createReceiptV2, verifyReceipt, sanitizeIntent
} from "./receipt.js";

let failures = 0;
function ok(cond: boolean, msg: string) {
  if (!cond) { console.error("FAIL:", msg); failures++; }
  else console.log("ok  :", msg);
}

// ── action outcome verification ─────────────────────────────────────────────
ok(verifyToolOutcome("t", { error: "boom" }).status === "failed", "error result -> failed");
ok(verifyToolOutcome("t", [1, 2]).status === "success", "non-empty array -> success");
ok(verifyToolOutcome("t", []).status === "partial", "empty array -> partial");
ok(verifyToolOutcome("t", { exitCode: 0 }).status === "success", "exit 0 -> success");
ok(verifyToolOutcome("t", { exitCode: 1 }).status === "failed", "exit 1 -> failed");
ok(verifyToolOutcome("t", null).status === "failed", "null result -> failed");

// ── tool calls carry hashes + observation ───────────────────────────────────
const calls = buildToolCalls([
  { name: "web.search", args: { q: "x" }, result: [{ title: "a" }], durationMs: 12.4 }
]);
ok(calls[0].argsHash.length === 64 && calls[0].resultHash.length === 64, "tool call hashes present");
ok(calls[0].status === "success" && calls[0].observation.includes("item"), "tool call status observed");
ok(calls[0].durationMs === 12, "duration rounded");

// ── outcome derivation ──────────────────────────────────────────────────────
ok(deriveOutcome(calls, "") === "failed", "empty answer -> failed");
ok(deriveOutcome(calls, "answer") === "success", "good tools + answer -> success");
ok(deriveOutcome(buildToolCalls([{ name: "x", args: {}, result: { error: "e" }, durationMs: 1 }]), "answer") === "partial",
   "failed tool + answer -> partial");
ok(deriveOutcome(calls, "answer", "budget_limit") === "budget_limit", "override honored");

// ── secret-free intent summary ──────────────────────────────────────────────
ok(sanitizeIntent("use key sk-ABCDEF1234567890ABCDEF now").includes("[redacted]"), "secret redacted in summary");

// ── receipt seal + verify + tamper detection ────────────────────────────────
const r = createReceiptV2({
  runId: "run_1", userId: "u1", surface: "backend",
  provider: { vendor: "openrouter", model: "openai/gpt-4o-mini", temperature: 0.45 },
  intent: "what is the deploy rollback command?",
  decisionPacket: { mode: "code", confidence: 0.82, toolNeed: 0.88, memoryNeed: 0.78, receiptNeed: 0.9 },
  controlEvents: ["retrieve", "finish"],
  decisionSources: ["heuristic", "tool_result"],
  toolCalls: calls,
  outcome: "success"
});
ok(r.schema === "HELLHOUND-NFET-RECEIPT-V2", "schema v2");
ok(r.receiptHash.length === 64 && verifyReceipt(r), "receipt seals and verifies");
ok(r.intentSummary.length > 0 && r.provider.model === "openai/gpt-4o-mini", "provider + intent summary present");

const tampered = { ...r, outcome: "failed" as const };
ok(!verifyReceipt(tampered), "tampered outcome detected (hash mismatch)");

const chained = createReceiptV2({
  previousHash: r.receiptHash, runId: "run_2", userId: "u1", surface: "web",
  provider: { vendor: "openrouter", model: "m", temperature: null },
  intent: "next", decisionPacket: r.decisionPacket, controlEvents: ["finish"],
  decisionSources: ["heuristic"], toolCalls: [], outcome: "success"
});
ok(chained.previousHash === r.receiptHash, "hash chain links to previous receipt");

if (failures) { console.error(`\n${failures} check(s) FAILED`); process.exit(1); }
console.log("\nALL RECEIPT-V2 CHECKS PASSED");
