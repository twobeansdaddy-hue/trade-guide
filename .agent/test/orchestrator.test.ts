import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { Orchestrator } from "../src/orchestrator/orchestrator.js";
import { Router } from "../src/orchestrator/router.js";
import { PolicyEngine } from "../src/policy/policy-engine.js";
import { ContextBuilder } from "../src/context/context-builder.js";
import { ArtifactManager } from "../src/artifacts/artifact-manager.js";
import { ProjectStateStore } from "../src/state/project-state-store.js";
import { MockDecisionEngine } from "../src/decision/mock-engine.js";
import type {
  AgentProvider,
  AgentRegistry,
  AgentResult,
  AgentTaskInput,
  BuiltContext,
  DecideNextActionInput,
  DecideNextActionResult,
  DecisionEngine,
  EvaluateRiskInput,
  EvaluateRiskResult,
  PolicyConfig,
} from "../src/types.js";

const permissivePolicy: PolicyConfig = {
  hardRules: [{ id: "force-push", keywords: ["force push"], reason: "Force push to remote" }],
  riskThresholds: { requireApprovalAtOrAbove: "high" },
};

class SucceedingProvider implements AgentProvider {
  readonly name = "test-success";
  async execute(_task: AgentTaskInput, _context: BuiltContext): Promise<AgentResult> {
    return { status: "completed", changedFiles: ["a.ts"], summary: "ok", followups: [] };
  }
}

class AlwaysFailingProvider implements AgentProvider {
  readonly name = "test-fail";
  async execute(): Promise<AgentResult> {
    throw new Error("boom");
  }
}

class AlwaysHumanEngine implements DecisionEngine {
  readonly name = "always-human";
  async decideNextAction(_input: DecideNextActionInput): Promise<DecideNextActionResult> {
    return { decision: "human", confidence: 1, reason: "ambiguous requirement" };
  }
  async checkCompletion(): Promise<{ status: "human_review"; confidence: number; reason: string }> {
    return { status: "human_review", confidence: 1, reason: "n/a" };
  }
  async evaluateRisk(_input: EvaluateRiskInput): Promise<EvaluateRiskResult> {
    return { risk: "low", reason: "n/a" };
  }
}

async function setup(registry: AgentRegistry, providers: Map<string, AgentProvider>) {
  const agentRoot = await mkdtemp(join(tmpdir(), "orch-agent-"));
  const repoRoot = await mkdtemp(join(tmpdir(), "orch-repo-"));
  const artifactManager = new ArtifactManager(join(agentRoot, "artifacts"));
  const stateStore = new ProjectStateStore(
    join(agentRoot, "state", "project.json"),
    join(agentRoot, "state", "decisions.jsonl"),
  );
  const contextBuilder = new ContextBuilder(repoRoot, artifactManager);
  const router = new Router(registry, providers);
  return { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router };
}

