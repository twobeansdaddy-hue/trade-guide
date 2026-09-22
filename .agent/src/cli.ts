import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type { AgentRegistry, PolicyConfig, StepOutcome } from "./types.js";
import { createDecisionEngine } from "./decision/create-decision-engine.js";
import { PolicyEngine } from "./policy/policy-engine.js";
import { ContextBuilder } from "./context/context-builder.js";
import { ArtifactManager } from "./artifacts/artifact-manager.js";
import { ProjectStateStore } from "./state/project-state-store.js";
import { Router } from "./orchestrator/router.js";
import { Orchestrator } from "./orchestrator/orchestrator.js";

const agentRoot = dirname(dirname(fileURLToPath(import.meta.url))); // .agent/
const repoRoot = join(agentRoot, "..");

async function loadJson<T>(relativePath: string): Promise<T> {
  const raw = await readFile(join(agentRoot, relativePath), "utf-8");
  return JSON.parse(raw) as T;
}

async function buildOrchestrator(): Promise<Orchestrator> {
  const registry = await loadJson<AgentRegistry>("config/agents.json");
  const policyConfig = await loadJson<PolicyConfig>("config/policies.json");

  const decisionEngine = createDecisionEngine();
  const policyEngine = new PolicyEngine(policyConfig);
  const artifactManager = new ArtifactManager(join(agentRoot, "artifacts"));
  const contextBuilder = new ContextBuilder(repoRoot, artifactManager);
  const stateStore = new ProjectStateStore(
    join(agentRoot, "state", "project.json"),
    join(agentRoot, "state", "decisions.jsonl"),
  );
  // V0.1 ships no real AgentProvider; every role resolves to
  // NotImplementedProvider until one is wired in. See providers/README.
  const router = new Router(registry, new Map());

  return new Orchestrator({
    decisionEngine,
    policyEngine,
    contextBuilder,
    artifactManager,
    stateStore,
    router,
  });
}

function printOutcome(outcome: StepOutcome): void {
  switch (outcome.kind) {
    case "agent_executed":
      console.log(`  -> ${outcome.role}: ${outcome.result.summary}`);
      break;
    case "agent_failed":
      console.log(`  -> ${outcome.role} FAILED: ${outcome.error}`);
      break;
    case "human_review":
      console.log(`  -> HUMAN_REVIEW: ${outcome.reason}`);
      break;
    case "complete":
      console.log(`  -> COMPLETE: ${outcome.reason}`);
      break;
    case "verify_more":
      console.log(`  -> VERIFY_MORE: ${outcome.reason}`);
      break;
    case "limit_reached":
      console.log(`  -> LIMIT_REACHED: ${outcome.reason}`);
      break;
  }
}

async function runCommand(goal: string, dryRun: boolean): Promise<void> {
  const orchestrator = await buildOrchestrator();

  console.log(`Goal\n └─ ${goal}\n`);

  if (dryRun) {
    const outcomes = await orchestrator.dryRun(goal);
    console.log("Planned flow (dry-run, no agent executed)");
    outcomes.forEach(printOutcome);
    return;
  }

  const { outcomes } = await orchestrator.run(goal);
  console.log("Execution");
  outcomes.forEach(printOutcome);
}

async function stateCommand(action: string): Promise<void> {
  const statePath = join(agentRoot, "state", "project.json");
  if (action === "show") {
    try {
      console.log(await readFile(statePath, "utf-8"));
    } catch {
      console.log("No project state file yet. Run `run \"<goal>\"` first.");
    }
    return;
  }
  if (action === "reset") {
    const { unlink } = await import("node:fs/promises");
    try {
      await unlink(statePath);
      console.log("Project state reset.");
    } catch {
      console.log("Nothing to reset.");
    }
    return;
  }
  console.error(`Unknown state action '${action}'. Use 'show' or 'reset'.`);
  process.exitCode = 1;
}

async function main(): Promise<void> {
  const [command, ...rest] = process.argv.slice(2);

  if (command === "run") {
    const dryRun = rest.includes("--dry-run");
    const goal = rest.filter((arg) => arg !== "--dry-run").join(" ").trim();
    if (!goal) {
      console.error('Usage: cli run "<goal>" [--dry-run]');
      process.exitCode = 1;
      return;
    }
    await runCommand(goal, dryRun);
    return;
  }

  if (command === "state") {
    await stateCommand(rest[0] ?? "show");
    return;
  }

  console.error('Usage:\n  cli run "<goal>" [--dry-run]\n  cli state show\n  cli state reset');
  process.exitCode = 1;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack ?? error.message : error);
  process.exitCode = 1;
});
