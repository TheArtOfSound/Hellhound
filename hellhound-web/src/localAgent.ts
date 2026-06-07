export type AgentProject = {
  id: string;
  title: string;
  kind: "project" | "paper" | "code" | "research" | "life";
  status: "active" | "paused" | "done";
  notes: string;
  createdAt: string;
  updatedAt: string;
};

export type AgentState = {
  enabled: boolean;
  nudgeMinutes: number;
  lastNudgeAt: string;
  memory: string[];
  goals: string[];
  projects: AgentProject[];
};

const KEY = "hellhound-local-agent-v1";

function now() {
  return new Date().toISOString();
}

function id() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

export function defaultAgentState(): AgentState {
  return {
    enabled: true,
    nudgeMinutes: 12,
    lastNudgeAt: "",
    memory: [
      "Hellhound should be direct, self-starting, project-aware, and useful without waiting for perfect instructions."
    ],
    goals: [
      "Watch for useful next steps.",
      "Suggest concrete project moves.",
      "Keep the user from drifting."
    ],
    projects: []
  };
}

export function loadAgentState(): AgentState {
  try {
    const parsed = JSON.parse(localStorage.getItem(KEY) || "null");
    if (!parsed) return defaultAgentState();
    return { ...defaultAgentState(), ...parsed };
  } catch {
    return defaultAgentState();
  }
}

export function saveAgentState(state: AgentState) {
  localStorage.setItem(KEY, JSON.stringify(state));
}

export function rememberLine(state: AgentState, line: string): AgentState {
  const clean = line.trim();
  if (!clean) return state;
  return {
    ...state,
    memory: [clean, ...state.memory.filter((item) => item !== clean)].slice(0, 80)
  };
}

export function addGoal(state: AgentState, goal: string): AgentState {
  const clean = goal.trim();
  if (!clean) return state;
  return {
    ...state,
    goals: [clean, ...state.goals.filter((item) => item !== clean)].slice(0, 40)
  };
}

export function addProject(state: AgentState, title: string, kind: AgentProject["kind"] = "project"): AgentState {
  const clean = title.trim();
  if (!clean) return state;
  const project: AgentProject = {
    id: id(),
    title: clean,
    kind,
    status: "active",
    notes: "Created by Hellhound local agent.",
    createdAt: now(),
    updatedAt: now()
  };
  return { ...state, projects: [project, ...state.projects].slice(0, 40) };
}

export function shouldNudge(state: AgentState) {
  if (!state.enabled) return false;
  if (!state.lastNudgeAt) return true;
  const elapsed = Date.now() - new Date(state.lastNudgeAt).getTime();
  return elapsed > state.nudgeMinutes * 60 * 1000;
}

export function markNudged(state: AgentState): AgentState {
  return { ...state, lastNudgeAt: now() };
}

export function buildAgentContext(state: AgentState) {
  const active = state.projects.filter((project) => project.status === "active").slice(0, 8);
  return [
    "LOCAL HELLHOUND MEMORY",
    state.memory.slice(0, 12).map((item) => `- ${item}`).join("\n"),
    "GOALS",
    state.goals.slice(0, 12).map((item) => `- ${item}`).join("\n"),
    "ACTIVE PROJECTS",
    active.map((project) => `- ${project.title} [${project.kind}] ${project.notes}`).join("\n") || "- none yet"
  ].join("\n");
}

export function craftLocalNudge(state: AgentState) {
  const active = state.projects.find((project) => project.status === "active");
  if (active) {
    return `Nudge: ${active.title} is still active. Pick one move: outline it, test it, publish it, or kill it.`;
  }

  const goal = state.goals[0] || "choose a useful target";
  return `Nudge: ${goal} What is the next concrete move?`;
}
