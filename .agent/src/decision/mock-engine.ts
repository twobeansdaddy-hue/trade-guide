import type {
  CheckCompletionResult,
  DecideNextActionInput,
  DecideNextActionResult,
  DecisionEngine,
  EvaluateRiskInput,
  EvaluateRiskResult,
  Role,
} from "../types.js";

const DEFAULT_SCRIPT: Role[] = ["research", "backend", "frontend"];

/**
 * Deterministic DecisionEngine for testing the orchestration loop without a
 * real Jev API. It advances through a fixed script of roles based on how
 * many tasks are already completed for the current goal, then reports
 * completion. No network calls, no randomness.
 */
export class MockDecisionEngine implements DecisionEngine {
  readonly name = "mock";
  private readonly script: Role[];

  constructor(script: Role[] = DEFAULT_SCRIPT) {
    this.script = script;
  }

  async decideNextAction(input: DecideNextActionInput): Promise<DecideNextActionResult> {
    const completedCount = input.state.completed.length;
    if (completedCount >= this.script.length) {
      return { decision: "complete", confidence: 1, reason: "mock script exhausted" };
    }
    const nextRole = this.script[completedCount];
    if (!nextRole) {
      return { decision: "complete", confidence: 1, reason: "mock script exhausted" };
    }
    return {
      decision: nextRole,
      confidence: 0.9,
      reason: `mock script step ${completedCount + 1}/${this.script.length}`,
    };
  }

  async checkCompletion(input: DecideNextActionInput): Promise<CheckCompletionResult> {
    if (input.state.blocked.length > 0) {
      return { status: "human_review", confidence: 1, reason: "blocked tasks pending" };
    }
    if (input.state.completed.length >= this.script.length) {
      return { status: "complete", confidence: 1, reason: "mock script exhausted" };
    }
    return { status: "incomplete", confidence: 0.9, reason: "mock script has remaining steps" };
  }

  async evaluateRisk(_input: EvaluateRiskInput): Promise<EvaluateRiskResult> {
    return { risk: "low", reason: "mock engine always reports low risk" };
  }
}
