import { useEffect, useMemo, useRef, useState } from "react";
import {
  ChatMessage,
  HELLHOUND_SYSTEM_PROMPT,
  PROVIDERS,
  ProviderPreset,
  sendProviderMessage
} from "./providers";

type SavedSettings = {
  providerId: string;
  baseUrl: string;
  model: string;
  temperature: number;
};

type UiMessage = ChatMessage & {
  id: string;
  pending?: boolean;
};

const STORAGE_KEY = "hellhound-web-settings-v1";

const starters = [
  "Audit this plan like it is going to fail unless we fix it.",
  "Turn this messy idea into a build plan with priorities.",
  "Give me the blunt version, then the useful version.",
  "Find the weak point in this argument."
];

function loadSettings(): SavedSettings {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    return {
      providerId: parsed.providerId || "cerebras",
      baseUrl: parsed.baseUrl || "",
      model: parsed.model || "",
      temperature: Number.isFinite(parsed.temperature) ? parsed.temperature : 0.45
    };
  } catch {
    return { providerId: "cerebras", baseUrl: "", model: "", temperature: 0.45 };
  }
}

function uid() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export default function App() {
  const [settings, setSettings] = useState(loadSettings);
  const selectedProvider = useMemo(
    () => PROVIDERS.find((provider) => provider.id === settings.providerId) ?? PROVIDERS[0],
    [settings.providerId]
  );
  const provider: ProviderPreset = useMemo(
    () => ({
      ...selectedProvider,
      baseUrl: settings.baseUrl.trim() || selectedProvider.baseUrl,
      model: settings.model.trim() || selectedProvider.model
    }),
    [selectedProvider, settings.baseUrl, settings.model]
  );

  const [apiKey, setApiKey] = useState("");
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<UiMessage[]>([
    {
      id: uid(),
      role: "assistant",
      content: "Hellhound is awake. Bring a key, choose a provider, and ask something worth answering."
    }
  ]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  }, [settings]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length, busy]);

  function patchSettings(patch: Partial<SavedSettings>) {
    setSettings((current) => ({ ...current, ...patch }));
  }

  function chooseProvider(id: string) {
    const next = PROVIDERS.find((item) => item.id === id) ?? PROVIDERS[0];
    setSettings((current) => ({
      ...current,
      providerId: next.id,
      baseUrl: next.baseUrl,
      model: next.model
    }));
  }

  async function send(text = input) {
    const trimmed = text.trim();
    if (!trimmed || busy) return;
    setError("");

    if (!apiKey.trim()) {
      setError("Add a provider API key first. Hellhound does not ship with a shared key.");
      return;
    }

    const userMessage: UiMessage = { id: uid(), role: "user", content: trimmed };
    const pending: UiMessage = { id: uid(), role: "assistant", content: "...", pending: true };
    const nextMessages = [...messages.filter((message) => !message.pending), userMessage];
    setMessages([...nextMessages, pending]);
    setInput("");
    setBusy(true);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const providerMessages: ChatMessage[] = [
        { role: "system", content: HELLHOUND_SYSTEM_PROMPT },
        ...nextMessages
          .filter((message) => message.role === "user" || message.role === "assistant")
          .slice(-18)
          .map(({ role, content }) => ({ role, content }))
      ];

      const reply = await sendProviderMessage({
        provider,
        apiKey: apiKey.trim(),
        model: provider.model,
        messages: providerMessages,
        temperature: settings.temperature,
        signal: controller.signal
      });

      setMessages([...nextMessages, { id: uid(), role: "assistant", content: reply }]);
    } catch (err) {
      if ((err as Error).name === "AbortError") {
        setMessages(nextMessages);
        setError("Stopped.");
      } else {
        setMessages(nextMessages);
        setError(err instanceof Error ? err.message : String(err));
      }
    } finally {
      setBusy(false);
      abortRef.current = null;
    }
  }

  function stop() {
    abortRef.current?.abort();
  }

  function clearChat() {
    setMessages([
      {
        id: uid(),
        role: "assistant",
        content: "Chat cleared. Hellhound is still here."
      }
    ]);
    setError("");
  }

  return (
    <main>
      <aside className="sidebar">
        <div>
          <div className="mark">HH</div>
          <h1>Hellhound</h1>
          <p>Not another polite assistant. A darker interface for bringing your own model key.</p>
        </div>

        <section>
          <label>Provider</label>
          <select value={selectedProvider.id} onChange={(event) => chooseProvider(event.target.value)}>
            {PROVIDERS.map((item) => (
              <option key={item.id} value={item.id}>{item.name}</option>
            ))}
          </select>
        </section>

        <section>
          <label>API key</label>
          <input
            value={apiKey}
            onChange={(event) => setApiKey(event.target.value)}
            placeholder={selectedProvider.keyPlaceholder}
            type="password"
            autoComplete="off"
          />
          <small>Your key stays in this browser session unless your provider receives it for the request.</small>
        </section>

        <section className="details">
          <label>Model</label>
          <input value={provider.model} onChange={(event) => patchSettings({ model: event.target.value })} />
          <label>Endpoint</label>
          <input value={provider.baseUrl} onChange={(event) => patchSettings({ baseUrl: event.target.value })} />
          <label>Temperature {settings.temperature.toFixed(2)}</label>
          <input
            type="range"
            min="0"
            max="1.2"
            step="0.05"
            value={settings.temperature}
            onChange={(event) => patchSettings({ temperature: Number(event.target.value) })}
          />
          <small>{selectedProvider.note}</small>
        </section>

        <section className="warning">
          Direct browser calls depend on provider CORS rules. If a provider blocks browser requests, use a small proxy endpoint with the same OpenAI-compatible shape.
        </section>
      </aside>

      <section className="chat-shell">
        <header className="chat-header">
          <div>
            <span className="overline">Hellhound web interface</span>
            <h2>{provider.name} / {provider.model}</h2>
          </div>
          <div className="header-actions">
            <button onClick={clearChat}>Clear</button>
            {busy ? <button className="danger" onClick={stop}>Stop</button> : null}
          </div>
        </header>

        <div className="starters">
          {starters.map((starter) => (
            <button key={starter} onClick={() => send(starter)} disabled={busy}>{starter}</button>
          ))}
        </div>

        <div className="messages" aria-live="polite">
          {messages.map((message) => (
            <article key={message.id} className={`message ${message.role} ${message.pending ? "pending" : ""}`}>
              <div className="role">{message.role === "assistant" ? "Hellhound" : "You"}</div>
              <div className="content">{message.content}</div>
            </article>
          ))}
          <div ref={bottomRef} />
        </div>

        {error ? <div className="error">{error}</div> : null}

        <footer className="composer">
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Ask Hellhound. Be specific."
            onKeyDown={(event) => {
              if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                event.preventDefault();
                void send();
              }
            }}
          />
          <div className="send-row">
            <span>Ctrl/⌘ + Enter to send</span>
            <button className="send" onClick={() => send()} disabled={!input.trim() || busy}>Send</button>
          </div>
        </footer>
      </section>
    </main>
  );
}