describe("Orchestrator", () => {
  let cleanupDirs: string[] = [];

  afterEach(async () => {
    await Promise.all(cleanupDirs.map((dir) => rm(dir, { recursive: true, force: true })));
    cleanupDirs = [];
  });

  it("runs the mock script end to end: research -> backend -> frontend -> complete", async () => {
    const registry: AgentRegistry = {
      research: { provider: "success", capabilities: [] },
      architecture: { provider: "success", capabilities: [] },
      backend: { provider: "success", capabilities: [] },
      frontend: { provider: "success", capabilities: [] },
      ui: { provider: "success", capabilities: [] },
      test: { provider: "success", capabilities: [] },
      review: { provider: "success", capabilities: [] },
    };
    const { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router } = await setup(
      registry,
      new Map([["success", new SucceedingProvider()]]),
    );
    cleanupDirs.push(agentRoot, repoRoot);

    const orchestrator = new Orchestrator({
      decisionEngine: new MockDecisionEngine(["research", "backend", "frontend"]),
      policyEngine: new PolicyEngine(permissivePolicy),
      contextBuilder,
      artifactManager,
      stateStore,
      router,
    });

    const { outcomes, finalState } = await orchestrator.run("회원가입 구현");

    const executedRoles = outcomes
      .filter((o) => o.kind === "agent_executed")
      .map((o) => (o as { role: string }).role);
    expect(executedRoles).toEqual(["research", "backend", "frontend"]);
    expect(outcomes.at(-1)?.kind).toBe("complete");
    expect(finalState?.completed).toHaveLength(3);
    expect(finalState?.active).toHaveLength(0);
  });

  it("moves pending -> running -> completed for a single successful task", async () => {
    const registry: AgentRegistry = {
      research: { provider: "success", capabilities: [] },
      architecture: { provider: "success", capabilities: [] },
      backend: { provider: "success", capabilities: [] },
      frontend: { provider: "success", capabilities: [] },
      ui: { provider: "success", capabilities: [] },
      test: { provider: "success", capabilities: [] },
      review: { provider: "success", capabilities: [] },
    };
    const { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router } = await setup(
      registry,
      new Map([["success", new SucceedingProvider()]]),
    );
    cleanupDirs.push(agentRoot, repoRoot);

    const orchestrator = new Orchestrator({
      decisionEngine: new MockDecisionEngine(["research"]),
      policyEngine: new PolicyEngine(permissivePolicy),
      contextBuilder,
      artifactManager,
      stateStore,
      router,
    });

    const { state } = await orchestrator.step("goal");
    expect(state.active).toHaveLength(0);
    expect(state.completed).toHaveLength(1);
    expect(state.completed[0]?.status).toBe("completed");
  });

  it("escalates to human_review after maxRetriesPerTask failed attempts on the same task", async () => {
    const registry: AgentRegistry = {
      research: { provider: "fail", capabilities: [] },
      architecture: { provider: "fail", capabilities: [] },
      backend: { provider: "fail", capabilities: [] },
      frontend: { provider: "fail", capabilities: [] },
      ui: { provider: "fail", capabilities: [] },
      test: { provider: "fail", capabilities: [] },
      review: { provider: "fail", capabilities: [] },
    };
    const { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router } = await setup(
      registry,
      new Map([["fail", new AlwaysFailingProvider()]]),
    );
    cleanupDirs.push(agentRoot, repoRoot);

    const orchestrator = new Orchestrator({
      decisionEngine: new MockDecisionEngine(["research", "research", "research", "research"]),
      policyEngine: new PolicyEngine(permissivePolicy),
      contextBuilder,
      artifactManager,
      stateStore,
      router,
      limits: { maxRetriesPerTask: 2, maxSteps: 10, maxAgentCalls: 10, maxDecisionCalls: 10 },
      buildTaskInput: (role, goal) => ({ id: `${role}-fixed`, goal, role }),
    });

    const { outcomes, finalState } = await orchestrator.run("goal");

    expect(outcomes.some((o) => o.kind === "agent_failed")).toBe(true);
    expect(outcomes.at(-1)?.kind).toBe("human_review");
    expect(finalState?.blocked[0]?.status).toBe("human_review");
    expect(finalState?.blocked[0]?.retryCount).toBe(2);
  });

  it("lets a Hard Policy rule force human_review even when the decision engine wants to act", async () => {
    const registry: AgentRegistry = {
      research: { provider: "success", capabilities: [] },
      architecture: { provider: "success", capabilities: [] },
      backend: { provider: "success", capabilities: [] },
      frontend: { provider: "success", capabilities: [] },
      ui: { provider: "success", capabilities: [] },
      test: { provider: "success", capabilities: [] },
      review: { provider: "success", capabilities: [] },
    };
    const { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router } = await setup(
      registry,
      new Map([["success", new SucceedingProvider()]]),
    );
    cleanupDirs.push(agentRoot, repoRoot);

    const strictPolicy: PolicyConfig = {
      hardRules: [{ id: "force-push", keywords: ["force push"], reason: "Force push to remote" }],
      riskThresholds: { requireApprovalAtOrAbove: "high" },
    };

    const orchestrator = new Orchestrator({
      decisionEngine: new MockDecisionEngine(["backend"]),
      policyEngine: new PolicyEngine(strictPolicy),
      contextBuilder,
      artifactManager,
      stateStore,
      router,
    });

    const { outcome } = await orchestrator.step("force push main branch");
    expect(outcome.kind).toBe("human_review");
  });

  it("stops immediately when the DecisionEngine itself asks for human review", async () => {
    const { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router } = await setup(
      { research: { provider: "success", capabilities: [] } } as unknown as AgentRegistry,
      new Map([["success", new SucceedingProvider()]]),
    );
    cleanupDirs.push(agentRoot, repoRoot);

    const orchestrator = new Orchestrator({
      decisionEngine: new AlwaysHumanEngine(),
      policyEngine: new PolicyEngine(permissivePolicy),
      contextBuilder,
      artifactManager,
      stateStore,
      router,
    });

    const { outcomes } = await orchestrator.run("ambiguous goal");
    expect(outcomes).toHaveLength(1);
    expect(outcomes[0]?.kind).toBe("human_review");
  });

  it("dry-run never calls an AgentProvider and never persists state", async () => {
    const registry: AgentRegistry = {
      research: { provider: "fail", capabilities: [] },
      architecture: { provider: "fail", capabilities: [] },
      backend: { provider: "fail", capabilities: [] },
      frontend: { provider: "fail", capabilities: [] },
      ui: { provider: "fail", capabilities: [] },
      test: { provider: "fail", capabilities: [] },
      review: { provider: "fail", capabilities: [] },
    };
    const { agentRoot, repoRoot, artifactManager, stateStore, contextBuilder, router } = await setup(
      registry,
      new Map([["fail", new AlwaysFailingProvider()]]),
    );
    cleanupDirs.push(agentRoot, repoRoot);

    const orchestrator = new Orchestrator({
      decisionEngine: new MockDecisionEngine(["research", "backend"]),
      policyEngine: new PolicyEngine(permissivePolicy),
      contextBuilder,
      artifactManager,
      stateStore,
      router,
    });

    const outcomes = await orchestrator.dryRun("goal");
    expect(outcomes.filter((o) => o.kind === "agent_executed")).toHaveLength(2);
    expect(outcomes.at(-1)?.kind).toBe("complete");

    const persisted = await stateStore.load("goal");
    expect(persisted.completed).toHaveLength(0);
  });
});
