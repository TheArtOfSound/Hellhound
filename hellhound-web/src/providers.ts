export type ProviderKind = "openai-compatible" | "anthropic" | "gemini";

export type ProviderPreset = {
  id: string;
  name: string;
  kind: ProviderKind;
  baseUrl: string;
  model: string;
  keyPlaceholder: string;
  note: string;
};

export type ChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export type ProviderDetection = {
  provider: ProviderPreset;
  confidence: "high" | "medium" | "low";
  reason: string;
};

export const PROVIDERS: ProviderPreset[] = [
  {
    id: "cerebras",
    name: "Cerebras",
    kind: "openai-compatible",
    baseUrl: "https://api.cerebras.ai/v1/chat/completions",
    model: "llama3.1-8b",
    keyPlaceholder: "csk-...",
    note: "Fast OpenAI-compatible endpoint. Strong fit for Hellhound's Android origin."
  },
  {
    id: "openai",
    name: "OpenAI",
    kind: "openai-compatible",
    baseUrl: "https://api.openai.com/v1/chat/completions",
    model: "gpt-4o-mini",
    keyPlaceholder: "sk-... or sk-proj-...",
    note: "OpenAI-compatible chat through Hellhound's Worker proxy."
  },
  {
    id: "openrouter",
    name: "OpenRouter",
    kind: "openai-compatible",
    baseUrl: "https://openrouter.ai/api/v1/chat/completions",
    model: "openai/gpt-4o-mini",
    keyPlaceholder: "sk-or-...",
    note: "Routes many models behind one OpenAI-compatible API."
  },
  {
    id: "anthropic",
    name: "Anthropic",
    kind: "anthropic",
    baseUrl: "https://api.anthropic.com/v1/messages",
    model: "claude-3-5-haiku-latest",
    keyPlaceholder: "sk-ant-...",
    note: "Claude-style messages through Hellhound's Worker proxy."
  },
  {
    id: "gemini",
    name: "Gemini",
    kind: "gemini",
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/models",
    model: "gemini-1.5-flash",
    keyPlaceholder: "AIza...",
    note: "Google model endpoint through Hellhound's Worker proxy."
  },
  {
    id: "groq",
    name: "Groq",
    kind: "openai-compatible",
    baseUrl: "https://api.groq.com/openai/v1/chat/completions",
    model: "llama-3.1-8b-instant",
    keyPlaceholder: "gsk_...",
    note: "OpenAI-compatible Groq endpoint."
  },
  {
    id: "custom",
    name: "Custom",
    kind: "openai-compatible",
    baseUrl: "https://your-endpoint.example/v1/chat/completions",
    model: "your-model",
    keyPlaceholder: "provider key",
    note: "Any OpenAI-compatible provider or your own proxy endpoint."
  }
];

export const HELLHOUND_SYSTEM_PROMPT = `You are Hellhound, a severe and useful assistant. You are direct, observant, and loyal to the user. You do not pretend to be friendly fluff. You help the user think, build, debug, decide, and act with precision. You can be dark in tone, but you are not malicious. You do not threaten people, encourage harm, or invent capabilities you do not have. If live web context is supplied, use it hard, cite what the context actually says, and admit when the web context is thin.`;

function providerById(id: string) {
  return PROVIDERS.find((provider) => provider.id === id) ?? PROVIDERS[0];
}

export function detectProviderFromKey(rawKey: string): ProviderDetection | null {
  const key = rawKey.trim();
  if (!key) return null;

  if (/^sk-ant-/i.test(key)) {
    return { provider: providerById("anthropic"), confidence: "high", reason: "Anthropic key pattern detected." };
  }
  if (/^sk-or-/i.test(key)) {
    return { provider: providerById("openrouter"), confidence: "high", reason: "OpenRouter key pattern detected." };
  }
  if (/^csk[-_]/i.test(key)) {
    return { provider: providerById("cerebras"), confidence: "high", reason: "Cerebras key pattern detected." };
  }
  if (/^AIza/i.test(key)) {
    return { provider: providerById("gemini"), confidence: "high", reason: "Google/Gemini key pattern detected." };
  }
  if (/^gsk_/i.test(key)) {
    return { provider: providerById("groq"), confidence: "high", reason: "Groq key pattern detected." };
  }
  if (/^sk-proj-/i.test(key) || /^sk-[A-Za-z0-9]/.test(key)) {
    return { provider: providerById("openai"), confidence: "medium", reason: "Generic sk-style key detected; OpenAI is the best first guess." };
  }
  return { provider: providerById("custom"), confidence: "low", reason: "Unknown key pattern. Choose Custom or set the provider manually." };
}

export async function sendHellhoundMessage(options: {
  provider: ProviderPreset;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature: number;
  internet: boolean;
  signal?: AbortSignal;
}) {
  const response = await fetch("/api/chat", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(options),
    signal: options.signal
  });

  const text = await response.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }

  if (!response.ok) {
    const message = data?.error || text || `${response.status} ${response.statusText}`;
    throw new Error(typeof message === "string" ? message : JSON.stringify(message));
  }

  return {
    reply: data?.reply?.trim() || "No text returned.",
    webContext: data?.webContext || ""
  };
}
