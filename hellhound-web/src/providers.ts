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
    keyPlaceholder: "sk-...",
    note: "OpenAI-compatible chat completions. Browser CORS may require a proxy."
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
    note: "Claude-style message API. Browser CORS may require a proxy."
  },
  {
    id: "gemini",
    name: "Gemini",
    kind: "gemini",
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/models",
    model: "gemini-1.5-flash",
    keyPlaceholder: "AIza...",
    note: "Google model endpoint. Uses key query parameter."
  },
  {
    id: "custom",
    name: "Custom",
    kind: "openai-compatible",
    baseUrl: "https://your-endpoint.example/v1/chat/completions",
    model: "your-model",
    keyPlaceholder: "provider key",
    note: "Any OpenAI-compatible provider or local proxy."
  }
];

export const HELLHOUND_SYSTEM_PROMPT = `You are Hellhound, a severe and useful assistant. You are direct, observant, and loyal to the user. You do not pretend to be friendly fluff. You help the user think, build, debug, decide, and act with precision. You can be dark in tone, but you are not malicious. You do not threaten people, encourage harm, or invent capabilities you do not have.`;

function headersFor(provider: ProviderPreset, apiKey: string) {
  if (provider.kind === "anthropic") {
    return {
      "content-type": "application/json",
      "x-api-key": apiKey,
      "anthropic-version": "2023-06-01",
      "anthropic-dangerous-direct-browser-access": "true"
    };
  }

  if (provider.kind === "gemini") {
    return { "content-type": "application/json" };
  }

  return {
    "content-type": "application/json",
    "authorization": `Bearer ${apiKey}`
  };
}

function payloadFor(provider: ProviderPreset, model: string, messages: ChatMessage[], temperature: number) {
  if (provider.kind === "anthropic") {
    const system = messages.find((message) => message.role === "system")?.content ?? HELLHOUND_SYSTEM_PROMPT;
    const nonSystem = messages.filter((message) => message.role !== "system");
    return {
      model,
      max_tokens: 1200,
      temperature,
      system,
      messages: nonSystem.map((message) => ({ role: message.role, content: message.content }))
    };
  }

  if (provider.kind === "gemini") {
    return {
      generationConfig: { temperature },
      contents: messages
        .filter((message) => message.role !== "system")
        .map((message) => ({
          role: message.role === "assistant" ? "model" : "user",
          parts: [{ text: message.content }]
        })),
      systemInstruction: { parts: [{ text: HELLHOUND_SYSTEM_PROMPT }] }
    };
  }

  return {
    model,
    temperature,
    messages
  };
}

function endpointFor(provider: ProviderPreset, apiKey: string, model: string) {
  if (provider.kind === "gemini") {
    const root = provider.baseUrl.replace(/\/$/, "");
    return `${root}/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;
  }
  return provider.baseUrl;
}

function parseResponse(provider: ProviderPreset, data: any): string {
  if (provider.kind === "anthropic") {
    return data?.content?.map((part: any) => part?.text ?? "").join("").trim() || "No text returned.";
  }

  if (provider.kind === "gemini") {
    return data?.candidates?.[0]?.content?.parts?.map((part: any) => part?.text ?? "").join("").trim() || "No text returned.";
  }

  return data?.choices?.[0]?.message?.content?.trim() || "No text returned.";
}

export async function sendProviderMessage(options: {
  provider: ProviderPreset;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature: number;
  signal?: AbortSignal;
}) {
  const endpoint = endpointFor(options.provider, options.apiKey, options.model);
  const response = await fetch(endpoint, {
    method: "POST",
    headers: headersFor(options.provider, options.apiKey),
    body: JSON.stringify(payloadFor(options.provider, options.model, options.messages, options.temperature)),
    signal: options.signal
  });

  const text = await response.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }

  if (!response.ok) {
    const message = data?.error?.message || data?.error || text || `${response.status} ${response.statusText}`;
    throw new Error(typeof message === "string" ? message : JSON.stringify(message));
  }

  return parseResponse(options.provider, data);
}
