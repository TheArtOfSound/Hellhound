import { env } from "../env.js";

export type ProviderMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export async function answerWithProvider(input: {
  apiKey?: string;
  baseUrl: string;
  model: string;
  messages: ProviderMessage[];
  temperature?: number;
}) {
  const apiKey = input.apiKey || env.answerKey;

  if (!apiKey) {
    throw new Error("Missing answer provider key.");
  }

  const response = await fetch(input.baseUrl, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "authorization": `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      model: input.model,
      messages: input.messages,
      temperature: input.temperature ?? 0.45
    })
  });

  const text = await response.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }

  if (!response.ok) {
    throw new Error(data?.error?.message || data?.error || text || `Provider failed: ${response.status}`);
  }

  return data?.choices?.[0]?.message?.content || "No answer returned.";
}
