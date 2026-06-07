import { HELLHOUND_SYSTEM_PROMPT, ProviderPreset, ChatMessage } from "./providers";

type Env = {
  ASSETS: Fetcher;
};

type RequestBody = {
  provider: ProviderPreset;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature: number;
  internet: boolean;
};

const jsonHeaders = {
  "content-type": "application/json; charset=utf-8",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,OPTIONS",
  "access-control-allow-headers": "content-type"
};

function json(data: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(data), {
    ...init,
    headers: { ...jsonHeaders, ...(init.headers || {}) }
  });
}

function providerHeaders(provider: ProviderPreset, apiKey: string): Record<string, string> {
  const headers: Record<string, string> = { "content-type": "application/json" };
  if (provider.kind === "anthropic") {
    headers["x-api-key"] = apiKey;
    headers["anthropic-version"] = "2023-06-01";
    return headers;
  }
  if (provider.kind === "gemini") return headers;
  headers.authorization = `Bearer ${apiKey}`;
  return headers;
}

function endpointFor(provider: ProviderPreset, apiKey: string, model: string) {
  if (provider.kind === "gemini") {
    const root = provider.baseUrl.replace(/\/$/, "");
    return `${root}/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;
  }
  return provider.baseUrl;
}

function providerPayload(provider: ProviderPreset, model: string, messages: ChatMessage[], temperature: number) {
  if (provider.kind === "anthropic") {
    const system = messages.find((message) => message.role === "system")?.content ?? HELLHOUND_SYSTEM_PROMPT;
    const nonSystem = messages.filter((message) => message.role !== "system");
    return { model, max_tokens: 1400, temperature, system, messages: nonSystem };
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

  return { model, temperature, messages };
}

function parseProviderText(provider: ProviderPreset, data: any): string {
  if (provider.kind === "anthropic") {
    return data?.content?.map((part: any) => part?.text ?? "").join("").trim() || "No text returned.";
  }
  if (provider.kind === "gemini") {
    return data?.candidates?.[0]?.content?.parts?.map((part: any) => part?.text ?? "").join("").trim() || "No text returned.";
  }
  return data?.choices?.[0]?.message?.content?.trim() || "No text returned.";
}

function latestUserMessage(messages: ChatMessage[]) {
  return [...messages].reverse().find((message) => message.role === "user")?.content ?? "";
}

async function getWebContext(query: string) {
  const clean = query.replace(/\s+/g, " ").trim().slice(0, 260);
  if (!clean) return "";

  const parts: string[] = [];

  const ddgUrl = `https://api.duckduckgo.com/?q=${encodeURIComponent(clean)}&format=json&no_html=1&skip_disambig=1`;
  const ddg = await fetch(ddgUrl)
    .then((response) => response.ok ? response.json() : null)
    .catch(() => null);

  if (ddg?.AbstractText) parts.push(`DuckDuckGo summary: ${ddg.AbstractText}`);
  if (ddg?.Answer) parts.push(`DuckDuckGo direct answer: ${ddg.Answer}`);
  if (Array.isArray(ddg?.RelatedTopics)) {
    const related = ddg.RelatedTopics
      .flatMap((item: any) => item?.Topics || [item])
      .map((item: any) => item?.Text)
      .filter(Boolean)
      .slice(0, 7);
    if (related.length) parts.push(`Related web context: ${related.join(" | ")}`);
  }

  const wikiTopic = clean.split(/[?.!,;:]/)[0].slice(0, 80);
  const wikiUrl = `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(wikiTopic)}`;
  const wiki = await fetch(wikiUrl)
    .then((response) => response.ok ? response.json() : null)
    .catch(() => null);

  if (wiki?.extract) parts.push(`Wikipedia summary: ${wiki.extract}`);

  return parts.join("\n\n").slice(0, 4200);
}

async function handleChat(request: Request) {
  const body = await request.json<RequestBody>();
  if (!body.apiKey?.trim()) return json({ error: "Missing provider API key." }, { status: 400 });
  if (!body.provider?.baseUrl || !body.provider?.kind) return json({ error: "Missing provider configuration." }, { status: 400 });

  let webContext = "";
  const messages = [...body.messages];

  if (body.internet) {
    webContext = await getWebContext(latestUserMessage(messages));
    messages.unshift({
      role: "system",
      content: webContext
        ? `Live web context for the latest user request:\n${webContext}\n\nUse this context when relevant. It is not exhaustive.`
        : "Live web context was requested, but no useful web summary was found. Say that plainly if recency matters."
    });
  }

  const providerResponse = await fetch(endpointFor(body.provider, body.apiKey, body.model), {
    method: "POST",
    headers: providerHeaders(body.provider, body.apiKey),
    body: JSON.stringify(providerPayload(body.provider, body.model, messages, body.temperature))
  });

  const text = await providerResponse.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }

  if (!providerResponse.ok) {
    const message = data?.error?.message || data?.error || text || `${providerResponse.status} ${providerResponse.statusText}`;
    return json({ error: typeof message === "string" ? message : JSON.stringify(message) }, { status: providerResponse.status });
  }

  return json({ reply: parseProviderText(body.provider, data), webContext });
}

export default {
  async fetch(request: Request, env: Env) {
    if (request.method === "OPTIONS") return new Response(null, { headers: jsonHeaders });
    const url = new URL(request.url);
    if (url.pathname === "/api/chat" && request.method === "POST") return handleChat(request);
    return env.ASSETS.fetch(request);
  }
};
