import { useEffect, useMemo, useRef, useState } from "react";
import {
  ChatMessage,
  HELLHOUND_SYSTEM_PROMPT,
  PROVIDERS,
  ProviderPreset,
  detectProviderFromKey,
  sendHellhoundMessage
} from "./providers";
import {
  AgentProject,
  addGoal,
  addProject,
  buildAgentContext,
  craftLocalNudge,
  loadAgentState,
  markNudged,
  rememberLine,
  saveAgentState,
  shouldNudge
} from "./localAgent";

type SavedSettings = {
  providerId: string;
  baseUrl: string;
  model: string;
  temperature: number;
  internet: boolean;
};

type UiMessage = ChatMessage & {
  id: string;
  pending?: boolean;
};

const STORAGE_KEY = "hellhound-web-settings-v2";

const starters = [
  "Search the web, then give me the ruthless truth about this idea.",
  "Find a cool current tech or AI article and explain why it matters.",
  "Audit this plan like failure is hunting it.",
  "Turn this messy thing into a clean execution path."
];

function loadSettings(): SavedSettings {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}");
    return {
      providerId: parsed.providerId || "cerebras",
      baseUrl: parsed.baseUrl || "",
      model: parsed.model || "",
      temperature: Number.isFinite(parsed.temperature) ? parsed.temperature : 0.45,
      internet: typeof parsed.internet === "boolean" ? parsed.internet : true
    };
  } catch {
    return { providerId: "cerebras", baseUrl: "", model: "", temperature: 0.45, internet: true };
  }
}

