import crypto from "node:crypto";

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
    createdAt,
    userId: input.userId,
    action: input.action,
    previousHash,
    intentHash,
    toolHash,
    memoryHash
  });

  return {
    schema: "HELLHOUND-NFET-RECEIPT-V1",
    createdAt,
    userId: input.userId,
    action: input.action,
    previousHash,
    intentHash,
    toolHash,
    memoryHash,
    receiptHash
  };
}
