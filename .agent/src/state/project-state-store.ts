import { mkdir, readFile, writeFile, appendFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { DecisionLogEntry, ProjectState, TaskRecord } from "../types.js";

function nowIso(): string {
  return new Date().toISOString();
}

export function createInitialState(goal: string): ProjectState {
  return {
    currentGoal: goal,
    completed: [],
    active: [],
    blocked: [],
    artifacts: {},
    decisions: [],
    stateVersion: 0,
    updatedAt: nowIso(),
  };
}

/**
 * Persists Project State (small, structured JSON) and an append-only Decision
 * Log (JSONL). Deliberately does not store agent conversation transcripts —
 * see docs/design/jev-orchestrator/ARCHITECTURE.md ("state + artifact, not
 * conversation").
 */
export class ProjectStateStore {
  constructor(
    private readonly statePath: string,
    private readonly decisionLogPath: string,
  ) {}

  async load(goal: string): Promise<ProjectState> {
    try {
      const raw = await readFile(this.statePath, "utf-8");
      const parsed = JSON.parse(raw) as ProjectState;
      if (goal && parsed.currentGoal !== goal) {
        return { ...parsed, currentGoal: goal };
      }
      return parsed;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") {
        return createInitialState(goal);
      }
      throw error;
    }
  }

  async save(state: ProjectState): Promise<ProjectState> {
    const next: ProjectState = {
      ...state,
      stateVersion: state.stateVersion + 1,
      updatedAt: nowIso(),
    };
    await mkdir(dirname(this.statePath), { recursive: true });
    await writeFile(this.statePath, JSON.stringify(next, null, 2) + "\n", "utf-8");
    return next;
  }

  async appendDecision(entry: DecisionLogEntry): Promise<void> {
    await mkdir(dirname(this.decisionLogPath), { recursive: true });
    await appendFile(this.decisionLogPath, JSON.stringify(entry) + "\n", "utf-8");
  }

  static moveTaskToActive(state: ProjectState, task: TaskRecord): ProjectState {
    return {
      ...state,
      active: [...state.active.filter((t) => t.id !== task.id), task],
    };
  }

  static completeTask(state: ProjectState, task: TaskRecord): ProjectState {
    return {
      ...state,
      active: state.active.filter((t) => t.id !== task.id),
      completed: [...state.completed.filter((t) => t.id !== task.id), task],
    };
  }

  static blockTask(state: ProjectState, task: TaskRecord): ProjectState {
    return {
      ...state,
      active: state.active.filter((t) => t.id !== task.id),
      blocked: [...state.blocked.filter((t) => t.id !== task.id), task],
    };
  }

  static recordArtifact(state: ProjectState, taskId: string, artifactPath: string): ProjectState {
    return {
      ...state,
      artifacts: { ...state.artifacts, [taskId]: artifactPath },
    };
  }
}