function uid() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export default function App() {
  const [settings, setSettings] = useState(loadSettings);
  const [agentState, setAgentState] = useState(loadAgentState);
  const [scratch, setScratch] = useState("");
  const [projectKind, setProjectKind] = useState<AgentProject["kind"]>("project");

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
  const [keyDetection, setKeyDetection] = useState("");
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<UiMessage[]>([
    {
      id: uid(),
      role: "assistant",
      content: "Hellhound is online. Local memory is awake. Internet mode is armed. Bring a key and ask something worth hunting."
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
    saveAgentState(agentState);
  }, [agentState]);

  useEffect(() => {
    const tick = window.setInterval(() => {
      setAgentState((current) => {
        if (!shouldNudge(current)) return current;

        const nudge = craftLocalNudge(current);
        setMessages((existing) => [
          ...existing,
          { id: uid(), role: "assistant", content: nudge }
        ]);

        return markNudged(current);
      });
    }, 30000);

    return () => window.clearInterval(tick);
  }, []);

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

  function updateApiKey(value: string) {
    setApiKey(value);
    const detected = detectProviderFromKey(value);

    if (!detected) {
      setKeyDetection("");
      return;
    }

    setKeyDetection(`${detected.confidence.toUpperCase()}: ${detected.provider.name} — ${detected.reason}`);

    if (detected.confidence !== "low") {
      setSettings((current) => ({
        ...current,
        providerId: detected.provider.id,
        baseUrl: detected.provider.baseUrl,
        model: detected.provider.model
      }));
    }
  }

  function addScratch(kind: "memory" | "goal" | "project") {
    if (!scratch.trim()) return;

    setAgentState((current) => {
      if (kind === "memory") return rememberLine(current, scratch);
      if (kind === "goal") return addGoal(current, scratch);
      return addProject(current, scratch, projectKind);
    });

    setMessages((existing) => [
      ...existing,
      { id: uid(), role: "assistant", content: `Logged ${kind}: ${scratch.trim()}` }
    ]);

    setScratch("");
  }

  async function send(text = input) {
    const trimmed = text.trim();
    if (!trimmed || busy) return;

    setError("");

    if (!apiKey.trim()) {
      setError("Add a provider API key first. Hellhound can detect common providers from the key pattern.");
      return;
    }

    const userMessage: UiMessage = { id: uid(), role: "user", content: trimmed };
    const pending: UiMessage = { id: uid(), role: "assistant", content: "HUNTING", pending: true };
    const nextMessages = [...messages.filter((message) => !message.pending), userMessage];

    setMessages([...nextMessages, pending]);
    setInput("");
    setBusy(true);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const providerMessages: ChatMessage[] = [
        { role: "system", content: HELLHOUND_SYSTEM_PROMPT },
        { role: "system", content: buildAgentContext(agentState) },
        ...nextMessages
          .filter((message) => message.role === "user" || message.role === "assistant")
          .slice(-18)
          .map(({ role, content }) => ({ role, content }))
      ];

      const result = await sendHellhoundMessage({
        provider,
        apiKey: apiKey.trim(),
        model: provider.model,
        messages: providerMessages,
        temperature: settings.temperature,
        internet: settings.internet,
        signal: controller.signal
      });

      if (trimmed.length > 18) {
        setAgentState((current) => rememberLine(current, `User asked: ${trimmed.slice(0, 180)}`));
      }

      setMessages([...nextMessages, { id: uid(), role: "assistant", content: result.reply }]);
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
        content: "Chat cleared. Hellhound remains awake."
      }
    ]);
    setError("");
  }

  return (
    <main>
      <aside className="sidebar">
        <div className="brand-block">
          <div className="mark">HH</div>
          <h1>Hellhound</h1>
          <p>A different AI interface. Darker. Sharper. Web-aware. Locally self-starting.</p>
        </div>

        <section className="compact-grid">
          <div>
            <label>Provider</label>
            <select value={selectedProvider.id} onChange={(event) => chooseProvider(event.target.value)}>
              {PROVIDERS.map((item) => (
                <option key={item.id} value={item.id}>{item.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label>Internet</label>
            <button
              className={settings.internet ? "armed" : ""}
              onClick={() => patchSettings({ internet: !settings.internet })}
            >
              {settings.internet ? "Armed" : "Off"}
            </button>
          </div>
        </section>

        <section>
          <label>API key</label>
          <input
            value={apiKey}
            onChange={(event) => updateApiKey(event.target.value)}
            placeholder={selectedProvider.keyPlaceholder}
            type="password"
            autoComplete="off"
          />
          <small>{keyDetection || "Paste a key and Hellhound will guess the provider when possible."}</small>
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

        <section className="details">
          <label>Local agent</label>
          <button
            className={agentState.enabled ? "armed" : ""}
            onClick={() => setAgentState((s) => ({ ...s, enabled: !s.enabled }))}
          >
            {agentState.enabled ? "Autonomy on" : "Autonomy off"}
          </button>

          <input
            value={scratch}
            onChange={(event) => setScratch(event.target.value)}
            placeholder="Memory, goal, project, paper, code idea..."
          />

          <select
            value={projectKind}
            onChange={(event) => setProjectKind(event.target.value as AgentProject["kind"])}
          >
            <option value="project">project</option>
            <option value="paper">paper</option>
            <option value="code">code</option>
            <option value="research">research</option>
            <option value="life">life</option>
          </select>

          <div className="header-actions">
            <button onClick={() => addScratch("memory")}>Remember</button>
            <button onClick={() => addScratch("goal")}>Goal</button>
            <button onClick={() => addScratch("project")}>Project</button>
          </div>

          <small>{agentState.projects.length} projects · {agentState.goals.length} goals · {agentState.memory.length} memories</small>
        </section>
      </aside>

      <section className="chat-shell">
        <header className="chat-header">
          <div>
            <span className="overline">
              {settings.internet ? "Internet mode armed" : "Local model call only"} · {agentState.enabled ? "autonomy awake" : "autonomy off"}
            </span>
            <h2>{provider.name} / {provider.model}</h2>
          </div>

          <div className="header-actions">
            <button onClick={() => setMessages((existing) => [...existing, { id: uid(), role: "assistant", content: craftLocalNudge(agentState) }])}>Nudge</button>
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
            placeholder="Ask Hellhound. If Internet is armed, it will pull live context before answering."
            onKeyDown={(event) => {
              if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
                event.preventDefault();
                void send();
              }
            }}
          />
          <div className="send-row">
            <span>Ctrl/⌘ + Enter · local memory active · Worker tools armed</span>
            <button className="send" onClick={() => send()} disabled={!input.trim() || busy}>Send</button>
          </div>
        </footer>
      </section>
    </main>
  );
}
