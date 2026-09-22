import type {
  AgentTaskInput,
  DecisionEngine,
  OrchestratorLimits,
  ProjectState,
  Role,
  StepOutcome,
  TaskRecord,
} from "../types.js";
import { PolicyEngine } from "../policy/policy-engine.js";
import { ContextBuilder } from "../context/context-builder.js";
import { ArtifactManager } from "../artifacts/artifact-manager.js";
import { ProjectStateStore } from "../state/project-state-store.js";
import { Router } from "./router.js";

export const DEFAULT_LIMITS: OrchestratorLimits = {
  maxSteps: 20,
  maxRetriesPerTask: 3,
  maxAgentCalls: 20,
  maxDecisionCalls: 40,
};

export type TaskInputBuilder = (role: Role, goal: string, state: ProjectState) => AgentTaskInput;

const defaultTaskInputBuilder: TaskInputBuilder = (role, goal, state) => ({
  id: `${role}-${state.completed.length + state.active.length + 1}`,
  goal,
  role,
});

export interface OrchestratorOptions {
  decisionEngine: DecisionEngine;
  policyEngine: PolicyEngine;
  contextBuilder: ContextBuilder;
  artifactManager: ArtifactManager;
  stateStore: ProjectStateStore;
  router: Router;
  limits?: Partial<OrchestratorLimits>;
  buildTaskInput?: TaskInputBuilder;
}

export interface RunResult {
  outcomes: StepOutcome[];
  finalState: ProjectState | null;
}

function nowIso(): string {
  return new Date().toISOString();
}

/**
 * Drives one Project State through: load -> ask Jev -> apply policy ->
 * select agent -> build minimal context -> execute agent -> collect result
 * -> save artifacts -> update state -> ask Jev completion -> repeat/complete
 * /human review. It never hands execution responsibility to the
 * DecisionEngine (see docs/design/jev-orchestrator/ARCHITECTURE.md
 * #orchestrator).
 */
export class Orchestrator {
  private readonly decisionEngine: DecisionEngine;
  private readonly policyEngine: PolicyEngine;
  private readonly contextBuilder: ContextBuilder;
  private readonly artifactManager: ArtifactManager;
  private readonly stateStore: ProjectStateStore;
  private readonly router: Router;
  private readonly limits: OrchestratorLimits;
  private readonly buildTaskInput: TaskInputBuilder;

  constructor(options: OrchestratorOptions) {
    this.decisionEngine = options.decisionEngine;
    this.policyEngine = options.policyEngine;
    this.contextBuilder = options.contextBuilder;
    this.artifactManager = options.artifactManager;
    this.stateStore = options.stateStore;
    this.router = options.router;
    this.limits = { ...DEFAULT_LIMITS, ...options.limits };
    this.buildTaskInput = options.buildTaskInput ?? defaultTaskInputBuilder;
  }

  /** Routing-only simulation: never persists state and never calls an AgentProvider. */
  async dryRun(goal: string): Promise<StepOutcome[]> {
    let state = await this.stateStore.load(goal);
    const outcomes: StepOutcome[] = [];
    let decisionCalls = 0;

    for (let step = 0; step < this.limits.maxSteps; step += 1) {
      if (decisionCalls >= this.limits.maxDecisionCalls) {
        outcomes.push({ kind: "limit_reached", reason: "maxDecisionCalls exceeded" });
        break;
      }
      decisionCalls += 1;
      const decision = await this.decisionEngine.decideNextAction({ goal, state });
      const risk = await this.decisionEngine.evaluateRisk({ goal, action: decision.decision });
      const policy = this.policyEngine.evaluate({ goal, risk: risk.risk });

      if (policy.requiresHumanApproval) {
        outcomes.push({
          kind: "human_review",
          reason: policy.reason ?? "policy requires human approval",
        });
        break;
      }
      if (decision.decision === "human") {
        outcomes.push({ kind: "human_review", reason: decision.reason });
        break;
      }
      if (decision.decision === "complete") {
        outcomes.push({ kind: "complete", reason: decision.reason });
        break;
      }

      const role = decision.decision;
      outcomes.push({
        kind: "agent_executed",
        role,
        result: {
          status: "completed",
          changedFiles: [],
          summary: `dry-run: would route to '${role}' (no agent executed)`,
          followups: [],
        },
      });

      // Advance the in-memory simulation only, so a scripted engine like
      // MockDecisionEngine can progress through its plan for the printout.
      const simulatedTask: TaskRecord = {
        id: `dry-run-${role}-${step}`,
        goal,
        role,
        status: "completed",
        retryCount: 0,
        createdAt: nowIso(),
        updatedAt: nowIso(),
      };
      state = ProjectStateStore.completeTask(state, simulatedTask);
    }

    return outcomes;
  }

  /** Executes one real decision + (if applicable) one agent call, persisting state. */
  async step(goal: string): Promise<{ outcome: StepOutcome; state: ProjectState }> {
    let state = await this.stateStore.load(goal);

    const decision = await this.decisionEngine.decideNextAction({ goal, state });
    const risk = await this.decisionEngine.evaluateRisk({ goal, action: decision.decision });
    const policy = this.policyEngine.evaluate({ goal, risk: risk.risk });

    await this.stateStore.appendDecision({
      timestamp: nowIso(),
      goal,
      decision: decision.decision,
      confidence: decision.confidence,
      reason: decision.reason,
      stateVersion: state.stateVersion,
      risk: risk.risk,
    });

    if (policy.requiresHumanApproval) {
      state = await this.stateStore.save(state);
      return {
        outcome: { kind: "human_review", reason: policy.reason ?? "policy requires human approval" },
        state,
      };
    }

    if (decision.decision === "human") {
      state = await this.stateStore.save(state);
      return { outcome: { kind: "human_review", reason: decision.reason }, state };
    }

    if (decision.decision === "complete") {
      const completion = await this.decisionEngine.checkCompletion({ goal, state });
      state = await this.stateStore.save(state);
      if (completion.status === "complete") {
        return { outcome: { kind: "complete", reason: completion.reason }, state };
      }
      return { outcome: { kind: "verify_more", reason: completion.reason }, state };
    }

    const role: Role = decision.decision;
    const taskInput = this.buildTaskInput(role, goal, state);
    const existing = [...state.active, ...state.blocked].find((t) => t.id === taskInput.id);
    const retryCount = existing?.retryCount ?? 0;

    const runningTask: TaskRecord = {
      id: taskInput.id,
      goal,
      role,
      status: "running",
      retryCount,
      createdAt: existing?.createdAt ?? nowIso(),
      updatedAt: nowIso(),
    };
    state = ProjectStateStore.moveTaskToActive(state, runningTask);

    try {
      const provider = this.router.resolve(role);
      const context = await this.contextBuilder.build(taskInput);
      const result = await provider.execute(taskInput, context);

      let artifactPath: string | undefined;
      if (result.artifactPath) {
        artifactPath = result.artifactPath;
        state = ProjectStateStore.recordArtifact(state, taskInput.id, artifactPath);
      }

      const completedTask: TaskRecord = {
        ...runningTask,
        status: "completed",
        changedFiles: result.changedFiles,
        tests: result.tests,
        summary: result.summary,
        followups: result.followups,
        artifactPath,
        updatedAt: nowIso(),
      };
      state = ProjectStateStore.completeTask(state, completedTask);
      state = await this.stateStore.save(state);
      return { outcome: { kind: "agent_executed", role, result }, state };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const failedTask: TaskRecord = {
        ...runningTask,
        retryCount: retryCount + 1,
        updatedAt: nowIso(),
      };

      if (failedTask.retryCount >= this.limits.maxRetriesPerTask) {
        failedTask.status = "human_review";
        state = ProjectStateStore.blockTask(state, failedTask);
        state = await this.stateStore.save(state);
        return {
          outcome: {
            kind: "human_review",
            reason: `task '${taskInput.id}' exceeded maxRetriesPerTask (${this.limits.maxRetriesPerTask}): ${message}`,
          },
          state,
        };
      }

      failedTask.status = "blocked";
      state = ProjectStateStore.blockTask(state, failedTask);
      state = await this.stateStore.save(state);
      return { outcome: { kind: "agent_failed", role, error: message }, state };
    }
  }

  /** Runs `step` repeatedly until complete/human_review/limit_reached. */
  async run(goal: string): Promise<RunResult> {
    const outcomes: StepOutcome[] = [];
    let finalState: ProjectState | null = null;
    let agentCalls = 0;
    let decisionCalls = 0;

    for (let i = 0; i < this.limits.maxSteps; i += 1) {
      if (decisionCalls >= this.limits.maxDecisionCalls) {
        outcomes.push({ kind: "limit_reached", reason: "maxDecisionCalls exceeded" });
        break;
      }
      decisionCalls += 1;

      const { outcome, state } = await this.step(goal);
      outcomes.push(outcome);
      finalState = state;

      if (outcome.kind === "agent_executed" || outcome.kind === "agent_failed") {
        agentCalls += 1;
      }
      if (agentCalls >= this.limits.maxAgentCalls) {
        outcomes.push({ kind: "limit_reached", reason: "maxAgentCalls exceeded" });
        break;
      }
      if (outcome.kind === "complete" || outcome.kind === "human_review") {
        break;
      }
    }

    if (outcomes.length === this.limits.maxSteps) {
      outcomes.push({ kind: "limit_reached", reason: "maxSteps exceeded" });
    }

    return { outcomes, finalState };
  }
}
